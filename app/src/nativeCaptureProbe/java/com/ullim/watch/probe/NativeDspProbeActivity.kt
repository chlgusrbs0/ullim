package com.ullim.watch.probe

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.EnvelopeExtractor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Opt-in synthetic arithmetic check; no microphone, vibrator, files, or network. */
class NativeDspProbeActivity : ComponentActivity() {
    private var worker: Thread? = null
    private var started = false
    private lateinit var label: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply {
            text = "소리 분석 자동 시험 준비"
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(label)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { worker?.interrupt(); return }
        if (started || intent.action != "com.ullim.watch.PROBE_DSP") return
        started = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        worker = Thread({
            try {
                verifyEnvelope()
                Log.i(TAG, "result=PASS")
                runOnUiThread { label.text = "소리 분석 시험 통과" }
            } catch (_: InterruptedException) { Log.i(TAG, "result=CANCELLED")
            } catch (failure: Exception) {
                Log.i(TAG, "result=FAIL code=${failure.message}")
                runOnUiThread { label.text = "소리 분석 시험 실패" }
            } finally { runOnUiThread { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        }, "ullim-dsp-probe").apply { start() }
    }
    override fun onPause() { worker?.interrupt(); super.onPause() }

    private fun frame(level: Int, index: Int = 0, session: Long = 1) =
        AudioFrame(session, index * 20_000_000L, ShortArray(320) { level.toShort() })

    private fun verifyEnvelope() {
        val quiet = EnvelopeExtractor().extract(frame(0))
        check(quiet.rms == 0f && quiet.peak == 0f && quiet.isSilent) { "SILENCE" }
        var previous = -1f
        for (level in intArrayOf(100, 1_000, 10_000, 32_767)) {
            val features = EnvelopeExtractor().extract(frame(level))
            check(features.rms > previous && features.envelope.isFinite()) { "ORDER" }
            previous = features.rms
        }
        val clipped = EnvelopeExtractor().extract(frame(-32_768))
        check(clipped.rms == 1f && clipped.peak == 1f) { "CLIPPING" }
        val sine = ShortArray(320) { (16_384 * sin(2 * PI * 1_000 * it / 16_000)).toInt().toShort() }
        val tone = EnvelopeExtractor().extract(AudioFrame(1, 0, sine))
        check(abs(tone.rms - .5f / sqrt(2f)) < .0001f && abs(tone.peak - .5f) < .0001f) { "SINE_RMS" }
        val extractor = EnvelopeExtractor()
        val attack = extractor.extract(frame(16_384)).envelope
        check(abs(attack - .5f * (1f - exp(-1f))) < .00001f) { "ATTACK" }
        var release = attack
        for (index in 1..6) release = extractor.extract(frame(0, index)).envelope
        check(abs(release - attack * exp(-1f)) < .00001f) { "RELEASE" }
        extractor.reset()
        check(extractor.extract(frame(0)).envelope == 0f) { "RESET" }
        extractor.extract(frame(32_767, 1))
        check(extractor.extract(frame(0, session = 2)).envelope == 0f) { "SESSION_RESET" }
        var maxNs = 0L
        val began = System.nanoTime()
        repeat(30_000) { index ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val input = AudioFrame(3, index * 20_000_000L, sine)
            val start = System.nanoTime()
            val result = extractor.extract(input)
            maxNs = maxOf(maxNs, System.nanoTime() - start)
            check(result.rms.isFinite() && result.envelope.isFinite()) { "FINITE" }
        }
        Log.i(TAG, "envelope frames=30000 simulatedMinutes=10 totalMs=${(System.nanoTime() - began) / 1_000_000} maxExtractUs=${maxNs / 1_000} sineRms=${tone.rms} attack=$attack release=$release order=true reset=true")
    }
    companion object { private const val TAG = "UllimDspProbe" }
}
