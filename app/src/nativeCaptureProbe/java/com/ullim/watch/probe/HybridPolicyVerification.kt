package com.ullim.watch.probe

import android.content.Context
import com.ullim.watch.ai.AndroidYamnet
import com.ullim.watch.ai.ClassificationSmoother
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.Classification
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.haptics.EnvelopeHapticMapper
import com.ullim.watch.haptics.HybridHapticMapper
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Actual model context followed by fast logical-time command A/B; no mic or motor. */
internal object HybridPolicyVerification {
    fun run(context: Context, active: () -> Unit, report: (String) -> Unit) {
        val classifier = AndroidYamnet.load(context)
        var noiseContext: Classification? = null
        try {
            for (kind in 0..2) {
                val smoother = ClassificationSmoother()
                var stable: Classification? = null
                repeat(2) { observation ->
                    active()
                    val random = Random(7331)
                    val samples = ShortArray(15_600) { index ->
                        val value = when (kind) {
                            0 -> random.nextDouble(-.2, .2)
                            1 -> 0.0
                            else -> .3 * sin(2 * PI * 1_000 * index / 16_000)
                        }
                        (value * 32_767).toInt().toShort()
                    }
                    val end = System.nanoTime()
                    val raw = classifier.classify(AudioFrame(1, end - 975_000_000L, samples))
                    stable = smoother.accept(raw, 1, System.nanoTime())!!
                    report("hybrid source=actual_model generatedFixture=${NAMES[kind]} observation=$observation raw=${raw.scores.joinToString("|") { "${it.label}:${format(it.confidence)}" }} stable=${stable!!.scores.firstOrNull()?.label ?: "UNKNOWN"}")
                    val wait = end + 500_000_000L - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                }
                val confirmed = stable!!
                check(confirmed.scores.isNotEmpty()) { "UNCONFIRMED_FIXTURE" }
                if (kind == 0) noiseContext = confirmed
                val dsp = DspProcessor()
                val baseline = EnvelopeHapticMapper()
                val hybrid = HybridHapticMapper()
                val random = Random(7331)
                var changed = 0
                var difference = 0.0
                repeat(100) { index ->
                    active()
                    val samples = ShortArray(320) { sample ->
                        val t = (index * 320L + sample) / 16_000.0
                        val value = when (kind) {
                            0 -> random.nextDouble(-.2, .2)
                            1 -> 0.0
                            else -> .3 * sin(2 * PI * 1_000 * t)
                        }
                        (value * 32_767).toInt().toShort()
                    }
                    // Fast offline stream in the same monotonic domain; not measured delivery cadence.
                    val features = dsp.extract(AudioFrame(1, confirmed.timestampNs + index * 20_000_000L, samples))
                    val base = baseline.map(features)
                    val adjusted = hybrid.map(features, if (index < 50) confirmed else null)
                    check(base.sessionId == adjusted.sessionId && base.timestampNs == adjusted.timestampNs && base.durationMs == adjusted.durationMs) { "TIME_CHANGED" }
                    check(base.isSilent == adjusted.isSilent) { "SILENCE_CHANGED" }
                    check(adjusted.commandedIntensity in 0f..0.85f && adjusted.commandedIntensity >= base.commandedIntensity * .9f - .000001f && adjusted.commandedIntensity <= base.commandedIntensity * 1.1f + .000001f) { "POLICY_LIMIT" }
                    if (index >= 50) check(adjusted == base) { "OFF_NOT_EXACT" }
                    if (adjusted != base) changed++
                    difference += abs(base.commandedIntensity - adjusted.commandedIntensity)
                }
                if (kind == 0) check(changed > 0) { "ACTUAL_AI_NEVER_PARTICIPATED" }
                else check(changed == 0) { "UNSUPPORTED_OR_SILENCE_CHANGED" }
                report("hybrid source=actual_model fixture=${NAMES[kind]} logicalFrames=100 aiFirstFrames=50 offLastFrames=50 changed=$changed commandMae=${difference / 100} timingAndSilencePreserved=true offExactlyBaseline=true motor=false perceptionTest=false")
            }

            // Deliberately wrong real noise context must not remove any of three acoustic events.
            val contextResult = noiseContext!!
            val dsp = DspProcessor()
            val baseline = EnvelopeHapticMapper()
            val hybrid = HybridHapticMapper()
            val edges = mutableListOf<Int>()
            var wasQuiet = true
            repeat(85) { index ->
                active()
                val samples = ShortArray(320) { if (index in listOf(5, 20, 45)) 28000 else 0 }
                val features = dsp.extract(AudioFrame(1, contextResult.timestampNs + index * 20_000_000L, samples))
                val base = baseline.map(features)
                val adjusted = hybrid.map(features, contextResult)
                check(base.isSilent == adjusted.isSilent && adjusted.timestampNs == base.timestampNs && adjusted.durationMs == base.durationMs) { "WRONG_CONTEXT_LOST_EVENT" }
                if (wasQuiet && !adjusted.isSilent) edges.add(index)
                wasQuiet = adjusted.isSilent
                if (features.timestampNs >= contextResult.validUntilNs) check(adjusted == base) { "EXPIRED_NOT_BASELINE" }
            }
            check(edges == listOf(5, 20, 45)) { "WRONG_CONTEXT_RHYTHM" }
            report("hybrid source=actual_model scenario=deliberately_wrong_noise_context knockFrames=${edges.joinToString(",")} expiredExactlyBaseline=true noLabelDrivenPulses=true")
        } finally {
            classifier.close()
            report("hybrid modelClosed=true")
        }
    }
    private fun format(value: Float) = String.format(Locale.US, "%.6f", value)
    private val NAMES = arrayOf("seeded_white_noise", "silence", "sine_1000Hz")
}
