package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.DspProcessor
import org.junit.Assert.*
import org.junit.Test

class EnvelopeHapticMapperTest {
    @Test fun strongerInputNeverProducesWeakerCommandAtSameCondition() {
        val intensities = listOf(0.01f, 0.1f, 0.3f, 0.6f, 1f).map { level ->
            EnvelopeHapticMapper().map(AudioFeatures(1, 0, level, level, level)).commandedIntensity
        }
        assertTrue(intensities.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(intensities.all { it in 0f..0.85f })
    }

    @Test fun threeKnocksRetainLeadingEdgesAndTheirIntervalsWithoutAi() {
        val dsp = DspProcessor()
        val mapper = EnvelopeHapticMapper()
        val knocks = listOf(5, 20, 45)
        val leading = mutableListOf<Int>()
        var wasSilent = true
        repeat(85) { index ->
            val features = dsp.extract(AudioFrame(1, index * 20_000_000L, ShortArray(320) { if (index in knocks) 28000 else 0 }))
            val output = mapper.map(features)
            if (wasSilent && !output.isSilent) leading.add(index)
            wasSilent = output.isSilent
            assertEquals(features.timestampNs, output.timestampNs)
            assertEquals(20L, output.durationMs)
        }
        assertEquals(knocks, leading)
        assertTrue(wasSilent)
    }

    @Test fun sustainedAudioMapsToContinuousCommandsRatherThanOneAlert() {
        val dsp = DspProcessor()
        val mapper = EnvelopeHapticMapper()
        val output = (0..99).map { index ->
            mapper.map(dsp.extract(AudioFrame(1, index * 20_000_000L, ShortArray(320) { 16000 })))
        }
        assertTrue(output.count { !it.isSilent } > 90)
        assertTrue(output.map { it.commandedIntensity }.distinct().size > 20)
    }

    @Test fun silenceAndNewSessionNeverKeepResidualCommand() {
        val mapper = EnvelopeHapticMapper()
        mapper.map(AudioFeatures(1, 0, 1f, 1f, 1f))
        assertTrue(mapper.map(AudioFeatures(1, 20_000_000, 0f, 0f, 0f)).isSilent)
        assertTrue(mapper.map(AudioFeatures(2, 0, 0f, 0f, 0f)).isSilent)
    }

    @Test fun releasedEnvelopeCannotFillQuietGapBetweenRealEvents() {
        val mapper = EnvelopeHapticMapper()
        mapper.map(AudioFeatures(1, 0, 0.5f, 0.5f, 0.5f))
        assertTrue(mapper.map(AudioFeatures(1, 20_000_000, 0.001f, 0.001f, 0.4f, noise = 0.003f, level = 0.4f)).isSilent)
    }
}
