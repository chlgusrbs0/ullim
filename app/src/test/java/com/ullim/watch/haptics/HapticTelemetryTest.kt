package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.HapticFrame
import org.junit.Assert.*
import org.junit.Test

class HapticTelemetryTest {
    @Test fun separatesAiChangesMotorFilteringAndCommandBandsWithoutAudio() {
        val trace = HapticTelemetry(7, windowFrames = 4)
        for ((index, intensity) in listOf(.2f, .5f, .8f, 0f).withIndex()) {
            val t = index * 20_000_000L
            trace.offer(AudioFeatures(7, t, intensity, intensity, intensity),
                HapticFrame(7, t, 20, intensity), true, t,
                rawRms = .4f, motorFiltered = index >= 2, aiAdjusted = index < 2)
        }
        val value = trace.takeLatest()!!
        assertEquals(2, value.motorFilteredFrames)
        assertEquals(1, value.filteredQuietFrames)
        assertEquals(2, value.aiAdjustedFrames)
        assertEquals(1, value.lowCommandFrames)
        assertEquals(1, value.middleCommandFrames)
        assertEquals(1, value.highCommandFrames)
        repeat(4) { index ->
            val t = 100_000_000L + index * 20_000_000L
            trace.offer(AudioFeatures(7, t, 0f, 0f, 0f), HapticFrame(7, t, 20, 0f), true, t)
        }
        assertEquals(0, trace.takeLatest()!!.aiAdjustedFrames)
    }

    private fun features(ts: Long, rms: Float, envelope: Float = rms, noise: Float = 0f, level: Float = envelope, session: Long = 7) =
        AudioFeatures(session, ts, rms, rms, envelope, noise = noise, level = level)

    @Test fun reportsMeasuredRangesAndRequestTimingOnlyAfterFullWindow() {
        val trace = HapticTelemetry(7, windowFrames = 2)
        trace.recordWaveform(longArrayOf(0, 20), intArrayOf(0, 26), 100_000_000)
        trace.offer(features(0, .1f, .2f, .01f, .3f), HapticFrame(7, 0, 20, .4f), true, 110_000_000)
        assertNull(trace.takeLatest())
        trace.recordWaveform(longArrayOf(0, 20), intArrayOf(0, 204), 160_000_000)
        trace.offer(features(20_000_000, .4f, .5f, .02f, .6f), HapticFrame(7, 20_000_000, 20, .8f), false, 170_000_000)
        val snapshot = trace.takeLatest()!!
        assertEquals(2, snapshot.frames)
        assertEquals(1, snapshot.rejectedFrames)
        assertEquals(2, snapshot.nonzeroCommandFrames)
        assertEquals(.1f, snapshot.rmsMin, 0f)
        assertEquals(.4f, snapshot.rmsMax, 0f)
        assertEquals(.2f, snapshot.envelopeMin, 0f)
        assertEquals(.5f, snapshot.envelopeMax, 0f)
        assertEquals(.01f, snapshot.noiseMin, 0f)
        assertEquals(.02f, snapshot.noiseMax, 0f)
        assertEquals(.3f, snapshot.levelMin, 0f)
        assertEquals(.6f, snapshot.levelMax, 0f)
        assertEquals(.4f, snapshot.commandMin, 0f)
        assertEquals(.8f, snapshot.commandMax, 0f)
        assertEquals(110_000_000L, snapshot.minFrameAgeNs)
        assertEquals(150_000_000L, snapshot.maxFrameAgeNs)
        assertEquals(60_000_000L, snapshot.maxCallbackGapNs)
        assertEquals(2, snapshot.waveformRequests)
        assertEquals(26, snapshot.amplitudeMin)
        assertEquals(204, snapshot.amplitudeMax)
        assertEquals(40L, snapshot.requestedDurationMs)
        assertEquals(60_000_000L, snapshot.maxWaveformGapNs)
        assertNull(trace.takeLatest())
    }

