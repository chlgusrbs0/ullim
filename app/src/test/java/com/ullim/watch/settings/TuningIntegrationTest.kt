package com.ullim.watch.settings

import com.ullim.watch.core.*
import com.ullim.watch.dsp.LiveDsp
import com.ullim.watch.haptics.*
import com.ullim.watch.settings.TuningParameter.*
import org.junit.Assert.*
import org.junit.Test

class TuningIntegrationTest {
    private fun audible(index: Int, level: Float = .5f) = AudioFeatures(1, index * 20_000_000L, .1f, .2f, .1f, level = level)
    private fun quiet(index: Int) = AudioFeatures(1, index * 20_000_000L, 0f, 0f, .08f, level = 0f)
    private fun frames(level: Float) = (0..2).map { HapticFrame(1, it * 20_000_000L, 20, level) }

    @Test fun numericEditsHaveBoundsOrderedAnchorsAndAtomicallyRestoredPairs() {
        var t = TuningSettings.DEFAULT.with(WEAK_OUTPUT, .9f)
        assertEquals(t[MIDDLE_OUTPUT], t[WEAK_OUTPUT], 0f)
        t = t.with(MIDDLE_OUTPUT, 0f)
        assertEquals(t[WEAK_OUTPUT], t[MIDDLE_OUTPUT], 0f)
        t = t.with(LONG_END, 1f).with(LONG_START, .9f).with(GROUP_MS, 37f)
        assertEquals(40f, t[GROUP_MS], 0f)
        assertEquals(t, TuningSettings.decode { t[it] })
        assertEquals(TuningSettings.DEFAULT, TuningSettings.decode { if (it == LONG_START) .9f else null })
        assertEquals(TuningSettings.DEFAULT, TuningSettings.decode { Float.NaN })
        assertThrows(IllegalArgumentException::class.java) { t.with(OUTPUT_GAIN, Float.NaN) }
    }

    @Test fun everyAllowedPulseControlPreservesBothSavedCalibrationWaveforms() {
        for (group in listOf(20f, 40f, 60f)) for (minOn in listOf(1f, 4f)) for (similar in listOf(0f, 10f)) {
            for (t in listOf(TuningSettings.DEFAULT.with(LONG_START, .25f).with(LONG_END, .3f),
                TuningSettings.DEFAULT.with(LONG_END, 1f).with(LONG_START, .9f))) {
                val edited = t.with(GROUP_MS, group).with(MIN_ON_MS, minOn).with(SIMILARITY_MS, similar)
                for (level in listOf(.2f, 1f)) {
                    val baseline = PulseFallback.encodeBatch(frames(level))
                    val result = PulseFallback.encodeBatch(frames(level), edited)
                    assertArrayEquals(baseline.timings, result.timings)
                    assertArrayEquals(baseline.amplitudes, result.amplitudes)
                }
                val result = PulseFallback.encodeBatch(frames(.65f), edited)
                assertEquals(60L, result.timings.sum())
                assertTrue(result.timings.all { it >= 0 })
            }
        }
    }

    @Test fun liveReferenceAndOutputCurveChangesAffectSameSessionWithoutClockChange() {
        var tuning = TuningSettings.DEFAULT
        val live = LiveDsp(readTuning = { tuning }) { 1f }
        val fixed = LiveDsp { 1f }
        val mapper = EnvelopeHapticMapper { tuning }
        repeat(30) { i ->
            if (i == 10) tuning = tuning.with(REFERENCE_RMS, .15f)
            val input = AudioFrame(1, i * 20_000_000L, ShortArray(320) { if (it % 2 == 0) 2000 else -2000 })
            val a = live.extract(input); val b = fixed.extract(input)
            if (i < 10) assertEquals(a, b) else assertTrue(a.level > b.level)
            assertEquals(input.timestampNs, mapper.map(a).timestampNs)
        }
        val old = mapper.map(audible(30)).commandedIntensity
        tuning = tuning.with(OUTPUT_GAIN, .1f).with(OUTPUT_ATTACK, 1f).with(OUTPUT_RELEASE, 1f)
        assertTrue(mapper.map(audible(31)).commandedIntensity < old)
    }

    @Test fun anchorsProduceContinuousIntermediateValuesAndBoundedMaximum() {
        val t = TuningSettings.DEFAULT.with(WEAK_OUTPUT, .1f).with(MIDDLE_OUTPUT, .4f)
            .with(STRONG_OUTPUT, .9f).with(OUTPUT_ATTACK, 1f)
        val mapper = EnvelopeHapticMapper { t }
        val values = (1..100).map { mapper.map(audible(it, it / 100f)).commandedIntensity }
        assertTrue(values.zipWithNext().all { (a,b) -> b > a })
        assertEquals(.1f, values[24], .00001f)
        assertEquals(.4f, values[49], .00001f)
        assertEquals(.9f, values[79], .00001f)
        assertEquals(1f, values.last(), .00001f)
    }

    @Test fun optionalGapHoldExpiresFromLastRealSoundAndNeverCreatesSilentOutput() {
        var t = TuningSettings.DEFAULT.with(GAP_HOLD_MS, 80f).with(OUTPUT_FLOOR, .2f)
        val mapper = EnvelopeHapticMapper { t }
        assertTrue(mapper.map(quiet(0)).isSilent)
        mapper.map(audible(1))
        for (i in 2..5) assertFalse(mapper.map(quiet(i)).isSilent)
        assertTrue(mapper.map(quiet(6)).isSilent)
        mapper.map(audible(7)); t = t.with(GAP_HOLD_MS, 0f)
        assertTrue(mapper.map(quiet(8)).isSilent)
        t = t.with(GAP_HOLD_MS, 100f); mapper.map(audible(9)); mapper.reset()
        assertTrue(mapper.map(quiet(10)).isSilent)
    }

    @Test fun aiTuningChangesActualRequestsAndZeroLimitRestoresBaseline() {
        var t = TuningSettings.DEFAULT
        val hybrid = HybridHapticMapper(EnvelopeHapticMapper { t }) { t }
        val baseline = EnvelopeHapticMapper { t }
        var changed = false
        repeat(80) { i ->
            if (i == 40) t = t.with(AI_LIMIT, 0f)
            val f = audible(i, if (i % 2 == 0) .46f else .54f)
            val context = Classification(1, f.timestampNs, f.timestampNs + 1_000_000_000, listOf(ClassScore("Speech", .9f)))
            val actual = hybrid.map(f, context); val plain = baseline.map(f)
            if (i < 40 && actual != plain) changed = true
            if (i >= 40) assertEquals(plain, actual)
        }
        assertTrue(changed)
    }

    @Test fun aiCannotExceedNewLiveOutputCeilingOrFloor() {
        var t = TuningSettings.DEFAULT
        val mapper = HybridHapticMapper(EnvelopeHapticMapper { t }) { t }
        repeat(50) { i ->
            if (i == 30) t = t.with(OUTPUT_CEILING, .48f)
            if (i == 40) t = t.with(OUTPUT_FLOOR, .48f)
            val f = audible(i)
            val output = mapper.map(f, Classification(1, f.timestampNs, f.timestampNs + 1_000_000_000,
                listOf(ClassScore("Speech", .9f)))).commandedIntensity
            if (i >= 30) assertTrue(output <= .48f)
            if (i >= 40) assertEquals(.48f, output, 0f)
        }
    }
}
