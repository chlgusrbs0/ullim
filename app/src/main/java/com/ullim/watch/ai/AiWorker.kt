package com.ullim.watch.ai

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.Classification
import com.ullim.watch.core.LatestClassification
import com.ullim.watch.core.SoundClassifier
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiPhase { DISABLED, LOADING, LISTENING, DELAYED, FAILED }
enum class AiFault { LOAD, INFERENCE, LOAD_TIMEOUT, INFERENCE_TIMEOUT, PREVIOUS_BUSY, CLEANUP, EXECUTOR }
data class AiSnapshot(
    val sessionId: Long = 0,
    val phase: AiPhase = AiPhase.DISABLED,
    val fault: AiFault? = null,
    val classification: Classification? = null,
    val modelLoaded: Boolean = false,
    val busy: Boolean = false,
    val cleanupPending: Boolean = false,
    val loadDurationNs: Long = 0,
    val lastInferenceNs: Long = 0,
    val maxInferenceNs: Long = 0,
    val inferenceCount: Long = 0,
    val successfulInferences: Long = 0,
    val droppedWindows: Long = 0,
    val operationElapsedNs: Long = 0,
    val faultCount: Long = 0,
    val timeoutCount: Long = 0,
    val resultExpired: Boolean = false,
    val automaticRetries: Int = 0,
    val rawClassification: Classification? = null,
)

