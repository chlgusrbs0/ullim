package com.ullim.watch.probe

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.haptics.EnvelopeHapticMapper
import kotlin.math.PI
import kotlin.math.sin

/** Synthetic PCM to command values only: no microphone or physical motor output. */
internal object HapticMappingVerification {
    fun run(report: (String) -> Unit) {
        fun active() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
        fun tone(amplitude: Int) = ShortArray(320) {
            (amplitude * sin(2 * PI * 1000 * it / 16000)).toInt().toShort()
        }
        fun strength(amplitude: Int, sensitivity: Float): Float {
            val dsp = DspProcessor().apply { this.sensitivity = sensitivity }
            val mapper = EnvelopeHapticMapper()
            var output = 0f
            repeat(25) { index ->
                active()
                output = mapper.map(dsp.extract(AudioFrame(1, index * 20_000_000L, tone(amplitude)))).commandedIntensity
            }
            return output
        }
        val strengths = listOf(2000, 8000, 24000).map { strength(it, 1f) }
        val sensitivities = listOf(.5f, 1f, 2f).map { strength(2000, it) }
        check(strengths.zipWithNext().all { (a, b) -> a < b }) { "PCM_STRENGTH_ORDER" }
        check(sensitivities.zipWithNext().all { (a, b) -> a < b }) { "SENSITIVITY_ORDER" }

        val dsp = DspProcessor()
        val mapper = EnvelopeHapticMapper()
        val leading = mutableListOf<Int>()
        var quiet = true
        repeat(85) { index ->
            active()
            val samples = ShortArray(320) { if (index in listOf(5, 20, 45)) 28000 else 0 }
            val output = mapper.map(dsp.extract(AudioFrame(1, index * 20_000_000L, samples)))
            if (quiet && !output.isSilent) leading.add(index)
            quiet = output.isSilent
        }
        check(leading == listOf(5, 20, 45) && quiet) { "KNOCK_GAPS" }
        dsp.reset(); mapper.reset()
        var minimum = 1f
        repeat(250) { index ->
            active()
            val output = mapper.map(dsp.extract(AudioFrame(2, index * 20_000_000L, tone(16000))))
            check(!output.isSilent && output.timestampNs == index * 20_000_000L && output.durationMs == 20L) { "DURATION" }
            minimum = minOf(minimum, output.commandedIntensity)
        }
        check(mapper.map(dsp.extract(AudioFrame(2, 5_000_000_000L, ShortArray(320)))).isSilent) { "QUIET_TAIL" }
        dsp.reset(); mapper.reset()
        val modulated = (0..99).map { index ->
            active()
            val amplitude = 12000 + (8000 * sin(2 * PI * 3 * index * .02)).toInt()
            mapper.map(dsp.extract(AudioFrame(3, index * 20_000_000L, tone(amplitude)))).commandedIntensity
        }
        check(modulated.distinct().size > 80 && modulated.max() - modulated.min() > .1f) { "MODULATION" }
        report("mapping synthetic=true motor=false ai=false strengths=${strengths.joinToString(",")} sensitivities=${sensitivities.joinToString(",")} knockFrames=${leading.joinToString(",")} intervalsMs=300,500 simulatedContinuousMs=5000 nonzeroFrames=250 minimum=$minimum quietTail=0 speechDistinct=${modulated.distinct().size} speechRange=${modulated.max() - modulated.min()}")
    }
}
