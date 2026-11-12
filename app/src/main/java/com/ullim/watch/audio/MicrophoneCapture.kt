package com.ullim.watch.audio

import com.ullim.watch.core.AudioFrame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface PcmInput {
    fun start()
    fun read(buffer: ShortArray): Int
    fun stop()
    fun release()
}

enum class CaptureError { PERMISSION, INITIALIZATION, READ, CONSUMER, CLEANUP }

sealed interface CaptureStatus {
    data object Starting : CaptureStatus
    data object Recording : CaptureStatus
    data object Stopped : CaptureStatus
    data class Failed(val reason: CaptureError) : CaptureStatus
}

class MicrophoneCapture(
    private val sourceFactory: () -> PcmInput,
    private val onFrame: (AudioFrame) -> Unit,
    private val onStatus: (CaptureStatus) -> Unit,
    private val clockNs: () -> Long = System::nanoTime,
) {
    private class Run(val sessionId: Long) {
        @Volatile var stopped = false
        var input: PcmInput? = null
        val done = CountDownLatch(1)
    }

    private val lock = Any()
    @Volatile private var active: Run? = null
    @Volatile private var latest: Run? = null

    fun start(sessionId: Long): Boolean = synchronized(lock) {
        require(sessionId > 0)
        if (active != null) return false
        val run = Run(sessionId)
        active = run
        latest = run
        Thread({ capture(run) }, "ullim-audio").start()
        true
    }

    fun stop() {
        val run = active ?: return
        run.stopped = true
        synchronized(run) { runCatching { run.input?.stop() } }
    }

    fun awaitStopped(timeoutMs: Long): Boolean = latest?.done?.await(timeoutMs, TimeUnit.MILLISECONDS) ?: true

    private fun emit(status: CaptureStatus) { runCatching { onStatus(status) } }

    private fun capture(run: Run) {
        var failure: CaptureError? = null
        var recording = false
        emit(CaptureStatus.Starting)
        try {
            val input = sourceFactory()
            synchronized(run) {
                run.input = input
                if (!run.stopped) {
                    input.start()
                    recording = true
                }
            }
            if (recording && !run.stopped) {
                val firstSampleNs = clockNs()
                var sampleOffset = 0L
                var emptyReads = 0
                val buffer = ShortArray(AudioFrame.DSP_SAMPLES)
                emit(CaptureStatus.Recording)
                while (!run.stopped) {
                    val count = input.read(buffer)
                    if (run.stopped) break
                    if (count < 0 || count > buffer.size) { failure = CaptureError.READ; break }
                    if (count == 0) {
                        if (++emptyReads >= 250) { failure = CaptureError.READ; break }
                        Thread.sleep(2)
                        continue
                    }
                    emptyReads = 0
                    val frame = AudioFrame(run.sessionId, firstSampleNs + sampleOffset * 62_500L, buffer.copyOf(count))
                    sampleOffset += count
                    try { onFrame(frame) } catch (_: RuntimeException) { failure = CaptureError.CONSUMER; break }
                }
            }
        } catch (_: SecurityException) {
            failure = CaptureError.PERMISSION
        } catch (_: Exception) {
            if (!run.stopped) failure = if (recording) CaptureError.READ else CaptureError.INITIALIZATION
        } finally {
            synchronized(run) {
                runCatching { run.input?.stop() }
                try { run.input?.release() } catch (_: RuntimeException) { failure = CaptureError.CLEANUP }
                run.input = null
            }
            synchronized(lock) { if (active === run) active = null }
            emit(failure?.let(CaptureStatus::Failed) ?: CaptureStatus.Stopped)
            run.done.countDown()
        }
    }
}
