package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.haptics.DiagnosticWaveform
import com.ullim.watch.haptics.EnvelopeHapticMapper
import com.ullim.watch.haptics.HapticCapabilities
import com.ullim.watch.haptics.WaveformDevice
import com.ullim.watch.haptics.WaveformRenderer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class InputLoudnessMappingTest {
    private fun frame(index: Int, rms: Double) = AudioFrame(1, index * 20_000_000L,
        ShortArray(320) { sample ->
            (rms * sqrt(2.0) * sin(2 * PI * 1000 * sample / 16000) * 32768).roundToInt().toShort()
        })

    private data class Result(val command: Float, val waveform: DiagnosticWaveform)
    private fun result(rms: Double, knee: Float): Result {
        val dsp = DspProcessor(knee)
        val mapper = EnvelopeHapticMapper()
        val frames = (0..39).map { mapper.map(dsp.extract(frame(it, rms))) }
        var played: DiagnosticWaveform? = null
        val renderer = WaveformRenderer(object : WaveformDevice {
            override val capabilities = HapticCapabilities(true, true, null)
            override fun play(timings: LongArray, amplitudes: IntArray) { played = DiagnosticWaveform(timings, amplitudes) }
            override fun cancel() {}
        }, forcePulseFallback = true)
        renderer.begin(1)
        renderer.renderBatch(frames.takeLast(3))
        return Result(frames.last().commandedIntensity, played!!)
    }

    private fun runs(wave: DiagnosticWaveform) = wave.timings.indices
        .filter { wave.amplitudes[it] != 0 && wave.timings[it] > 0 }.map { wave.timings[it] }

    @Test fun everydayPcmUsesMoreOfTheFinalTactileRange() {
        for (rms in listOf(.02, .05, .08, .15)) {
            val old = result(rms, 1f)
            val updated = result(rms, NoiseGate.DEFAULT_LOUDNESS_KNEE)
            assertTrue(updated.command > old.command)
            assertTrue(runs(updated.waveform).sum() > runs(old.waveform).sum())
            assertEquals(60L, updated.waveform.timings.sum())
            println("Synthetic RMS=$rms: oldRequest=${old.command} newRequest=${updated.command} oldON=${runs(old.waveform)} newON=${runs(updated.waveform)}")
        }
        val soft = result(.02, .3f)
        val middle = result(.08, .3f)
        val loud = result(.15, .3f)
        assertTrue(soft.command < .6f)
        assertTrue(middle.command > .6f && middle.command < .75f)
        assertTrue(loud.command > .75f)
        assertEquals(3, runs(soft.waveform).size)
        assertEquals(1, runs(loud.waveform).size)
        assertTrue(runs(middle.waveform).max() > runs(soft.waveform).max())
        assertTrue(runs(loud.waveform).max() > runs(middle.waveform).max())
    }

    @Test fun upperInputChangesStillProduceDifferentCommandsInsteadOfAnEarlyFlatMaximum() {
        val levels = listOf(.10, .15, .25, .35, .45).map { result(it, .3f).command }
        assertTrue(levels.zipWithNext().all { (a,b) -> b > a })
        assertTrue(levels.last() < 1f)
    }

    @Test fun pcmNoiseHistoryOnsetAndRealSilentEdgesStayUnchanged() {
        val old = DspProcessor(1f)
        val updated = DspProcessor(.3f)
        val oldMapper = EnvelopeHapticMapper()
        val updatedMapper = EnvelopeHapticMapper()
        val oldEdges = mutableListOf<Int>()
        val newEdges = mutableListOf<Int>()
        var wasSilent = true
        var wasNewSilent = true
        repeat(140) { index ->
            val rms = when (index) { in 10..39 -> .02; in 50..79 -> .08; in 90..119 -> .15; else -> 0.0 }
            val pcm = frame(index, rms)
            val before = old.extract(pcm)
            val after = updated.extract(pcm)
            assertEquals(before.rms, after.rms, 0f)
            assertEquals(before.noise, after.noise, 0f)
            assertEquals(before.envelope, after.envelope, 0f)
            assertEquals(before.onset, after.onset, 0f)
            val command = oldMapper.map(before)
            val newCommand = updatedMapper.map(after)
            assertEquals(command.isSilent, newCommand.isSilent)
            assertEquals(command.timestampNs, newCommand.timestampNs)
            if (wasSilent && !command.isSilent) oldEdges.add(index)
            if (wasNewSilent && !newCommand.isSilent) newEdges.add(index)
            wasSilent = command.isSilent; wasNewSilent = newCommand.isSilent
        }
        assertEquals(oldEdges, newEdges)
        assertEquals(3, newEdges.size)
        assertTrue(wasNewSilent)
    }

    @Test fun invalidNormalizationParametersAreRejected() {
        for (knee in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 1.1f)) {
            assertThrows(IllegalArgumentException::class.java) { NoiseGate(knee) }
        }
    }
}
