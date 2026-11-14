package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.dsp.FrameSpectrum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class MotorNoiseSuppressorTest {
    private fun tone(hz: Int, amplitude: Int, phase: Int = 0) = ShortArray(320) {
        (sin(2 * PI * hz * (it + phase) / 16000) * amplitude).roundToInt().toShort()
    }
    private fun frame(index: Int, samples: ShortArray, session: Long = 1) = AudioFrame(session, index * 20_000_000L, samples)
    private fun rms(frame: AudioFrame): Double = sqrt((0 until frame.sampleCount).sumOf { frame.normalizedAt(it).toDouble().let { x -> x*x } } / frame.sampleCount)
    private fun motorProfile(): MotorNoiseProfile {
        val spectrum = FrameSpectrum()
        val power = DoubleArray(257)
        repeat(40) { phase ->
            val measured = spectrum.power(frame(0, tone(200, 12000, phase)))
            for (bin in power.indices) power[bin] = maxOf(power[bin], measured[bin])
        }
        return MotorNoiseProfile(power, power)
    }

    @Test fun spectrumEnergyMatchesKnownToneAndZeroProfilePreservesSamples() {
        val samples = tone(1000, 8000)
        val source = frame(3, samples)
        val spectrum = FrameSpectrum()
        assertEquals(0.0298023224, spectrum.power(source).sum(), 0.00001)
        val restored = spectrum.subtract(source, DoubleArray(257))
        assertEquals(source.timestampNs, restored.timestampNs)
        assertEquals(source.sessionId, restored.sessionId)
        for (i in samples.indices) assertTrue(abs(restored.sampleAt(i).toInt() - samples[i].toInt()) <= 1)
    }

    @Test fun measuredMotorBoundsRejectMotorDespitePhaseChanges() {
        val timeline = MotorOutputTimeline()
        timeline.record(longArrayOf(0, 1000), intArrayOf(0, 100), 0)
        val suppressor = MotorNoiseSuppressor(motorProfile(), timeline)
        repeat(40) { phase ->
            assertTrue("motor survived phase $phase", rms(suppressor.process(frame(phase, tone(200, 12000, phase)))) < 0.005)
        }
    }

    @Test fun simultaneousExternalToneRemainsAndStrongerSoundStaysStronger() {
        fun cleaned(amplitude: Int): Double {
            val timeline = MotorOutputTimeline()
            timeline.record(longArrayOf(0, 600), intArrayOf(0, 100), 0)
            val motor = tone(200, 12000)
            val sound = tone(1000, amplitude)
            val mixed = ShortArray(320) { (motor[it].toInt() + sound[it].toInt()).toShort() }
            return rms(MotorNoiseSuppressor(motorProfile(), timeline).process(frame(1, mixed)))
        }
        val weak = cleaned(4000)
        val strong = cleaned(8000)
        assertTrue(weak > 0.06)
        assertTrue(strong > weak * 1.7)
        assertTrue(strong < 0.19)
    }

    @Test fun noOutputAndMissingProfileNeverSuppressTheEnvironment() {
        val source = frame(0, tone(200, 12000))
        assertSame(source, MotorNoiseSuppressor(motorProfile(), MotorOutputTimeline()).process(source))
        val timeline = MotorOutputTimeline().apply { record(longArrayOf(600), intArrayOf(100), 0) }
        assertSame(source, MotorNoiseSuppressor(null, timeline).process(source))
    }

    @Test fun delayedAudioQueriesHistoricalOutputAndCancelClipsFuture() {
        val timeline = MotorOutputTimeline(tailNs = 40_000_000, maxWindows = 4)
        timeline.record(longArrayOf(0, 60), intArrayOf(0, 100), 1_000_000_000)
        timeline.record(longArrayOf(0, 60), intArrayOf(0, 200), 1_300_000_000)
        assertEquals(100, timeline.amplitudeAt(1_050_000_000, 20_000_000))
        timeline.cancel(1_310_000_000)
        assertEquals(0, timeline.amplitudeAt(1_360_000_000, 20_000_000))
        assertEquals(200, timeline.amplitudeAt(1_300_000_000, 10_000_000))
    }

    @Test fun outputHistoryIsBoundedAndDoesNotGrowWithRuntime() {
        val timeline = MotorOutputTimeline(tailNs = 0, maxWindows = 4)
        repeat(1000) { timeline.record(longArrayOf(20), intArrayOf(50), it * 20_000_000L) }
        assertEquals(0, timeline.amplitudeAt(0, 20_000_000))
        assertEquals(50, timeline.amplitudeAt(999 * 20_000_000L, 20_000_000))
    }

    @Test fun oneExternalTransientCannotFeedItselfForSeconds() {
        fun run(compensate: Boolean): List<Int> {
            val profile = motorProfile()
            val timeline = MotorOutputTimeline(tailNs = 40_000_000)
            val suppressor = MotorNoiseSuppressor(if (compensate) profile else null, timeline)
            val dsp = DspProcessor()
            val mapper = EnvelopeHapticMapper()
            val active = mutableListOf<Int>()
            var motorUntil = -1
            repeat(350) { index ->
                val external = if (index == 10) ShortArray(320) { if (it % 2 == 0) 18000 else -18000 } else ShortArray(320)
                val motor = if (index < motorUntil) tone(200, 12000, index * 320) else ShortArray(320)
                val mixed = ShortArray(320) { (external[it].toInt() + motor[it].toInt()).toShort() }
                val output = mapper.map(dsp.extract(suppressor.process(frame(index, mixed))))
                if (!output.isSilent) {
                    active += index
                    motorUntil = index + 3
                    timeline.record(longArrayOf(20), intArrayOf(150), index * 20_000_000L)
                }
            }
            return active
        }
        assertTrue("fixture must reproduce self feedback", run(false).size > 100)
        val corrected = run(true)
        assertEquals(listOf(10), corrected)
    }

    @Test fun longerExternalSoundKeepsOutputUntilItsActualEnd() {
        fun activeFrames(length: Int): List<Int> {
            val timeline = MotorOutputTimeline()
            timeline.record(longArrayOf(1000), intArrayOf(100), 0)
            val suppressor = MotorNoiseSuppressor(motorProfile(), timeline)
            val dsp = DspProcessor()
            val mapper = EnvelopeHapticMapper()
            return (0..49).filter { index ->
                val motor = tone(200, 12000, index * 320)
                val external = if (index < length) tone(1000, 8000) else ShortArray(320)
                val mixed = ShortArray(320) { (motor[it].toInt() + external[it].toInt()).toShort() }
                !mapper.map(dsp.extract(suppressor.process(frame(index, mixed)))).isSilent
            }
        }
        val short = activeFrames(10)
        val long = activeFrames(30)
        assertEquals((0..9).toList(), short)
        assertEquals((0..29).toList(), long)
    }
}
