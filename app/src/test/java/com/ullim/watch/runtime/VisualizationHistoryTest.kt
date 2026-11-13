package com.ullim.watch.runtime

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.RenderResult
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.haptics.HybridHapticMapper
import com.ullim.watch.haptics.HapticScheduler
import com.ullim.watch.haptics.SessionRenderer
import org.junit.Assert.*
import org.junit.Test

class VisualizationHistoryTest {
    private fun offer(history: VisualizationHistory, session: Long, time: Long, value: Float = .5f) {
        history.offer(AudioFeatures(session, time, value, value, value), HapticFrame(session, time, 20, value))
    }

    @Test fun numericHistoryMatchesActualSyntheticPcmDspAndCommandOnOneTimeline() {
        val history = VisualizationHistory()
        history.begin(1)
        val dsp = DspProcessor()
        val mapper = HybridHapticMapper()
        var now = 0L
        val rendered = mutableListOf<HapticFrame>()
        val renderer = object : SessionRenderer {
            override fun begin(sessionId: Long) {}
            override fun render(frame: HapticFrame): RenderResult { rendered.add(frame); return RenderResult.SENT }
            override fun renderBatch(frames: List<HapticFrame>): RenderResult { rendered.addAll(frames); return RenderResult.SENT }
            override fun cancel() {}
        }
        val scheduler = HapticScheduler(renderer, { now })
        scheduler.begin(1)
        val expected = (0 until 150).map { i ->
            val amplitude = if (i in 50..74) 18_000 else 0
            val frame = AudioFrame(1, i * 20_000_000L, ShortArray(320) { if (it % 2 == 0) amplitude.toShort() else (-amplitude).toShort() })
            val input = dsp.extract(frame)
            val output = mapper.map(input)
            now = frame.timestampNs
            assertTrue(scheduler.offer(output))
            history.offer(input, output)
            input to output
        }
        // No UI snapshot was requested while DSP and all renderer batches ran.
        assertEquals(expected.map { it.second }, rendered)
        val snapshot = history.snapshot(3_000_000_000L)
        assertEquals(150, snapshot.points.size)
        snapshot.points.zip(expected).forEach { (point, pair) ->
            assertEquals(pair.first.timestampNs, point.timestampNs)
            assertEquals(pair.first.rms, point.rms)
            assertEquals(pair.first.envelope, point.envelope)
            assertEquals(pair.second.commandedIntensity, point.commanded)
            assertEquals(pair.second.durationMs, point.durationMs)
        }
        assertTrue(snapshot.points.any { it.rms > 0 && it.commanded > 0 })
        assertTrue(snapshot.points.take(50).all { it.rms == 0f && it.commanded == 0f })
    }

    @Test fun processingWithNoUiReaderStaysBoundedAndExpiresSilenceAndOldData() {
        val history = VisualizationHistory()
        history.begin(1)
        repeat(10_000) { offer(history, 1, it * 20_000_000L) }
        val snapshot = history.snapshot(200_000_000_000L)
        assertTrue(snapshot.points.size in 250..256)
        assertEquals(195_000_000_000L, snapshot.startNs)
        assertEquals(200_000_000_000L, snapshot.endNs)
        assertTrue(snapshot.points.all { it.timestampNs >= snapshot.startNs })
        assertTrue(history.snapshot(206_000_000_000L).points.isEmpty())
    }

    @Test fun stoppedOrReplacedSessionsCannotPolluteNewHistoryAndInvalidPairsAreRejected() {
        val history = VisualizationHistory()
        history.begin(1); offer(history, 1, 0)
        history.begin(2); offer(history, 1, 20_000_000)
        offer(history, 2, 40_000_000)
        offer(history, 2, 40_000_000, .9f)
        history.offer(AudioFeatures(2, 60_000_000, .5f, .5f, .5f), HapticFrame(2, 80_000_000, 20, .8f))
        assertEquals(listOf(40_000_000L), history.snapshot(100_000_000).points.map { it.timestampNs })
        history.end(1)
        assertEquals(1, history.snapshot(100_000_000).points.size)
        history.end(2); offer(history, 2, 60_000_000)
        assertTrue(history.snapshot(100_000_000).points.isEmpty())
    }
}
