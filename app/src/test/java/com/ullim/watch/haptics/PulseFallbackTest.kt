package com.ullim.watch.haptics

import org.junit.Assert.*
import org.junit.Test

class PulseFallbackTest {
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
