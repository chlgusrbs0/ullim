package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.Classification
import com.ullim.watch.dsp.DspProcessor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class HybridHapticMapperTest {
    private fun context(t: Long, label: String = "Speech", score: Float = .9f, session: Long = 1) =
        Classification(session, t, t + 1_500_000_000L, listOf(ClassScore(label, score)))
    private fun features(t: Long, session: Long = 1, level: Float = .5f, onset: Float = 0f) =
        AudioFeatures(session, t, level, level, level, onset = onset)
    private fun ripple(index: Int) = if (index % 2 == 0) .46f else .54f

    @Test fun voiceHornAlarmAndNoiseUseExactlyTheSameLoudnessRule() {
        val outputs = listOf("Speech", "Conversation", "Vehicle horn, car horn, honking", "Alarm", "Noise", "Music").map { label ->
            val mapper = HybridHapticMapper()
            (0..99).map { index ->
                val input = features(index * 20_000_000L, level = ripple(index))
                mapper.map(input, context(input.timestampNs, label)).commandedIntensity
            }
        }
        outputs.drop(1).forEach { assertEquals(outputs.first(), it) }
    }

    @Test fun reliableContinuityReducesMicroFluctuationAndChangesRealPulseRequests() {
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        val baseLevels = mutableListOf<Float>()
        val aiLevels = mutableListOf<Float>()
        var pulseChanges = 0
        repeat(120) { index ->
            val input = features(index * 20_000_000L, level = ripple(index))
            val base = baseline.map(input)
            val output = hybrid.map(input, context(input.timestampNs))
            assertEquals(base.isSilent, output.isSilent)
            assertTrue(abs(output.commandedIntensity - base.commandedIntensity) <= .030001f)
            if (index > 30) {
                baseLevels.add(base.commandedIntensity); aiLevels.add(output.commandedIntensity)
                val plain = PulseFallback.encode(base.commandedIntensity, 20)
                val ai = PulseFallback.encode(output.commandedIntensity, 20)
                if (!plain.timings.contentEquals(ai.timings)) pulseChanges++
            }
        }
        assertTrue(aiLevels.max() - aiLevels.min() < (baseLevels.max() - baseLevels.min()) / 2)
        assertTrue("AI must survive actual integer pulse encoding", pulseChanges > 20)
        assertEquals(AiTactileStatus.STABILIZING, hybrid.lastStatus)
    }

    @Test fun invalidExpiredAmbiguousAndOffRestoreExactBaselineImmediately() {
        val t = 600_000_000L
        val expired = Classification(1, 0, t, listOf(ClassScore("Speech", .9f)))
        val invalid = listOf(null, expired, context(t, score = .3f), context(t, session = 2),
            context(t + 1), Classification(1, t, t + 1_000_000_000L, emptyList()),
            Classification(1, t, t + 1_000_000_000L, listOf(ClassScore("Speech", .8f), ClassScore("Noise", .79f))))
        for (bad in invalid) {
            val baseline = EnvelopeHapticMapper()
            val hybrid = HybridHapticMapper()
            var changed = false
            repeat(30) { index ->
                val input = features(index * 20_000_000L, level = ripple(index))
                val base = baseline.map(input)
                if (hybrid.map(input, context(input.timestampNs)) != base) changed = true
            }
            assertTrue(changed)
            repeat(3) { offset ->
                val input = features(t + offset * 20_000_000L)
                assertEquals(baseline.map(input), hybrid.map(input, if (offset == 0) bad else null))
                assertFalse(hybrid.lastAiApplied)
            }
        }
    }

    @Test fun firstResponseLargeChangesOnsetsAndSilencePassStraightThrough() {
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        repeat(30) { index ->
            val input = features(index * 20_000_000L, level = ripple(index))
            val base = baseline.map(input)
            val output = hybrid.map(input, context(input.timestampNs))
            if (index == 0) assertEquals(base, output)
        }
        for ((offset, level) in listOf(1f, .1f, 0f).withIndex()) {
            val input = features(600_000_000L + offset * 20_000_000L, level = level, onset = if (offset == 0) 1f else 0f)
            assertEquals(baseline.map(input), hybrid.map(input, context(input.timestampNs)))
            assertFalse(hybrid.lastAiApplied)
        }
    }

    @Test fun steadyLevelsHaveNoPermanentCategoryGainAndRetainIntensityOrder() {
        val outputs = listOf(.05f, .2f, .5f, .75f, .85f, 1f).map { level ->
            val mapper = HybridHapticMapper()
            var output = 0f
            repeat(120) { index ->
                val input = features(index * 20_000_000L, level = level)
                output = mapper.map(input, context(input.timestampNs, "Alarm")).commandedIntensity
                val base = mapper.lastBaseline
                if (base <= .75f) assertTrue(output <= .75f)
                if (base >= .85f) assertTrue(output >= .85f)
            }
            assertEquals(level, output, .00001f)
            output
        }
        assertTrue(outputs.zipWithNext().all { (a,b) -> a < b })
    }

    @Test fun aSourceChangeAndTimestampGapResetOnlyTheAiFilter() {
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        repeat(30) { index ->
            val input = features(index * 20_000_000L, level = ripple(index))
            baseline.map(input); hybrid.map(input, context(input.timestampNs))
        }
        val changed = features(600_000_000L)
        assertEquals(baseline.map(changed), hybrid.map(changed, context(changed.timestampNs, "Alarm")))
        val afterGap = features(660_000_000L)
        assertEquals(baseline.map(afterGap), hybrid.map(afterGap, context(afterGap.timestampNs, "Alarm")))
        hybrid.reset()
        val next = features(0, session = 2)
        assertEquals(HybridHapticMapper().map(next, context(0, session = 2)), hybrid.map(next, context(0, session = 2)))
    }

    @Test fun lateContextKeepsShortEventUnchangedAndAssistsSustainedFluctuation() {
        val baseline = EnvelopeHapticMapper()
        val hybrid = HybridHapticMapper()
        var changedLater = false
        repeat(80) { index ->
            val input = features(index * 20_000_000L, level = ripple(index))
            val base = baseline.map(input)
            val output = hybrid.map(input, if (index < 5) null else context(input.timestampNs))
            if (index < 25) assertEquals(base, output)
            if (index > 30 && output != base) changedLater = true
        }
        assertTrue(changedLater)
    }

    @Test fun actualPcmKeepsTimingAndRealQuietGapsForEveryCategory() {
        for (label in listOf("Speech", "Alarm", "Knock")) {
            val dsp = DspProcessor()
            val baseline = EnvelopeHapticMapper()
            val hybrid = HybridHapticMapper()
            val edges = mutableListOf<Int>()
            var silent = true
            repeat(85) { index ->
                val amplitude = if (index in listOf(5, 20, 45)) 28000 else 0
                val pcm = ShortArray(320) { sample -> (amplitude * sin(2 * PI * 1000 * sample / 16000)).toInt().toShort() }
                val input = dsp.extract(AudioFrame(1, index * 20_000_000L, pcm))
                val base = baseline.map(input)
                val output = hybrid.map(input, context(input.timestampNs, label))
                assertEquals(base, output)
                if (silent && !output.isSilent) edges.add(index)
                silent = output.isSilent
            }
            assertEquals(listOf(5, 20, 45), edges)
        }
    }
}
