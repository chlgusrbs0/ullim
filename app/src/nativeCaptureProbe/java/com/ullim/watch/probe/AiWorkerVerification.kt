package com.ullim.watch.probe

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import com.ullim.watch.ai.AiCaptureFeed
import com.ullim.watch.ai.AiWorker
import com.ullim.watch.ai.AndroidYamnet
import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.Classification
import com.ullim.watch.core.SoundClassifier
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.haptics.EnvelopeHapticMapper
import com.ullim.watch.haptics.HybridHapticMapper
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

/** Real paced synthetic producer + original worker. No AudioRecord, motor, files or network. */
internal object AiWorkerVerification {
    fun run(context: Context, active: () -> Unit, report: (String) -> Unit) {
        val power = context.getSystemService(PowerManager::class.java)
        val enabled = AtomicBoolean()
        val loads = AtomicInteger()
        val closes = AtomicInteger()
        val nativeActive = AtomicInteger()
        val maxNative = AtomicInteger()
        val inferenceTimes = ConcurrentLinkedQueue<Long>()
        val lane = Executors.newSingleThreadExecutor { task -> Thread(task, "ullim-native-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
        fun <T> native(block: () -> T): T {
            val count = nativeActive.incrementAndGet()
            maxNative.updateAndGet { maxOf(it, count) }
            try { return block() } finally { nativeActive.decrementAndGet() }
        }
        val worker = AiWorker({
            val model = native { AndroidYamnet.load(context) }
            loads.incrementAndGet()
            object : SoundClassifier {
                override fun classify(frame: AudioFrame): Classification {
                    val began = System.nanoTime()
                    try { return native { model.classify(frame) } }
                    finally { inferenceTimes.add(System.nanoTime() - began) }
                }
                override fun close() {
                    native { model.close() }
                    closes.incrementAndGet()
                }
            }
        }, lane)
        val dsp = DspProcessor()
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        var dspFrames = 0
        var changedFrames = 0
        var confirmedFrames = 0
        var commandDifference = 0.0
        val fanout = AudioFanOut { frame ->
            val features = dsp.extract(frame)
            val base = baseline.map(features)
            val classification = worker.current(1, features.timestampNs)
            val output = hybrid.map(features, classification)
            check(base.isSilent == output.isSilent && base.timestampNs == output.timestampNs && base.durationMs == output.durationMs && base.sessionId == output.sessionId) { "DSP_INVARIANT" }
            check(output.commandedIntensity in 0f..0.85f) { "OUTPUT_BOUND" }
            if (classification?.scores?.isNotEmpty() == true) confirmedFrames++
            if (output != base) changedFrames++
            commandDifference += abs(base.commandedIntensity - output.commandedIntensity)
            if (!enabled.get()) check(output == base) { "OFF_NOT_BASELINE" }
            dspFrames++
        }
        val feed = AiCaptureFeed(1, worker, fanout, enabled::get)
        var globalIndex = 0
        var origin = 0L
        val random = Random(7331)

        fun snapshot(stage: String): Int? {
            val memory = Debug.MemoryInfo()
            Debug.getMemoryInfo(memory)
            val heap = Runtime.getRuntime().run { totalMemory() - freeMemory() }
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE }
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val thermal = power.currentThermalStatus
            val state = worker.state.value
            report("worker stage=$stage elapsedMs=${if (origin == 0L) 0 else (System.nanoTime() - origin) / 1_000_000} dspFrames=$dspFrames phase=${state.phase} fault=${state.fault ?: "NONE"} valid=${state.successfulInferences} completed=${state.inferenceCount} dropped=${state.droppedWindows} loadMs=${state.loadDurationNs / 1_000_000} maxInferenceMs=${state.maxInferenceNs / 1_000_000} nativeActive=${nativeActive.get()} maxNative=${maxNative.get()} loads=${loads.get()} closes=${closes.get()} modelLoaded=${state.modelLoaded} busy=${state.busy} cleanupPending=${state.cleanupPending} pssKb=${memory.totalPss} javaBytes=$heap processCpuMs=${Process.getElapsedCpuTime()} batteryTenths=${temperature ?: "UNKNOWN"} plugged=${plugged ?: "UNKNOWN"} thermal=$thermal confirmedFrames=$confirmedFrames changedFrames=$changedFrames")
            check(thermal < PowerManager.THERMAL_STATUS_SEVERE && (temperature == null || temperature < 450)) { "THERMAL_TEST_STOP" }
            return temperature
        }
        fun percentile(values: LongArray, count: Int): Long {
            if (count == 0) return 0
            val sorted = values.copyOf(count).apply { sort() }
            return sorted[(ceil(count * .95).toInt() - 1).coerceAtLeast(0)]
        }
        fun phase(name: String, frameCount: Int) {
            val started = System.nanoTime()
            val cpu = Process.getElapsedCpuTime()
            val gaps = LongArray(frameCount - 1)
            val lateness = LongArray(frameCount)
            val processing = LongArray(frameCount)
            val beforeDsp = dspFrames
            val beforeChanged = changedFrames
            val beforeDifference = commandDifference
            val beforeValid = worker.state.value.successfulInferences
            val progress = if (enabled.get()) NativeInferenceProgress { worker.state.value.successfulInferences } else null
            var previousDelivery = 0L
            var faultSince = 0L
            var lastHeartbeat = 0L
            report("worker phaseStart=$name frames=$frameCount enabled=${enabled.get()}")
            repeat(frameCount) { index ->
                active()
                val frameStart = started + index * 20_000_000L
                val due = frameStart + 20_000_000L
                val wait = due - System.nanoTime()
                if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                val part = globalIndex / 1000 % 3
                val knock = globalIndex % 100 in listOf(5, 20, 45)
                val samples = ShortArray(320) { sample ->
                    val t = (globalIndex * 320L + sample) / 16_000.0
                    val value = when (part) {
                        0 -> random.nextDouble(-.2, .2)
                        1 -> (.1 + .3 * (1 + sin(2 * PI * 4 * t)) / 2) * sin(2 * PI * 220 * t)
                        else -> if (knock) .7 else 0.0
                    }
                    (value * 32_767).toInt().toShort()
                }
                val delivered = System.nanoTime()
                if (index > 0) gaps[index - 1] = delivered - previousDelivery
                previousDelivery = delivered
                lateness[index] = (delivered - due).coerceAtLeast(0)
                val processingStart = System.nanoTime()
                check(feed.accept(AudioFrame(1, frameStart, samples))) { "FRAME_REJECTED" }
                processing[index] = System.nanoTime() - processingStart
                globalIndex++
                val now = System.nanoTime()
                if (now - lastHeartbeat >= 100_000_000L) { worker.tick(); lastHeartbeat = now }
                if (index % 50 == 0) check(power.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE) { "THERMAL_TEST_STOP" }
                if ((index + 1) % 3000 == 0) {
                    snapshot("${name}_minute${(index + 1) / 3000}")
                    progress?.checkpoint()
                }
                if (enabled.get() && worker.state.value.fault != null) {
                    if (faultSince == 0L) {
                        faultSince = now
                        report("worker fallbackObserved=$name fault=${worker.state.value.fault} dspFrames=$dspFrames")
                    } else if (now - faultSince >= 10_000_000_000L) {
                        snapshot("${name}_fault_after_10s_dsp")
                        error("AI_FAULT_WITH_DSP_FALLBACK")
                    }
                }
            }
            val elapsed = System.nanoTime() - started
            check(dspFrames - beforeDsp == frameCount) { "DSP_COUNT" }
            report("worker phaseComplete=$name actualElapsedMs=${elapsed / 1_000_000} processCpuDeltaMs=${Process.getElapsedCpuTime() - cpu} dspFrames=${dspFrames - beforeDsp} validDelta=${worker.state.value.successfulInferences - beforeValid} changedDelta=${changedFrames - beforeChanged} commandMae=${(commandDifference - beforeDifference) / frameCount} gapP95Us=${percentile(gaps, gaps.size) / 1000} gapMaxUs=${gaps.max() / 1000} lateP95Us=${percentile(lateness, frameCount) / 1000} lateMaxUs=${lateness.max() / 1000} processingP95Us=${percentile(processing, frameCount) / 1000} processingMaxUs=${processing.max() / 1000}")
            check(elapsed >= frameCount * 20_000_000L) { "NOT_REAL_TIME" }
        }
        try {
            fanout.start(1)
            check(feed.start())
            snapshot("before_off")
            origin = System.nanoTime()
            phase("OFF_BASELINE", 15_000)
            check(loads.get() == 0 && worker.state.value.inferenceCount == 0L) { "OFF_LOADED_MODEL" }
            enabled.set(true)
            feed.refreshMode()
            phase("ON", 15_000)
            check(worker.state.value.fault == null && worker.state.value.successfulInferences >= 100) { "ON_NOT_SUSTAINED" }
            enabled.set(false)
            feed.refreshMode()
            val beforeOff = worker.state.value.inferenceCount
            check(worker.current(1, System.nanoTime()) == null) { "OFF_CONTEXT_RETAINED" }
            phase("OFF_CLEANUP", 1500)
            worker.tick()
            val state = worker.state.value
            check(!state.busy && !state.modelLoaded && !state.cleanupPending && loads.get() == closes.get()) { "MODEL_NOT_CLOSED" }
            check(state.inferenceCount <= beforeOff + 1) { "INFERRED_AFTER_OFF" }
            check(maxNative.get() == 1 && nativeActive.get() == 0) { "PARALLEL_NATIVE" }
            snapshot("after_off_cleanup")
            val infer = inferenceTimes.toLongArray()
            report("worker final dspFrames=$dspFrames realElapsedMs=${(System.nanoTime() - origin) / 1_000_000} actualNativeInferences=${infer.size} inferenceP95Us=${percentile(infer, infer.size) / 1000} inferenceMaxUs=${infer.maxOrNull()?.div(1000) ?: 0} loads=${loads.get()} closes=${closes.get()} maxNative=${maxNative.get()} changedFrames=$changedFrames rawAudioSaved=false motor=false")
        } finally {
            enabled.set(false)
            worker.setEnabled(1, false)
            worker.stop(1)
            fanout.stop()
            var interrupted = Thread.interrupted()
            val deadline = System.nanoTime() + 5_000_000_000L
            try {
                while (System.nanoTime() < deadline) {
                    worker.tick()
                    val state = worker.state.value
                    if (!state.busy && !state.modelLoaded && !state.cleanupPending) break
                    try { Thread.sleep(20) } catch (_: InterruptedException) { interrupted = true }
                }
            } finally {
                lane.shutdown()
                try { lane.awaitTermination(2, TimeUnit.SECONDS) } catch (_: InterruptedException) { interrupted = true }
                val state = worker.state.value
                report("worker cleanup busy=${state.busy} modelLoaded=${state.modelLoaded} pending=${state.cleanupPending} nativeActive=${nativeActive.get()} loads=${loads.get()} closes=${closes.get()}")
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
    }
}
