package com.ullim.watch.dsp

import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.core.AudioFrame
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DspBoundaryTest {
    private enum class Signal { SILENCE, LOW, CLIPPING, TONE, BAND_NOISE, KNOCK, SPEECH }

    private fun signal(kind: Signal, frame: Int): ShortArray {
        val random = Random(frame)
        var filtered = 0f
        return ShortArray(320) { sample ->
            val t = (frame * 320L + sample) / 16000.0
            val value = when (kind) {
                Signal.SILENCE -> 0.0
                Signal.LOW -> 0.001 * sin(2 * PI * 440 * t)
                Signal.CLIPPING -> if (sample % 2 == 0) -1.0 else 1.0
                Signal.TONE -> 0.4 * sin(2 * PI * 440 * t)
                Signal.BAND_NOISE -> { filtered += (random.nextFloat() * 2 - 1 - filtered) * 0.2f; filtered * 0.2.toDouble() }
                Signal.KNOCK -> if (frame % 20 == 0 && sample < 80) 0.8 * random.nextDouble(-1.0, 1.0) else 0.0
                Signal.SPEECH -> (0.1 + 0.3 * (1 + sin(2 * PI * 4 * t)) / 2) * sin(2 * PI * 220 * t)
            }
            (value * 32768).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    @Test fun generatedFixturesRemainFiniteAndBounded() {
        for (kind in Signal.entries) {
            val dsp = DspProcessor()
            repeat(300) { index ->
                val output = dsp.extract(AudioFrame(1, index * 20_000_000L, signal(kind, index)))
                assertTrue(listOf(output.rms, output.peak, output.envelope, output.noise, output.onset, output.level).all { it.isFinite() && it in 0f..1f })
            }
        }
    }

    @Test fun invalidLengthAndReorderedFramesAreMutedWithoutAdvancingDspState() {
        val dsp = DspProcessor()
        dsp.extract(AudioFrame(1, 20_000_000, ShortArray(320) { 10000 }))
        assertTrue(dsp.extract(AudioFrame(1, 0, ShortArray(320) { 32767 })).isSilent)
        assertTrue(dsp.extract(AudioFrame(1, 40_000_000, ShortArray(319) { 32767 })).isSilent)
    }

    @Test fun discontinuityCannotKeepPreviousAudioEnvelope() {
        val dsp = DspProcessor()
        dsp.extract(AudioFrame(1, 0, ShortArray(320) { 32767 }))
        assertTrue(dsp.extract(AudioFrame(1, 2_000_000_000, ShortArray(320))).isSilent)
    }

    @Test fun tenMinuteDspStreamReportsDesktopTimingAndHeap() {
        val dsp = DspProcessor()
        val durations = LongArray(30_000)
        var count = 0
        val fanout = AudioFanOut { frame ->
            val start = System.nanoTime()
            dsp.extract(frame)
            durations[count++] = System.nanoTime() - start
        }
        fanout.start(1)
        val runtime = Runtime.getRuntime()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        repeat(30_000) { index ->
            fanout.accept(AudioFrame(1, index * 20_000_000L, signal(Signal.entries[(index / 100) % Signal.entries.size], index)))
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        durations.sort()
        val p95 = durations[28_499]
        assertEquals(30_000, count)
        assertTrue(p95 < 20_000_000)
        assertTrue(elapsedMs < 600_000)
        fanout.stop()
        assertFalse(fanout.accept(AudioFrame(1, 600_000_000_000, ShortArray(320))))
        println("Desktop synthetic 10min: frames=$count elapsedMs=$elapsedMs DSP-p95-ns=$p95 DSP-max-ns=${durations.last()} heap-before=$heapBefore heap-after=$heapAfter; heap includes GC timing, not retained-memory or Watch7 proof")
    }
}
