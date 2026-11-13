package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.Classification
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.ai.ClassificationSmoother
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class HybridHapticMapperTest {
    private fun context(t: Long, label: String = "Speech", score: Float = .9f, session: Long = 1) =
        Classification(session, t, t + 1_500_000_000L, listOf(ClassScore(label, score)))
    private fun features(t: Long, session: Long = 1, level: Float = .3f) =
        AudioFeatures(session, t, level, level, level, onset = .5f)

    @Test fun measuredModelNoiseScoresReachTheNoisePolicyAfterRealSmoothing() {
        // Exact pinned-model scores observed on generated white noise, not a fake Noise winner.
        val scores = listOf(ClassScore("Static", .66796875f), ClassScore("Noise", .5859375f),
            ClassScore("White noise", .5f))
        val smoother = ClassificationSmoother()
        smoother.accept(Classification(1, 1_000_000_000, 2_500_000_000, scores), 1, 1_000_000_000)
        val context = smoother.accept(Classification(1, 1_500_000_000, 3_000_000_000, scores), 1, 1_500_000_000)!!
        assertEquals("Static", context.scores.first().label)
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        repeat(30) { index ->
            val input = features(1_500_000_000L + index * 20_000_000L)
            val base = baseline.map(input)
            val adjusted = hybrid.map(input, context)
            assertTrue("confirmed real-model noise must adjust the command", adjusted.commandedIntensity < base.commandedIntensity)
            assertTrue(adjusted.commandedIntensity >= base.commandedIntensity * .94f - .000001f)
        }
    }

    @Test fun noiseGroupingNeverBypassesOtherPolicyUnknownOrLowScoreFallback() {
        val cases = listOf(
            listOf(ClassScore("Static", .7f), ClassScore("Speech", .65f)),
            listOf(ClassScore("Music", .8f), ClassScore("Static", .7f)),
            listOf(ClassScore("Static", .7f), ClassScore("Music", .65f)),
            listOf(ClassScore("Static", .64f), ClassScore("Noise", .3f)),
        )
        for (scores in cases) {
            val baseline = EnvelopeHapticMapper()
            val hybrid = HybridHapticMapper()
            val first = features(1_500_000_000)
            baseline.map(first)
            hybrid.map(first, context(first.timestampNs, "Noise"))
            val input = features(1_520_000_000)
            assertEquals(baseline.map(input), hybrid.map(input,
                Classification(1, input.timestampNs, 3_000_000_000, scores)))
        }
    }

    @Test fun offIsExactlyBaselineEvenAfterAiChangedPreviousOutput() {
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        var changed = false
        repeat(80) { index ->
            val input = features(index * 20_000_000L, level = if (index % 10 == 0) 0f else .3f)
            val base = baseline.map(input)
            val output = hybrid.map(input, if (index < 50) context(input.timestampNs) else null)
            if (index < 50 && output != base) changed = true
            if (index >= 50) assertEquals(base, output)
        }
        assertTrue(changed)
    }

    @Test fun invalidOrUnmappedContextRestoresBaselineWithoutResidualPolicy() {
        val t = 2_020_000_000L
        val invalid = listOf(null, context(0), context(t, score = .3f), context(t, session = 2),
            context(t, "French horn"), context(t + 1), Classification(1, t, t + 1_000_000_000, emptyList()),
            Classification(1, t, t + 1_000_000_000, listOf(ClassScore("Speech", .8f), ClassScore("Noise", .79f))))
        for (bad in invalid) {
            val baseline = EnvelopeHapticMapper()
            val hybrid = HybridHapticMapper()
            val first = features(2_000_000_000)
            baseline.map(first); hybrid.map(first, context(first.timestampNs, "Noise"))
            val next = features(t)
            assertEquals(baseline.map(next), hybrid.map(next, bad))
        }
    }

    @Test fun samePcmPreservesTimingSilenceAndBoundedChangesForSpeechKnockAndNoise() {
        for ((kind, label) in listOf("speech" to "Speech", "knock" to "Knock", "noise" to "Noise")) {
            val dsp = DspProcessor()
            val baseline = EnvelopeHapticMapper()
            val hybrid = HybridHapticMapper()
            val baseEdges = mutableListOf<Int>()
            val aiEdges = mutableListOf<Int>()
            var baseSilent = true
            var aiSilent = true
            var changed = 0
            var difference = 0.0
            val random = Random(1234)
            repeat(100) { index ->
                val pcm = ShortArray(320) { sample ->
                    val seconds = (index * 320L + sample) / 16000.0
                    val value = when (kind) {
                        "knock" -> if (index in listOf(5, 20, 45)) .7 else 0.0
                        "speech" -> (.1 + .3 * (1 + sin(2 * PI * 4 * seconds)) / 2) * sin(2 * PI * 220 * seconds)
                        else -> random.nextDouble(-.2, .2)
                    }
                    (value * 32767).toInt().toShort()
                }
                val input = dsp.extract(AudioFrame(1, index * 20_000_000L, pcm))
                val base = baseline.map(input)
                val output = hybrid.map(input, context(input.timestampNs, label))
                assertEquals(base.sessionId, output.sessionId)
                assertEquals(base.timestampNs, output.timestampNs)
                assertEquals(base.durationMs, output.durationMs)
                assertEquals(base.isSilent, output.isSilent)
                assertTrue(output.commandedIntensity in 0f..0.85f)
                assertTrue(output.commandedIntensity >= base.commandedIntensity * .9f - .000001f)
                assertTrue(output.commandedIntensity <= base.commandedIntensity * 1.1f + .000001f)
                if (baseSilent && !base.isSilent) baseEdges.add(index)
                if (aiSilent && !output.isSilent) aiEdges.add(index)
                baseSilent = base.isSilent; aiSilent = output.isSilent
                if (base != output) changed++
                difference += abs(base.commandedIntensity - output.commandedIntensity)
            }
            assertEquals(baseEdges, aiEdges)
            if (kind == "knock") assertEquals(listOf(5, 20, 45), aiEdges)
            assertTrue(changed > 0)
            println("Synthetic $kind: changed=$changed/100 command-MAE=${difference / 100}; timing/zeros preserved; not perceptual improvement evidence")
        }
    }

    @Test fun intensityOrderAndResetRemainIndependentOfOldContext() {
        val output = listOf(.05f, .2f, .4f, .8f).map { level ->
            HybridHapticMapper().map(features(0, level = level), context(0, "Knock")).commandedIntensity
        }
        assertTrue(output.zipWithNext().all { (a, b) -> a <= b })
        val hybrid = HybridHapticMapper()
        repeat(20) { index -> hybrid.map(features(index * 20_000_000L), context(index * 20_000_000L, "Noise")) }
        hybrid.reset()
        val next = features(1_000_000_000, session = 2)
        val fresh = context(next.timestampNs, "Speech", session = 2)
        assertEquals(HybridHapticMapper().map(next, fresh), hybrid.map(next, fresh))
        val third = features(1_020_000_000, session = 3)
        val thirdContext = context(third.timestampNs, "Speech", session = 3)
        assertEquals(HybridHapticMapper().map(third, thirdContext), hybrid.map(third, thirdContext))
    }

    @Test fun sameSessionGapRestartsOnlyPolicyAndPreservesBaselineHistory() {
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        repeat(20) { index ->
            val input = features(index * 20_000_000L)
            baseline.map(input)
            hybrid.map(input, context(input.timestampNs, "Noise"))
        }
        val afterGap = features(440_000_000, level = .1f)
        val base = baseline.map(afterGap)
        val adjusted = hybrid.map(afterGap, context(afterGap.timestampNs, "Noise"))
        // First step toward the Noise gain is 0.994; the warmed baseline remains independent.
        assertEquals(base.commandedIntensity * .994f, adjusted.commandedIntensity, .000001f)
        assertTrue(base.commandedIntensity > EnvelopeHapticMapper().map(afterGap).commandedIntensity)
    }
}
