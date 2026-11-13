package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class NoiseGateScenarioTest {
    private class Pipeline {
        val extractor = EnvelopeExtractor()
        val gate = NoiseGate()
        fun frame(index: Int, fan: Boolean = true, sound: (Double) -> Double = { 0.0 }): AudioFeatures {
            val pcm = ShortArray(320) { sample ->
                val seconds = (index * 320L + sample) / 16_000.0
                val background = if (fan) .02 * sin(2 * PI * 120 * seconds) else 0.0
                ((background + sound(seconds)).coerceIn(-1.0, 1.0) * 32_767).toInt().toShort()
            }
            return gate.process(extractor.extract(AudioFrame(1, index * 20_000_000L, pcm)))
        }
    }

    @Test fun learnedFanAllowsThreeSeparatedKnocksAndReturnsToQuiet() {
        val pipeline = Pipeline()
        val knockAt = listOf(1_000, 1_020, 1_060)
        val edges = mutableListOf<Int>()
        var wasSilent = true
        var tail = 0f
        repeat(1_250) { index ->
            val active = knockAt.any { index in it until it + 2 }
            val value = pipeline.frame(index) { seconds -> if (active) .55 * sin(2 * PI * 700 * seconds) else 0.0 }
            if (index >= 900 && !value.isSilent && wasSilent) edges.add(index)
            wasSilent = value.isSilent
            if (index >= 1_200) tail = maxOf(tail, value.level)
        }
        assertEquals(knockAt, edges)
        assertEquals(0f, tail, 0f)
        println("Fan+knocks: expected/observed onset frames=$edges; tailMax=$tail")
    }

    @Test fun speechLikeModulationRemainsVisibleAboveLearnedFan() {
        val pipeline = Pipeline()
        var heard = 0
        var peak = 0f
        var last = 0f
        repeat(1_250) { index ->
            val voice = index in 1_000 until 1_100
            val output = pipeline.frame(index) { seconds ->
                if (voice) .3 * (.55 + .45 * sin(2 * PI * 4 * seconds)) * sin(2 * PI * 220 * seconds) else 0.0
            }
            if (voice && !output.isSilent) heard++
            if (voice) peak = maxOf(peak, output.level)
            last = output.level
        }
        assertTrue("voice changes were lost above fan", heard >= 40)
        assertTrue(peak > .35f)
        assertEquals(0f, last, 0f)
        println("Fan+speech modulation: activeFrames=$heard/100 peak=$peak final=$last")
    }

    @Test fun clippingThenMuteStaysFiniteAndDecaysWithoutReopening() {
        val pipeline = Pipeline()
        var previous = 1f
        var last = 0f
        repeat(400) { index ->
            val output = pipeline.frame(index, fan = false) { if (index in 200 until 210) 1.0 else 0.0 }
            assertTrue(output.noise.isFinite() && output.level.isFinite())
            assertTrue(output.level in 0f..1f)
            if (index >= 210) assertTrue("muting reopened/increased output", output.level <= previous + .000001f)
            previous = output.level
            last = output.level
        }
        assertEquals(0f, last, 0f)
    }

    @Test fun louderSignalHasNoSmallerPeakAtTheSameLearnedNoiseCondition() {
        val peaks = listOf(.08, .16, .32).map { amplitude ->
            val pipeline = Pipeline()
            repeat(1_000) { pipeline.frame(it) }
            var peak = 0f
            repeat(10) { offset ->
                val output = pipeline.frame(1_000 + offset) { seconds -> amplitude * sin(2 * PI * 400 * seconds) }
                peak = maxOf(peak, output.level)
            }
            peak
        }
        assertTrue(peaks.first() > 0f)
        assertTrue(peaks.zipWithNext().all { (low, high) -> high >= low })
        println("Same fan, signal amplitudes0.08/0.16/0.32: output peaks=$peaks")
    }
}
