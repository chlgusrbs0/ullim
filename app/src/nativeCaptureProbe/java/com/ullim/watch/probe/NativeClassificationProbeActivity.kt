package com.ullim.watch.probe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ullim.watch.ai.AndroidYamnet
import com.ullim.watch.ai.ClassificationSmoother
import com.ullim.watch.ai.SoundLabels
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.Classification
import com.ullim.watch.core.ClassScore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Opt-in generated-audio/controlled-score verification; never captures microphone audio. */
class NativeClassificationProbeActivity : ComponentActivity() {
    private val cancelled = AtomicBoolean()
    private var resumed = false
    private var started = false
    private var worker: Thread? = null
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply {
            text = "AI 결과 자동 시험 준비"
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
    private fun cancel() { cancelled.set(true); worker?.interrupt() }
    private fun active() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun maybeStart() {
        if (started || !resumed || !hasWindowFocus() || intent.action !in setOf("com.ullim.watch.PROBE_CLASSIFICATION", "com.ullim.watch.PROBE_HYBRID", "com.ullim.watch.PROBE_AI_WORKER")) return
        started = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        label.text = "AI 결과 자동 시험 중"
        worker = Thread({
            try {
                if (intent.action == "com.ullim.watch.PROBE_AI_WORKER") AiWorkerVerification.run(this, ::active) { Log.i(TAG, it) }
                else if (intent.action == "com.ullim.watch.PROBE_HYBRID") HybridPolicyVerification.run(this, ::active) { Log.i(TAG, it) }
                else verify()
                Log.i(TAG, "result=PASS")
                runOnUiThread { if (!isDestroyed) label.text = "AI 결과 시험 완료" }
            } catch (_: InterruptedException) { Log.i(TAG, "result=CANCELLED")
            } catch (failure: Throwable) {
                Log.i(TAG, "result=FAIL type=${failure.javaClass.simpleName} code=${failure.message}")
            } finally { runOnUiThread { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        }, "ullim-classification-probe").apply { start() }
    }

    private fun verify() {
        check(checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_DENIED)
        controlledScores()
        val smoother = ClassificationSmoother()
        val classifier = AndroidYamnet.load(this)
        try {
            var last: Classification? = null
            for (kind in 0..5) {
                smoother.reset()
                repeat(3) { observation ->
                    active()
                    val now = System.nanoTime()
                    val input = AudioFrame(1, now - 975_000_000L, generatedSamples(kind))
                    val raw = classifier.classify(input)
                    val readAt = System.nanoTime()
                    val stable = smoother.accept(raw, 1, readAt)!!
                    check(stable.timestampNs == raw.timestampNs && stable.validUntilNs == raw.validUntilNs)
                    check(stable.scores.size <= 5 && stable.isFresh(1, readAt))
                    val shown = stable.scores.firstOrNull()
                    val scores = raw.scores.take(3).joinToString("|") { "${it.label}:${score(it.confidence)}" }
                    Log.i(TAG, "source=generated_pcm fixture=${FIXTURES[kind]} observation=$observation raw=$scores stable=${shown?.label ?: "UNKNOWN"} korean=${shown?.let { SoundLabels.display(it.label) } ?: "불확실"} confidence=${shown?.let { score(it.confidence) } ?: "NONE"} timestampNs=${stable.timestampNs} validUntilNs=${stable.validUntilNs} ageMs=${(readAt - stable.timestampNs) / 1_000_000}")
                    last = stable
                    // Distinct window ends at least 500ms apart, using the real monotonic clock.
                    val wait = now + 500_000_000L - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                }
            }
            val expires = last!!.validUntilNs
            val wait = expires - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / 1_000_000 + 2)
            active()
            check(smoother.current(1, System.nanoTime()) == null) { "DELAYED_LABEL_RETAINED" }
            Log.i(TAG, "source=generated_pcm state=expired_without_new_callback oldExpiryNs=$expires nowNs=${System.nanoTime()}")
        } finally {
            smoother.reset()
            classifier.close()
            Log.i(TAG, "modelClosed=true contextCleared=true")
        }
    }

    private fun controlledScores() {
        fun result(t: Long, scores: List<ClassScore>, session: Long = 1) = Classification(session, t, t + 1_500_000_000L, scores)
        for (name in listOf("Speech", "Vehicle horn, car horn, honking", "Knock", "Noise")) {
            val smoother = ClassificationSmoother()
            val first = result(1_000_000_000L, listOf(ClassScore(name, .9f)))
            check(smoother.accept(first, 1, first.timestampNs)!!.scores.isEmpty())
            repeat(5) { check(smoother.accept(first, 1, 1_400_000_000L)!!.scores.isEmpty()) }
            val second = result(1_500_000_000L, first.scores)
            val stable = smoother.accept(second, 1, second.timestampNs)!!
            check(stable.scores.first().label == name && stable.scores.first().confidence == .9f)
            check(smoother.current(1, second.validUntilNs) == null)
            check(smoother.accept(second, 1, second.validUntilNs) == null)
            check(smoother.current(2, second.timestampNs) == null)
            smoother.reset()
            check(smoother.current(1, second.timestampNs) == null)
            Log.i(TAG, "source=controlled_scores label=$name korean=${SoundLabels.display(name)} confirmation=true duplicateRejected=true expiry=true otherSession=true reset=true")
        }
        val smoother = ClassificationSmoother()
        for ((index, name) in listOf("Speech", "Knock", "Speech", "Knock").withIndex()) {
            val input = result(1_000_000_000L + index * 500_000_000L, listOf(ClassScore(name, .9f)))
            check(smoother.accept(input, 1, input.timestampNs)!!.scores.isEmpty())
        }
        val ambiguous = result(3_000_000_000L, listOf(ClassScore("Speech", .4f), ClassScore("Knock", .39f)))
        check(smoother.accept(ambiguous, 1, ambiguous.timestampNs)!!.scores.isEmpty())
        val low = result(3_500_000_000L, listOf(ClassScore("Speech", .1f)))
        check(smoother.accept(low, 1, low.timestampNs)!!.scores.isEmpty())
        val empty = result(4_000_000_000L, emptyList())
        check(smoother.accept(empty, 1, empty.timestampNs)!!.scores.isEmpty())
        val future = result(6_000_000_000L, listOf(ClassScore("Speech", .9f)))
        check(smoother.accept(future, 1, 5_500_000_000L) == null)
        Log.i(TAG, "source=controlled_scores alternating=true ambiguous=true low=true empty=true futureRejected=true")
    }

    private fun generatedSamples(kind: Int): ShortArray {
        val random = Random(7331)
        return ShortArray(15_600) { index ->
            val t = index / 16_000.0
            val value = when (kind) {
                0 -> 0.0
                1 -> {
                    val phase = 2 * PI * (120 * t + 1.5 * sin(2 * PI * 2 * t))
                    .15 * (sin(phase) + .5 * sin(2 * phase) + .25 * sin(3 * phase)) * (.5 + .5 * sin(2 * PI * 3 * t))
                }
                2 -> .15 * (sin(2 * PI * 350 * t) + sin(2 * PI * 440 * t))
                3 -> random.nextDouble(-.5, .5) * exp(-80 * (t % .31))
                4 -> random.nextDouble(-.2, .2)
                else -> .3 * sin(2 * PI * 1_000 * t)
            }
            (value * 32_767).toInt().toShort()
        }
    }
    private fun score(value: Float) = String.format(Locale.US, "%.6f", value)
    companion object {
        private const val TAG = "UllimClassificationProbe"
        private val FIXTURES = arrayOf("silence", "voiced_harmonic", "dual_tone_horn_like", "decaying_noise_knock_like", "seeded_white_noise", "sine_1000Hz")
    }
}
