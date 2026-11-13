package com.ullim.watch.probe

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ullim.watch.ai.AndroidYamnet
import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.Classification
import com.ullim.watch.core.SoundClassifier
import com.ullim.watch.dsp.DspProcessor
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Opt-in synthetic model test. No AudioRecord, vibrator, audio files, or network. */
class NativeYamnetProbeActivity : ComponentActivity() {
    private val cancelled = AtomicBoolean()
    private var resumed = false
    private var started = false
    private var worker: Thread? = null
    private var producer: Thread? = null
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply {
            text = "AI 모델 자동 시험 준비"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(label)
    }
    override fun onResume() { super.onResume(); resumed = true; maybeStart() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) maybeStart() else cancel()
    }
    override fun onPause() { resumed = false; cancel(); super.onPause() }
    private fun cancel() { cancelled.set(true); worker?.interrupt(); producer?.interrupt() }
    private fun active() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun maybeStart() {
        if (started || !resumed || !hasWindowFocus() || intent.action != "com.ullim.watch.PROBE_YAMNET") return
        started = true
        cancelled.set(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        label.text = "AI 모델 자동 시험 중"
        worker = Thread({
            try {
                verify()
                Log.i(TAG, "result=PASS")
                runOnUiThread { if (!isDestroyed) label.text = "AI 모델 추론 확인" }
            } catch (_: InterruptedException) { Log.i(TAG, "result=CANCELLED")
            } catch (failure: Throwable) {
                Log.i(TAG, "result=FAIL type=${failure.javaClass.simpleName} code=${failure.message}")
                runOnUiThread { if (!isDestroyed) label.text = "AI 모델 시험 실패" }
            } finally { runOnUiThread { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        }, "ullim-yamnet-probe").apply { start() }
    }

    private fun memory(stage: String) {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val java = Runtime.getRuntime().run { totalMemory() - freeMemory() }
        val temperature = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }
        Log.i(TAG, "memory stage=$stage pssKb=${info.totalPss} usedJavaBytes=$java batteryTemperatureTenths=${temperature ?: "UNKNOWN"}")
    }

    private fun verify() {
        check(checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_DENIED) { "NETWORK_PERMISSION" }
        memory("before")
        val dsp = DspProcessor()
        val processed = AtomicInteger()
        val done = AtomicBoolean()
        val childFailure = AtomicReference<Throwable?>()
        val timings = LongArray(500)
        val deliveries = LongArray(500)
        val lateness = LongArray(500)
        val fanout = AudioFanOut { frame ->
            val began = System.nanoTime()
            val output = dsp.extract(frame)
            val index = processed.getAndIncrement()
            timings[index] = System.nanoTime() - began
            check(output.level.isFinite()) { "DSP_NON_FINITE" }
        }
        var classifier: SoundClassifier? = null
        var inferred = 0
        var maxInferenceNs = 0L
        var overlappingInferences = 0
        val began = System.nanoTime()
        val cpuBefore = Process.getElapsedCpuTime()
        fanout.start(1)
        producer = Thread({
            try {
                repeat(500) { index ->
                    active()
                    val frameStart = began + index * 20_000_000L
                    // A sampled 20ms packet is deliverable only after its last sample exists.
                    val due = frameStart + 20_000_000L
                    val wait = due - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    val samples = ShortArray(320) { sample ->
                        val t = (index * 320L + sample) / 16_000.0
                        if (index < 150) 0 else (.2 * sin(2 * PI * 1_000 * t) * 32_767).toInt().toShort()
                    }
                    val delivered = System.nanoTime()
                    deliveries[index] = delivered
                    lateness[index] = (delivered - due).coerceAtLeast(0L)
                    check(fanout.accept(AudioFrame(1, frameStart, samples))) { "DSP_FRAME_REJECTED" }
                }
            } catch (failure: Throwable) { childFailure.set(failure)
            } finally { done.set(true) }
        }, "ullim-yamnet-dsp").apply { start() }
        try {
            val loadStarted = System.nanoTime()
            val dspBeforeLoad = processed.get()
            classifier = AndroidYamnet.load(this)
            Log.i(TAG, "loaded ms=${(System.nanoTime() - loadStarted) / 1_000_000} inputSamples=15600 outputClasses=521 type=FLOAT32 dspBefore=$dspBeforeLoad dspAfter=${processed.get()}")
            memory("loaded")
            val dspAtInferenceStart = processed.get()
            for (kind in 0..2) {
                active()
                val random = Random(7331)
                val samples = ShortArray(15_600) { index ->
                    val value = when (kind) {
                        0 -> 0.0
                        1 -> .3 * sin(2 * PI * 1_000 * index / 16_000)
                        else -> random.nextDouble(-.2, .2)
                    }
                    (value * 32_767).toInt().toShort()
                }
                val input = AudioFrame(1, System.nanoTime() - 975_000_000L, samples)
                val start = System.nanoTime()
                val dspBeforeInference = processed.get()
                val result = classifier.classify(input)
                val elapsed = System.nanoTime() - start
                if (processed.get() > dspBeforeInference) overlappingInferences++
                validate(result, input)
                inferred++
                maxInferenceNs = maxOf(maxInferenceNs, elapsed)
                val fixture = arrayOf("digital_silence", "sine_1000Hz", "seeded_white_noise")[kind]
                val scores = result.scores.take(3).joinToString("|") { "${it.label}:${String.format(Locale.US, "%.6f", it.confidence)}" }
                Log.i(TAG, "fixture=$fixture inferMs=${elapsed / 1_000_000} scores=$scores dspFrames=${processed.get()}")
            }
            while (true) {
                active()
                check(System.nanoTime() - began < 30_000_000_000L) { "PRODUCER_TIMEOUT" }
                val frame = fanout.takeLatestAi()
                if (frame == null) {
                    if (done.get()) break
                    Thread.sleep(10)
                    continue
                }
                val start = System.nanoTime()
                val dspBeforeInference = processed.get()
                validate(classifier.classify(frame), frame)
                if (processed.get() > dspBeforeInference) overlappingInferences++
                maxInferenceNs = maxOf(maxInferenceNs, System.nanoTime() - start)
                inferred++
            }
            producer!!.join(2_000)
            check(!producer!!.isAlive && childFailure.get() == null && processed.get() == 500) { "DSP_PRODUCER_FAILED" }
            check(inferred >= 4) { "NO_LATEST_WINDOW_INFERENCE" }
            val dspAtInferenceEnd = processed.get()
            check(dspAtInferenceEnd > dspAtInferenceStart) { "NO_CONCURRENT_DSP_PROGRESS" }
            memory("stream_complete")
            val closeStarted = System.nanoTime()
            classifier.close()
            classifier.close()
            Log.i(TAG, "closed closeUs=${(System.nanoTime() - closeStarted) / 1_000}")
            classifier = null
            System.gc()
            Thread.sleep(100)
            memory("closed")
            timings.sort()
            val gaps = LongArray(499) { deliveries[it + 1] - deliveries[it] }.apply { sort() }
            lateness.sort()
            Log.i(TAG, "cadence gapP95Us=${gaps[474] / 1_000} gapMaxUs=${gaps.last() / 1_000} lateP95Us=${lateness[474] / 1_000} lateMaxUs=${lateness.last() / 1_000} dspDuringInferenceStart=$dspAtInferenceStart dspDuringInferenceEnd=$dspAtInferenceEnd overlappingOperations=$overlappingInferences")
            Log.i(TAG, "summary dspFrames=${processed.get()} validInferences=$inferred maxInferenceMs=${maxInferenceNs / 1_000_000} dspP95Us=${timings[474] / 1_000} dspMaxUs=${timings.last() / 1_000} elapsedMs=${(System.nanoTime() - began) / 1_000_000} cpuMs=${Process.getElapsedCpuTime() - cpuBefore} closed=true")
        } finally {
            cancelled.set(true)
            producer?.interrupt()
            // An interrupted observation must still close the model on this AI thread.
            var restoreInterrupt = Thread.interrupted()
            try {
                try { producer?.join(2_000) } catch (_: InterruptedException) { restoreInterrupt = true }
            } finally {
                try { fanout.stop(); classifier?.close() }
                finally { if (restoreInterrupt) Thread.currentThread().interrupt() }
            }
        }
    }

    private fun validate(result: Classification, frame: AudioFrame) {
        check(result.sessionId == frame.sessionId && result.timestampNs == frame.timestampNs + frame.durationNs) { "MODEL_TIME" }
        check(result.scores.size == 5 && result.scores.all { it.label.isNotBlank() && it.confidence.isFinite() && it.confidence in 0f..1f }) { "MODEL_RESULT" }
    }
    companion object { private const val TAG = "UllimYamnetProbe" }
}
