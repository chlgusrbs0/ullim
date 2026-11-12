package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.RenderResult
import org.junit.Assert.*
import org.junit.Test

class HapticSchedulerTest {
    private class Renderer : SessionRenderer {
        val batches = mutableListOf<List<HapticFrame>>()
        var cancelled = 0
        override fun begin(sessionId: Long) {}
        override fun render(frame: HapticFrame): RenderResult = renderBatch(listOf(frame))
        override fun renderBatch(frames: List<HapticFrame>): RenderResult { batches.add(frames); return RenderResult.SENT }
        override fun cancel() { cancelled++ }
    }

    @Test fun sixtySecondStreamUsesBoundedBatchesAndPreservesEachCommand() {
        var now = 0L
        val renderer = Renderer()
        val scheduler = HapticScheduler(renderer, { now })
        scheduler.begin(1)
        repeat(3000) { index ->
            now = index * 20_000_000L
            assertTrue(scheduler.offer(HapticFrame(1, now, 20, (index % 5 + 1) / 10f)))
        }
        assertEquals(1000, renderer.batches.size)
        assertEquals(3000, renderer.batches.sumOf { it.size })
        assertTrue(renderer.batches.all { it.size <= 3 && it.sumOf { frame -> frame.durationMs } == 60L })
    }

    @Test fun stoppingDropsPendingAndOldSessionCannotReplay() {
        val renderer = Renderer()
        val scheduler = HapticScheduler(renderer, { 0 })
        scheduler.begin(1)
        scheduler.offer(HapticFrame(1, 0, 20, 0.5f))
        scheduler.stop()
        assertFalse(scheduler.offer(HapticFrame(1, 0, 20, 0.5f)))
        scheduler.begin(2)
        assertFalse(scheduler.offer(HapticFrame(1, 0, 20, 0.5f)))
        assertTrue(renderer.batches.isEmpty())
        assertTrue(renderer.cancelled > 0)
    }

    @Test fun silenceFlushesEarlierFramesAndPreservesRealGap() {
        var now = 0L
        val renderer = Renderer()
        val scheduler = HapticScheduler(renderer, { now })
        scheduler.begin(1)
        scheduler.offer(HapticFrame(1, now, 20, 0.5f))
        now = 20_000_000
        scheduler.offer(HapticFrame(1, now, 20, 0f))
        assertEquals(listOf(0.5f, 0f), renderer.batches.single().map { it.commandedIntensity })
    }

    @Test fun lateAndFutureFramesCannotGrowQueueOrReplayOldOutput() {
        var now = 1_000_000_000L
        val renderer = Renderer()
        val scheduler = HapticScheduler(renderer, { now })
        scheduler.begin(1)
        assertFalse(scheduler.offer(HapticFrame(1, 0, 20, 0.5f)))
        assertTrue(renderer.batches.single().single().isSilent)
        assertFalse(scheduler.offer(HapticFrame(1, 2_000_000_000, 20, 0.5f)))
        now += 20_000_000
        assertTrue(scheduler.offer(HapticFrame(1, now, 20, 0.5f)))
    }

    @Test fun waveformBatchPreservesAmplitudeAndTimingInsteadOfAveraging() {
        val waves = mutableListOf<DiagnosticWaveform>()
        val device = object : WaveformDevice {
            override val capabilities = HapticCapabilities(true, true, null)
            override fun play(timings: LongArray, amplitudes: IntArray) { waves.add(DiagnosticWaveform(timings, amplitudes)) }
            override fun cancel() {}
        }
        val renderer = WaveformRenderer(device)
        renderer.begin(1)
        assertEquals(RenderResult.SENT, renderer.renderBatch(listOf(HapticFrame(1, 0, 20, 0.2f), HapticFrame(1, 20_000_000, 20, 0.8f))))
        assertEquals(40L, waves.single().timings.sum())
        assertTrue(waves.single().amplitudes[1] < waves.single().amplitudes[2])
    }

    @Test fun sameSessionCommandsWorkWithWaveformPulseAndEnvelopeBackends() {
        for (mode in 0..2) {
            var now = 0L
            var outputs = 0
            var totalMs = 0L
            val device = object : EnvelopeDevice {
                override val capabilities = HapticCapabilities(true, mode != 1, if (mode == 2) EnvelopeLimits(16, 10, 1000, 16000) else null)
                override fun play(timings: LongArray, amplitudes: IntArray) { outputs++; totalMs += timings.sum() }
                override fun playEnvelope(plan: EnvelopePlan) { outputs++; totalMs += plan.points.sumOf { it.durationMs } }
                override fun cancel() {}
            }
            val renderer = if (mode == 2) AdaptiveHapticRenderer(device, true) else WaveformRenderer(device)
            val scheduler = HapticScheduler(renderer, { now }, chunkMs = 20)
            scheduler.begin(1)
            for (level in listOf(0.2f, 0.8f, 0f)) {
                assertTrue(scheduler.offer(HapticFrame(1, now, 20, level)))
                now += 20_000_000
            }
            assertEquals(2, outputs)
            assertEquals(40L, totalMs)
            scheduler.stop()
            assertFalse(scheduler.offer(HapticFrame(1, now, 20, 0.8f)))
            assertEquals(2, outputs)
        }
    }
}
