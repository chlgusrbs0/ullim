package com.ullim.watch.haptics

import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class HapticComparisonTest {
    private class Device : WaveformDevice {
        override val capabilities = HapticCapabilities(true, true, null)
        val calls = mutableListOf<Pair<Long, DiagnosticWaveform>>()
        var cancellations = 0
        var fail = false
        override fun play(timings: LongArray, amplitudes: IntArray) {
            if (fail) throw SecurityException("not recorded")
            calls += SystemClock.uptimeMillis() to DiagnosticWaveform(timings, amplitudes)
        }
        override fun cancel() { cancellations++ }
    }

    @Test fun patternsControlAmplitudeAndTotalDurationButSeparateSegmentationAndReplacement() {
        for ((pattern, expectedCalls, expectedSegments) in listOf(
            Triple(ComparisonPattern.SINGLE, 1, 1),
            Triple(ComparisonPattern.SEGMENTS, 1, 30),
            Triple(ComparisonPattern.CHUNKS_60, 10, 1),
            Triple(ComparisonPattern.CHUNKS_200, 3, 1),
        )) {
            val device = Device()
            var state = ComparisonState()
            val comparison = HapticComparison(device, onUpdate = { state = it })
            comparison.start(pattern)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650))
            assertEquals(expectedCalls, device.calls.size)
            assertEquals(600L, device.calls.sumOf { it.second.timings.sum() })
            assertTrue(device.calls.all { it.second.timings.size == expectedSegments })
            assertTrue(device.calls.flatMap { it.second.amplitudes.toList() }.all { it == 220 })
            assertEquals(ComparisonPhase.COMPLETE, state.phase)
            assertEquals(expectedCalls, state.commands)
            assertEquals(0L, state.maxDispatchLateMs)
            if (expectedCalls > 1) assertEquals(600L / expectedCalls,
                device.calls[1].first - device.calls[0].first)
            comparison.stop()
        }
    }

    @Test fun stopAndReplacementInvalidateEveryPendingOldCommand() {
        val device = Device()
        val comparison = HapticComparison(device)
        comparison.start(ComparisonPattern.CHUNKS_60)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(130))
        assertEquals(3, device.calls.size)
        comparison.start(ComparisonPattern.SINGLE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertEquals(4, device.calls.size)
        comparison.start(ComparisonPattern.CHUNKS_60)
        val before = device.calls.size
        comparison.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(before, device.calls.size)
        assertTrue(device.cancellations >= 4)
    }

    @Test fun lateDispatchAbortsInsteadOfBurstingMissedChunks() {
        val device = Device()
        var now = 0L
        var state = ComparisonState()
        val comparison = HapticComparison(device, clockMs = { now }, onUpdate = { state = it })
        comparison.start(ComparisonPattern.CHUNKS_60)
        now = 500
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        assertEquals(1, device.calls.size)
        assertEquals(ComparisonPhase.LATE, state.phase)
        assertEquals(440L, state.maxDispatchLateMs)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1, device.calls.size)
    }

    @Test fun nativeFailureStopsTheSequenceWithoutAutomaticRetry() {
        val device = Device()
        var state = ComparisonState()
        val comparison = HapticComparison(device, onUpdate = { state = it })
        comparison.start(ComparisonPattern.CHUNKS_60)
        device.fail = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1, device.calls.size)
        assertEquals(ComparisonPhase.FAILED, state.phase)
        assertEquals(1, state.commands)
    }
}