    @Test fun latestWindowReplacesUnreadHistoryAndResetsRanges() {
        val trace = HapticTelemetry(7, windowFrames = 1)
        trace.offer(features(0, .8f), HapticFrame(7, 0, 20, .7f), false, 1_000_000)
        trace.offer(features(20_000_000, 0f), HapticFrame(7, 20_000_000, 20, 0f), true, 21_000_000)
        val snapshot = trace.takeLatest()!!
        assertEquals(20_000_000L, snapshot.firstSampleNs)
        assertEquals(0f, snapshot.rmsMin, 0f)
        assertEquals(0f, snapshot.rmsMax, 0f)
        assertEquals(0f, snapshot.commandMax, 0f)
        assertEquals(0, snapshot.nonzeroCommandFrames)
        assertEquals(0, snapshot.rejectedFrames)
        assertEquals(0, snapshot.waveformRequests)
        assertEquals(0, snapshot.amplitudeMin)
        assertNull(trace.takeLatest())
    }

    @Test fun foreignOrMismatchedFramesDoNotEnterSessionMetrics() {
        val trace = HapticTelemetry(7, windowFrames = 1)
        trace.offer(features(0, .9f, session = 8), HapticFrame(8, 0, 20, .9f), true, 10)
        trace.offer(features(0, .9f), HapticFrame(7, 1, 20, .9f), true, 10)
        assertNull(trace.takeLatest())
        trace.offer(features(20, .1f), HapticFrame(7, 20, 20, .2f), true, 10)
        val snapshot = trace.takeLatest()!!
        assertEquals(7L, snapshot.sessionId)
        assertEquals(1, snapshot.frames)
        assertEquals(.1f, snapshot.rmsMax, 0f)
        assertEquals(-10L, snapshot.minFrameAgeNs)
    }

    @Test fun waveformGapIsPreservedAcrossWindowBoundaries() {
        val trace = HapticTelemetry(7, windowFrames = 1)
        trace.recordWaveform(longArrayOf(20), intArrayOf(50), 0)
        trace.offer(features(0, .1f), HapticFrame(7, 0, 20, .1f), true, 0)
        trace.takeLatest()
        trace.recordWaveform(longArrayOf(20), intArrayOf(70), 200_000_000)
        trace.offer(features(20_000_000, .2f), HapticFrame(7, 20_000_000, 20, .2f), true, 200_000_000)
        assertEquals(200_000_000L, trace.takeLatest()!!.maxWaveformGapNs)
    }

    private class Device : EnvelopeDevice {
        override val capabilities = HapticCapabilities(true, true, EnvelopeLimits(16, 10, 1000, 16000))
        var timings: LongArray? = null
        var amplitudes: IntArray? = null
        var plan: EnvelopePlan? = null
        var cancels = 0
        var fail = false
        override fun play(timings: LongArray, amplitudes: IntArray) {
            this.timings = timings
            this.amplitudes = amplitudes
            if (fail) error("native failure")
        }
        override fun playEnvelope(plan: EnvelopePlan) { this.plan = plan }
        override fun cancel() { cancels++ }
    }

    @Test fun tracingAdapterPassesOriginalRequestsWithoutChangingOutput() {
        val device = Device()
        val trace = HapticTelemetry(7, windowFrames = 1)
        val wrapper = TracingEnvelopeDevice(device, trace) { 100 }
        val timings = longArrayOf(0, 20, 20)
        val amplitudes = intArrayOf(0, 40, 200)
        wrapper.play(timings, amplitudes)
        assertSame(timings, device.timings)
        assertSame(amplitudes, device.amplitudes)
        assertArrayEquals(longArrayOf(0, 20, 20), timings)
        assertArrayEquals(intArrayOf(0, 40, 200), amplitudes)
        val plan = EnvelopePlan.create(HapticFrame(7, 0, 100, .5f), device.capabilities.envelope!!)!!
        wrapper.playEnvelope(plan)
        assertSame(plan, device.plan)
        wrapper.cancel()
        assertEquals(1, device.cancels)
        trace.offer(features(0, .1f), HapticFrame(7, 0, 20, .2f), true, 100)
        val snapshot = trace.takeLatest()!!
        assertEquals(1, snapshot.waveformRequests)
        assertEquals(1, snapshot.cancelRequests)
        assertEquals(40, snapshot.amplitudeMin)
        assertEquals(200, snapshot.amplitudeMax)
    }

    @Test fun tracingAdapterPreservesNativeFailureWithoutSendingAnotherRequest() {
        val device = Device().apply { fail = true }
        val wrapper = TracingEnvelopeDevice(device, HapticTelemetry(7))
        assertThrows(IllegalStateException::class.java) { wrapper.play(longArrayOf(20), intArrayOf(100)) }
        assertEquals(0, device.cancels)
        assertNull(device.plan)
    }
}
