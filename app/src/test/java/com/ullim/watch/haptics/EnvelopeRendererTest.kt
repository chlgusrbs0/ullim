package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.RenderResult
import org.junit.Assert.*
import org.junit.Test

class EnvelopeRendererTest {
    private class Device(limits: EnvelopeLimits?) : EnvelopeDevice {
        override val capabilities = HapticCapabilities(true, true, limits)
        var waves = 0
        var envelopes = 0
        var fail = false
        override fun play(timings: LongArray, amplitudes: IntArray) { waves++ }
        override fun playEnvelope(plan: EnvelopePlan) { envelopes++; if (fail) throw IllegalArgumentException() }
        override fun cancel() {}
    }

    @Test fun validPlanStartsAndEndsAtZeroAndHonorsLimits() {
        val frame = HapticFrame(1, 0, 40, 0.5f)
        val plan = EnvelopePlan.create(frame, EnvelopeLimits(16, 10, 1000, 16000))!!
        assertEquals(0f, plan.points.first().intensity, 0f)
        assertEquals(0f, plan.points.last().intensity, 0f)
        assertEquals(40L, plan.points.sumOf { it.durationMs })
        assertTrue(plan.points.drop(1).all { it.durationMs >= 10 })
    }

    @Test fun unsupportedOrInvalidLimitsUseWaveform() {
        for (limits in listOf(null, EnvelopeLimits(1, 10, 1000, 16000), EnvelopeLimits(16, 30, 1000, 16000))) {
            val device = Device(limits)
            val renderer = AdaptiveHapticRenderer(device, preferEnvelope = true)
            renderer.begin(1)
            assertEquals(RenderResult.SENT, renderer.render(HapticFrame(1, 0, 20, 0.5f)))
            assertEquals(0, device.envelopes)
            assertEquals(1, device.waves)
        }
    }

    @Test fun supportedEnvelopeCanBeExplicitlyEnabledAndFailureFallsBack() {
        val device = Device(EnvelopeLimits(16, 10, 1000, 16000))
        val renderer = AdaptiveHapticRenderer(device, preferEnvelope = true)
        renderer.begin(1)
        assertEquals(RenderResult.SENT, renderer.render(HapticFrame(1, 0, 40, 0.5f)))
        assertEquals(1, device.envelopes)
        device.fail = true
        assertEquals(RenderResult.SENT, renderer.render(HapticFrame(1, 40_000_000, 40, 0.5f)))
        assertEquals(1, device.waves)
        renderer.render(HapticFrame(1, 80_000_000, 40, 0.5f))
        assertEquals(2, device.envelopes)
        assertEquals(2, device.waves)
    }

    @Test fun defaultRemainsWaveformUntilRealDeviceComparison() {
        val device = Device(EnvelopeLimits(16, 10, 1000, 16000))
        val renderer = AdaptiveHapticRenderer(device)
        renderer.begin(1)
        renderer.render(HapticFrame(1, 0, 40, 0.5f))
        assertEquals(0, device.envelopes)
        renderer.cancel()
        assertEquals(RenderResult.STALE, renderer.render(HapticFrame(1, 40_000_000, 40, 0.5f)))
    }
}
