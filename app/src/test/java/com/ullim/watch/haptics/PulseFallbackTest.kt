package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import org.junit.Assert.*
import org.junit.Test

class PulseFallbackTest {
    private fun frames(vararg levels: Float) = levels.mapIndexed { index, level ->
        HapticFrame(1, index * 20_000_000L, 20, level)
    }

    @Test fun middleLiveFramesRetainTheOriginalShortPulsePattern() {
        val wave = PulseFallback.encodeBatch(frames(.45f, .5f, .55f))
        assertArrayEquals(longArrayOf(0, 9, 11, 10, 10, 11, 9), wave.timings)
        assertArrayEquals(intArrayOf(0, -1, 0, -1, 0, -1, 0), wave.amplitudes)
        assertEquals(60L, wave.timings.sum())
    }

    @Test fun transitionTransfersOnTimeGraduallyRatherThanSwitchingAtEightyPercent() {
        fun wave(level: Float) = PulseFallback.encodeBatch(frames(level, level, level))
        assertArrayEquals(longArrayOf(0, 15, 5, 15, 5, 15, 5), wave(.75f).timings)
        assertArrayEquals(longArrayOf(0, 32, 4, 8, 4, 8, 4), wave(.8f).timings)
        assertArrayEquals(longArrayOf(0, 51, 9), wave(.85f).timings)
        val longest = (75..85).map { value ->
            val encoded = wave(value / 100f)
            assertEquals(60L, encoded.timings.sum())
            val oldOn = PulseFallback.encode(value / 100f, 20).timings[1] * 3
            assertEquals(oldOn, encoded.timings.indices.sumOf { if (encoded.amplitudes[it] == -1) encoded.timings[it] else 0 })
            encoded.timings.indices.filter { encoded.amplitudes[it] == -1 }.maxOf { encoded.timings[it] }
        }
        assertTrue(longest.zipWithNext().all { (a,b) -> b >= a })
        assertTrue("transition needs intermediate lengths, not two modes", longest.distinct().size >= 8)
    }

    @Test fun aRealSilentFrameAndDistinctStrengthChangesKeepTheirTimeSlots() {
        val silent = PulseFallback.encodeBatch(frames(.5f, 0f, .5f))
        assertArrayEquals(longArrayOf(0, 10, 30, 10, 10), silent.timings)
        val contrast = PulseFallback.encodeBatch(frames(.2f, .8f, .2f))
        assertArrayEquals(longArrayOf(0, 4, 16, 16, 4, 4, 16), contrast.timings)
    }

    @Test fun groupingStaysWithinSixtyMsAndRetainsWeakMiddleAndFullOutput() {
        val onTimes = listOf(.2f, .5f, 1f).map { level ->
            val wave = PulseFallback.encodeBatch(frames(level, level, level))
            wave.timings.indices.sumOf { if (wave.amplitudes[it] == -1) wave.timings[it] else 0 }
        }
        assertEquals(listOf(12L, 30L, 60L), onTimes)
        val longer = PulseFallback.encodeBatch(frames(.9f, .9f, .9f, .9f, .9f, .9f))
        assertArrayEquals(longArrayOf(0, 54, 6, 54, 6), longer.timings)
    }

    @Test fun fullRequestHasNoArtificialOffTime() {
        val waveform = PulseFallback.encode(1f, 20)
        assertEquals(20L, waveform.timings.indices.sumOf { if (waveform.amplitudes[it] == -1) waveform.timings[it] else 0 })
        assertEquals(20L, waveform.timings.sum())
    }

    @Test fun strongerRequestedLevelUsesMoreOnTimeWithoutAmplitudeClaims() {
        val onTimes = listOf(0.1f, 0.5f, 0.85f).map { level ->
            val wave = PulseFallback.encode(level, 20)
            wave.timings.indices.sumOf { if (wave.amplitudes[it] == -1) wave.timings[it] else 0 }
        }
        assertTrue(onTimes.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun pulseDurationIsBoundedAndOnlyOnOffIsUsed() {
        for (duration in listOf(1L, 20L, 1000L)) {
            val wave = PulseFallback.encode(1f, duration)
            assertEquals(duration, wave.timings.sum())
            assertTrue(wave.timings.all { it >= 0 })
            assertTrue(wave.amplitudes.all { it == 0 || it == -1 })
        }
    }

    @Test fun silenceNeverContainsOnPulseAndInvalidInputIsRejected() {
        assertTrue(PulseFallback.encode(0f, 20).amplitudes.all { it == 0 })
        assertThrows(IllegalArgumentException::class.java) { PulseFallback.encode(Float.NaN, 20) }
        assertThrows(IllegalArgumentException::class.java) { PulseFallback.encode(1f, 1001) }
    }
}
