package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFrame
import org.junit.Assert.*
import org.junit.Test

class TemporalFeaturesTest {
    private fun frame(level: Int, index: Int) = AudioFrame(1, index * 20_000_000L, ShortArray(320) { level.toShort() })

    @Test fun threeKnocksPreserveTheirDistinctIntervals() {
        val dsp = DspProcessor()
        val knocks = listOf(5, 20, 45)
        val detected = (0..80).mapNotNull { index ->
            val features = dsp.extract(frame(if (index in knocks) 28000 else 0, index))
            if (features.onset > 0f) index else null
        }
        assertEquals(knocks, detected)
        println("Knock input/detected frames=$detected; intervalsMs=300,500")
    }

    @Test fun constantSoundCannotGenerateEndlessOnsets() {
        val dsp = DspProcessor()
        val features = (0..250).map { dsp.extract(frame(16000, it)) }
        assertEquals(1, features.count { it.onset > 0f })
        assertEquals("continuous five-second input was truncated", 251, features.count { it.level > 0f })
        println("Constant input: frames=${features.size}, nonSilent=${features.count { it.level > 0f }}, onsets=${features.count { it.onset > 0f }}")
    }

    @Test fun speechLikeModulationProducesContinuousChangingLevelsAndReleasesToZero() {
        val dsp = DspProcessor()
        val levels = (0..100).map { index ->
            val level = if (index % 20 < 10) 6000 else 18000
            dsp.extract(frame(level, index)).level
        }
        assertTrue(levels.distinct().size > 20)
        var tail = dsp.extract(frame(0, 101))
        for (index in 102..200) tail = dsp.extract(frame(0, index))
        assertTrue(tail.isSilent)
    }

    @Test fun refractoryPeriodRejectsCloselySpacedRepeatAndResetRestoresFirstOnset() {
        val onset = OnsetDetector()
        fun features(level: Float, time: Long) = com.ullim.watch.core.AudioFeatures(1, time, level, level, level)
        assertTrue(onset.process(features(0.5f, 0)).onset > 0f)
        onset.process(features(0f, 20_000_000))
        assertEquals(0f, onset.process(features(0.5f, 40_000_000)).onset, 0f)
        onset.reset()
        assertTrue(onset.process(features(0.5f, 0)).onset > 0f)
    }
}
