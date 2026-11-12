package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.RenderResult
import org.junit.Assert.*
import org.junit.Test

class WaveformRendererTest {
    private class Device(override val capabilities: HapticCapabilities) : WaveformDevice {
        val played = mutableListOf<DiagnosticWaveform>()
        var cancellations = 0
        var fail = false
        override fun play(timings: LongArray, amplitudes: IntArray) {
            if (fail) throw SecurityException()
            played.add(DiagnosticWaveform(timings, amplitudes))
        }
        override fun cancel() { cancellations++ }
    }

    @Test fun amplitudeIsLimitedAndDurationIsFinite() {
        val device = Device(HapticCapabilities(true, true, null))
        val renderer = WaveformRenderer(device)
        renderer.begin(1)
        assertEquals(RenderResult.SENT, renderer.render(HapticFrame(1, 0, 20, 1f)))
        assertEquals(20L, device.played.single().timings.sum())
        assertTrue(device.played.single().amplitudes.max() <= 217)
    }

    @Test fun unsupportedMotorNeverReceivesCommands() {
        val device = Device(HapticCapabilities(false, false, null))
        val renderer = WaveformRenderer(device)
        renderer.begin(1)
        assertEquals(RenderResult.UNSUPPORTED, renderer.render(HapticFrame(1, 0, 20, 0.5f)))
        assertTrue(device.played.isEmpty())
    }

    @Test fun missingAmplitudeControlIsExplicitAndUsesDefaultAmplitude() {
        val device = Device(HapticCapabilities(true, false, null))
        val renderer = WaveformRenderer(device)
        renderer.begin(1)
        renderer.render(HapticFrame(1, 0, 20, 0.5f))
        assertTrue(renderer.strengthLimited)
        assertEquals(-1, device.played.single().amplitudes.last())
    }

    @Test fun staleSessionsDuplicateTimeAndStoppedCommandsCannotVibrate() {
        val device = Device(HapticCapabilities(true, true, null))
        val renderer = WaveformRenderer(device)
        renderer.begin(2)
        assertEquals(RenderResult.STALE, renderer.render(HapticFrame(1, 0, 20, 1f)))
        renderer.render(HapticFrame(2, 0, 20, 0.5f))
        assertEquals(RenderResult.STALE, renderer.render(HapticFrame(2, 0, 20, 1f)))
        renderer.cancel()
        assertEquals(RenderResult.STALE, renderer.render(HapticFrame(2, 20_000_000, 20, 1f)))
        assertEquals(1, device.played.size)
    }

    @Test fun silenceCancelsAndOutputFailureIsReported() {
        val device = Device(HapticCapabilities(true, true, null))
        val renderer = WaveformRenderer(device)
        renderer.begin(1)
        assertEquals(RenderResult.SILENT, renderer.render(HapticFrame(1, 0, 20, 0f)))
        assertTrue(device.cancellations >= 2)
        device.fail = true
        assertEquals(RenderResult.FAILED, renderer.render(HapticFrame(1, 20_000_000, 20, 0.5f)))
        assertTrue(device.cancellations >= 3)
    }
}