/** The executor must be asynchronous. Production owns one worker/lane for the whole process. */
class AiWorker(
    private val factory: () -> SoundClassifier,
    private val executor: Executor,
    private val clockNs: () -> Long = System::nanoTime,
) {
    private enum class Kind { LOAD, INFER, CLOSE }
    private data class Job(val kind: Kind, val generation: Long, val session: Long, val startedNs: Long,
        val engine: SoundClassifier? = null, val frame: AudioFrame? = null)
    private val lock = Any()
    private val mutableState = MutableStateFlow(AiSnapshot())
    val state = mutableState.asStateFlow()
    private val latest = LatestClassification()
    private val smoother = ClassificationSmoother()
    private var session = 0L
    private var active = false
    private var requestedEnabled = false
    private var generation = 0L
    private var requestedAtNs = 0L
    private var fault: AiFault? = null
    private var phase = AiPhase.DISABLED
    private var pending: AudioFrame? = null
    private var lastOfferedNs = -1L
    private var model: SoundClassifier? = null
    private var modelGeneration = 0L
    private var failedClose: SoundClassifier? = null
    private var operation: Job? = null
    private var pumpRunning = false
    private var automaticRetries = 0
    private var retryAfterNs: Long? = null

    fun start(sessionId: Long, enabled: Boolean): Boolean = synchronized(lock) {
        if (sessionId <= session) return false
        session = sessionId
        active = true
        val modeChanged = requestedEnabled != enabled
        requestedEnabled = enabled
        generation++
        requestedAtNs = clockNs()
        automaticRetries = 0
        fault = if (failedClose != null) AiFault.CLEANUP else if (modeChanged) null else fault
        retryAfterNs = if (fault == AiFault.INFERENCE_TIMEOUT) requestedAtNs + 5_000_000_000L else null
        phase = if (fault != null) AiPhase.FAILED else if (enabled) AiPhase.LOADING else AiPhase.DISABLED
        clearContext()
        lastOfferedNs = -1
        mutableState.value = AiSnapshot(sessionId)
        publish()
        kick()
        true
    }

    fun setEnabled(sessionId: Long, enabled: Boolean) = synchronized(lock) {
        // Repeated UI observations must not reset the bounded timeout recovery.
        if (sessionId != session || requestedEnabled == enabled) return
        requestedEnabled = enabled
        generation++
        requestedAtNs = clockNs()
        automaticRetries = 0
        retryAfterNs = null
        fault = if (failedClose != null) AiFault.CLEANUP else null
        phase = if (fault != null) AiPhase.FAILED else if (active && enabled) AiPhase.LOADING else AiPhase.DISABLED
        clearContext()
        lastOfferedNs = -1
        publish()
        kick()
    }

    fun stop(sessionId: Long) = synchronized(lock) {
        if (!active || sessionId != session) return
        active = false
        retryAfterNs = null
        generation++
        if (failedClose != null) fault = AiFault.CLEANUP
        phase = if (fault != null) AiPhase.FAILED else AiPhase.DISABLED
        clearContext()
        publish()
        kick()
    }

    fun offer(frame: AudioFrame): Boolean = synchronized(lock) {
        if (!effective() || frame.sessionId != session || frame.sampleCount != AudioFrame.AI_SAMPLES ||
            frame.timestampNs <= lastOfferedNs ||
            (lastOfferedNs >= 0 && frame.timestampNs - lastOfferedNs < 500_000_000L) || !freshWindow(frame)
        ) return false
        lastOfferedNs = frame.timestampNs
        if (pending != null) dropWindow()
        pending = frame
        kick()
        true
    }

    /** Atomic result read; native work and the worker state lock are never on the DSP read path. */
    fun current(sessionId: Long, nowNs: Long): Classification? = latest.current(sessionId, nowNs)

    /** Called from a service heartbeat even while the model is blocked or no windows arrive. */
    fun tick() = synchronized(lock) {
        val now = clockNs()
        // Retry one transient inference timeout per capture session, only after
        // the old native call AND close have returned. Never create a second lane.
        if (active && requestedEnabled && fault == AiFault.INFERENCE_TIMEOUT && automaticRetries < 1 &&
            retryAfterNs?.let { now >= it } == true && operation == null && model == null && failedClose == null) {
            automaticRetries++
            retryAfterNs = null
            generation++
            requestedAtNs = now
            fault = null
            phase = AiPhase.LOADING
            clearContext()
            lastOfferedNs = -1
            kick()
        }
        val result = mutableState.value.classification
        if (mutableState.value.rawClassification?.isFresh(session, now) == false) {
            mutableState.value = mutableState.value.copy(rawClassification = null)
        }
        if (result != null && !result.isFresh(session, now)) {
            latest.clear()
            mutableState.value = mutableState.value.copy(classification = null, resultExpired = true)
        }
        if (effective()) {
            val job = operation
            when {
                job?.generation == generation && job.kind == Kind.INFER && now - job.startedNs > 1_000_000_000L ->
                    fail(AiFault.INFERENCE_TIMEOUT, generation)
                job?.generation == generation && job.kind == Kind.INFER && now - job.startedNs > 500_000_000L -> {
                    phase = AiPhase.DELAYED
                    latest.clear()
                    mutableState.value = mutableState.value.copy(classification = null)
                }
                job?.generation == generation && job.kind == Kind.LOAD && now - job.startedNs > 10_000_000_000L ->
                    fail(AiFault.LOAD_TIMEOUT, generation)
                (model == null || modelGeneration != generation) && now - requestedAtNs > 10_000_000_000L ->
                    fail(if (job != null) AiFault.PREVIOUS_BUSY else AiFault.LOAD_TIMEOUT, generation)
            }
        }
        publish()
    }

    private fun effective() = active && requestedEnabled && fault == null && failedClose == null
    private fun freshWindow(frame: AudioFrame): Boolean {
        if (frame.timestampNs > Long.MAX_VALUE - frame.durationNs) return false
        val age = clockNs() - (frame.timestampNs + frame.durationNs)
        return age >= -20_000_000L && age < 1_500_000_000L
    }
    private fun clearContext() {
        pending = null
        latest.clear()
        smoother.reset()
        mutableState.value = mutableState.value.copy(classification = null, rawClassification = null, resultExpired = false)
    }
    private fun dropWindow() { mutableState.value = mutableState.value.copy(droppedWindows = mutableState.value.droppedWindows + 1) }
    private fun publish() {
        val nativePresent = model != null || operation != null || failedClose != null
        val oldOperation = operation?.let { it.generation != generation || it.kind == Kind.CLOSE } == true
        mutableState.value = mutableState.value.copy(sessionId = session, phase = phase, fault = fault,
            automaticRetries = automaticRetries,
            modelLoaded = model != null || operation?.kind == Kind.CLOSE || failedClose != null,
            busy = operation != null,
            operationElapsedNs = operation?.let { (clockNs() - it.startedNs).coerceAtLeast(0) } ?: 0,
            cleanupPending = nativePresent && (!effective() || oldOperation || (model != null && modelGeneration != generation)))
    }
    private fun fail(reason: AiFault, expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        fault = reason
        phase = AiPhase.FAILED
        retryAfterNs = if (reason == AiFault.INFERENCE_TIMEOUT && automaticRetries < 1)
            clockNs() + 5_000_000_000L else null
        mutableState.value = mutableState.value.copy(faultCount = mutableState.value.faultCount + 1,
            timeoutCount = mutableState.value.timeoutCount + if (reason in listOf(AiFault.LOAD_TIMEOUT, AiFault.INFERENCE_TIMEOUT, AiFault.PREVIOUS_BUSY)) 1 else 0)
        generation++
        clearContext()
        publish()
    }
    private fun kick() {
        if (pumpRunning || failedClose != null) return
        if (!effective() && model == null) return
        pumpRunning = true
        try { executor.execute(::pump) } catch (_: RejectedExecutionException) {
            pumpRunning = false
            fail(AiFault.EXECUTOR, generation)
        }
    }

    private fun nextJob(): Job? = synchronized(lock) {
        val currentModel = model
        val job = when {
            currentModel != null && (!effective() || modelGeneration != generation) -> {
                model = null
                Job(Kind.CLOSE, modelGeneration, session, clockNs(), currentModel)
            }
            !effective() -> null
            currentModel == null -> Job(Kind.LOAD, generation, session, clockNs())
            pending != null -> {
                val frame = pending!!
                pending = null
                if (!freshWindow(frame)) { dropWindow(); null }
                else Job(Kind.INFER, generation, session, clockNs(), currentModel, frame)
            }
            else -> null
        }
        if (job == null) pumpRunning = false
        else if (job.kind == Kind.LOAD) phase = AiPhase.LOADING
        operation = job
        publish()
        job
    }

    private fun pump() {
        while (true) {
            val job = nextJob() ?: return
            when (job.kind) {
                Kind.LOAD -> load(job)
                Kind.INFER -> infer(job)
                Kind.CLOSE -> close(job)
            }
        }
    }

    private fun load(job: Job) {
        var loaded: SoundClassifier? = null
        var failed = false
        try { loaded = factory() } catch (_: Exception) { failed = true } catch (_: LinkageError) { failed = true }
        synchronized(lock) {
            operation = null
            val elapsed = (clockNs() - job.startedNs).coerceAtLeast(0)
            if (loaded != null) { model = loaded; modelGeneration = job.generation }
            if (job.session == session) mutableState.value = mutableState.value.copy(loadDurationNs = elapsed)
            if (job.generation == generation && effective()) {
                when {
                    failed -> fail(AiFault.LOAD, job.generation)
                    elapsed > 10_000_000_000L -> fail(AiFault.LOAD_TIMEOUT, job.generation)
                    else -> phase = AiPhase.LISTENING
                }
            }
            publish()
        }
    }

    private fun infer(job: Job) {
        var result: Classification? = null
        var failed = false
        try { result = job.engine!!.classify(job.frame!!) } catch (_: Exception) { failed = true } catch (_: LinkageError) { failed = true }
        synchronized(lock) {
            operation = null
            val now = clockNs()
            val elapsed = (now - job.startedNs).coerceAtLeast(0)
            val expectedEnd = job.frame!!.timestampNs + job.frame.durationNs
            if (job.session == session) mutableState.value = mutableState.value.copy(
                lastInferenceNs = elapsed, maxInferenceNs = maxOf(mutableState.value.maxInferenceNs, elapsed),
                inferenceCount = mutableState.value.inferenceCount + 1,
                successfulInferences = mutableState.value.successfulInferences +
                    if (!failed && result?.sessionId == job.session && result?.timestampNs == expectedEnd) 1 else 0)
            if (job.generation == generation && effective()) {
                val output = result
                when {
                    failed || output == null || output.sessionId != job.session || output.timestampNs != expectedEnd ->
                        fail(AiFault.INFERENCE, job.generation)
                    elapsed > 1_000_000_000L -> fail(AiFault.INFERENCE_TIMEOUT, job.generation)
                    else -> {
                        val processed = smoother.accept(output, job.session, now)
                        if (processed != null) latest.publish(processed)
                        mutableState.value = mutableState.value.copy(classification = processed,
                            rawClassification = output, resultExpired = false)
                        phase = AiPhase.LISTENING
                    }
                }
            }
            publish()
        }
    }

    private fun close(job: Job) {
        var failed = false
        try { job.engine!!.close() } catch (_: Exception) { failed = true } catch (_: LinkageError) { failed = true }
        synchronized(lock) {
            operation = null
            if (failed) {
                failedClose = job.engine
                fail(AiFault.CLEANUP, generation)
            }
            publish()
        }
    }
}
