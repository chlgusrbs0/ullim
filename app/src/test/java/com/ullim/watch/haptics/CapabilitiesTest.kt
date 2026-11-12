package com.ullim.watch.haptics

import org.junit.Assert.*
import org.junit.Test

class CapabilitiesTest {
    private class Probe(override val sdkInt: Int, val present: Boolean = true) : HapticProbe {
        var envelopeCalls = 0
        override fun hasVibrator() = present
        override fun hasAmplitudeControl() = true
        override fun envelopeLimits(): EnvelopeLimits? {
            envelopeCalls++
            check(sdkInt >= 36)
            return EnvelopeLimits(16, 10, 1000, 16000)
        }
    }

    @Test fun oldOsNeverCallsEnvelopeApi() {
        val probe = Probe(34)
        val capabilities = HapticCapabilities.detect(probe)
        assertTrue(capabilities.available)
        assertNull(capabilities.envelope)
        assertEquals(0, probe.envelopeCalls)
    }

    @Test fun noMotorSkipsEveryAdvancedProbe() {
        val probe = Probe(36, false)
        val capabilities = HapticCapabilities.detect(probe)
        assertFalse(capabilities.available)
        assertFalse(capabilities.amplitudeControl)
        assertEquals(0, probe.envelopeCalls)
    }

    @Test fun supportedOsReportsLimits() {
        val probe = Probe(36)
        assertEquals(16, HapticCapabilities.detect(probe).envelope?.maxPoints)
        assertEquals(1, probe.envelopeCalls)
    }

    @Test fun probeFailureIsVisibleAndDisablesOutput() {
        val broken = object : HapticProbe {
            override val sdkInt = 36
            override fun hasVibrator(): Boolean = throw SecurityException()
            override fun hasAmplitudeControl() = true
            override fun envelopeLimits(): EnvelopeLimits? = null
        }
        val capabilities = HapticCapabilities.detect(broken)
        assertFalse(capabilities.available)
        assertTrue(capabilities.probeFailed)
    }

    @Test fun diagnosticPatternsAreFiniteAndStrengthFallbackIsExplicit() {
        for (pattern in DiagnosticPattern.entries) {
            val waveform = pattern.waveform(true)
            assertEquals(waveform.timings.size, waveform.amplitudes.size)
            assertTrue(waveform.timings.sum() in 1..2000)
            assertTrue(waveform.amplitudes.all { it in 0..255 })
            assertTrue(pattern.waveform(false).amplitudes.all { it == 0 || it == -1 })
        }
        assertTrue(DiagnosticPattern.WEAK.waveform(true).amplitudes.max() < DiagnosticPattern.STRONG.waveform(true).amplitudes.max())
        assertEquals(3, DiagnosticPattern.RHYTHM.waveform(true).amplitudes.count { it > 0 })
    }
}
