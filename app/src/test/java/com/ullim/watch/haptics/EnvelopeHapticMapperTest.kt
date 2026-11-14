package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.DspProcessor
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EnvelopeHapticMapperTest {
    @Test fun middleLevelUsesMoreOutputAndMaximumCanReachFullRange() {
        fun settled(level: Float): Float {
            val mapper = EnvelopeHapticMapper()
            var result = 0f
            repeat(30) { index ->
                result = mapper.map(AudioFeatures(1, index * 20_000_000L, level, level, level)).commandedIntensity
            }
            return result
        }
        assertEquals(.8f, settled(.5f), .00001f)
        assertEquals(1f, settled(1f), .00001f)
        assertTrue(listOf(.1f, .2f, .3f, .4f, .5f, .75f, 1f).map(::settled).zipWithNext().all { (a,b) -> a < b })
    }

    @Test fun pcmStrengthAndUserSensitivityKeepTheirOrderThroughDspAndMapper() {
        fun output(amplitude: Int, sensitivity: Float): Float {
            val dsp = DspProcessor().apply { this.sensitivity = sensitivity }
            val mapper = EnvelopeHapticMapper()
            var result = 0f
            repeat(25) { index ->
                val samples = ShortArray(320) { (amplitude * sin(2 * PI * 1000 * it / 16000)).toInt().toShort() }
                result = mapper.map(dsp.extract(AudioFrame(1, index * 20_000_000L, samples))).commandedIntensity
            }
            return result
        }
        val strengths = listOf(2000, 8000, 24000).map { output(it, 1f) }
        assertTrue(strengths.zipWithNext().all { (a, b) -> a < b })
        val sensitivities = listOf(.5f, 1f, 2f).map { output(2000, it) }
        assertTrue(sensitivities.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun fiveSecondTonePreservesEveryCommandAndStopsOnFirstQuietFrame() {
        val dsp = DspProcessor()
        val mapper = EnvelopeHapticMapper()
        repeat(250) { index ->
            val samples = ShortArray(320) { (16000 * sin(2 * PI * 1000 * it / 16000)).toInt().toShort() }
            val output = mapper.map(dsp.extract(AudioFrame(1, index * 20_000_000L, samples)))
            assertFalse("missing command at $index", output.isSilent)
            assertEquals(index * 20_000_000L, output.timestampNs)
            assertEquals(20L, output.durationMs)
        }
        assertTrue(mapper.map(dsp.extract(AudioFrame(1, 5_000_000_000L, ShortArray(320)))).isSilent)
    }

    @Test fun speechLikeAmplitudeModulationRemainsASequenceOfChangingCommands() {
        val dsp = DspProcessor()
        val mapper = EnvelopeHapticMapper()
        val levels = (0..99).map { index ->
            val amplitude = 12000 + (8000 * sin(2 * PI * 3 * index * .02)).toInt()
            val samples = ShortArray(320) { (amplitude * sin(2 * PI * 1000 * it / 16000)).toInt().toShort() }
            mapper.map(dsp.extract(AudioFrame(1, index * 20_000_000L, samples))).commandedIntensity
        }
        assertTrue(levels.distinct().size > 80)
        assertTrue(levels.max() - levels.min() > .1f)
    }
    @Test fun strongerInputNeverProducesWeakerCommandAtSameCondition() {
        val intensities = listOf(0.01f, 0.1f, 0.3f, 0.6f, 1f).map { level ->
            EnvelopeHapticMapper().map(AudioFeatures(1, 0, level, level, level)).commandedIntensity
        }
        assertTrue(intensities.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(intensities.all { it in 0f..1f })
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
        assertTrue(output.first().commandedIntensity < output.last().commandedIntensity)
        assertEquals(output[79].commandedIntensity, output.last().commandedIntensity, .000001f)
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
