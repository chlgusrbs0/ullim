package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed work buffers; power profiles retain neither PCM nor phase. Single DSP owner. */
class FrameSpectrum {
    private val real = DoubleArray(SIZE)
    private val imaginary = DoubleArray(SIZE)
    private val cosine = DoubleArray(SIZE / 2) { cos(2 * PI * it / SIZE) }
    private val sine = DoubleArray(SIZE / 2) { sin(2 * PI * it / SIZE) }
    var lastRawRms = 0f
        private set

    fun power(frame: AudioFrame): DoubleArray = try {
        load(frame)
        transform(false)
        DoubleArray(BINS) { binPower(it) }
    } finally { clear() }

    fun subtract(frame: AudioFrame, noisePower: DoubleArray, margin: Double = 1.25): AudioFrame = try {
        require(noisePower.size == BINS && noisePower.all { it.isFinite() && it >= 0 })
        require(margin.isFinite() && margin >= 1)
        load(frame)
        transform(false)
        var totalPower = 0.0
        for (bin in 0 until BINS) {
            val power = binPower(bin)
            totalPower += power
            val gain = if (power == 0.0) 0.0 else sqrt((1 - noisePower[bin] * margin / power).coerceIn(0.0, 1.0))
            real[bin] *= gain
            imaginary[bin] *= gain
            if (bin > 0 && bin < SIZE / 2) {
                real[SIZE - bin] *= gain
                imaginary[SIZE - bin] *= gain
            }
        }
        lastRawRms = sqrt(totalPower).toFloat().coerceIn(0f, 1f)
        transform(true)
        val samples = ShortArray(AudioFrame.DSP_SAMPLES) {
            (real[it] * 32768).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        AudioFrame(frame.sessionId, frame.timestampNs, samples)
    } finally { clear() }

    private fun load(frame: AudioFrame) {
        require(frame.sampleCount == AudioFrame.DSP_SAMPLES)
        for (i in 0 until SIZE) {
            real[i] = if (i < frame.sampleCount) frame.normalizedAt(i).toDouble() else 0.0
            imaginary[i] = 0.0
        }
    }

    private fun binPower(bin: Int): Double {
        val scale = if (bin == 0 || bin == SIZE / 2) 1.0 else 2.0
        return (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * scale / (SIZE * AudioFrame.DSP_SAMPLES)
    }

    private fun transform(inverse: Boolean) {
        for (i in 0 until SIZE) {
            val reversed = Integer.reverse(i) ushr (32 - 9)
            if (reversed > i) {
                val r = real[i]; real[i] = real[reversed]; real[reversed] = r
                val im = imaginary[i]; imaginary[i] = imaginary[reversed]; imaginary[reversed] = im
            }
        }
        var length = 2
        while (length <= SIZE) {
            val half = length / 2
            val stride = SIZE / length
            var base = 0
            while (base < SIZE) {
                for (offset in 0 until half) {
                    val twiddle = offset * stride
                    val wr = cosine[twiddle]
                    val wi = if (inverse) sine[twiddle] else -sine[twiddle]
                    val a = base + offset
                    val b = a + half
                    val vr = wr * real[b] - wi * imaginary[b]
                    val vi = wr * imaginary[b] + wi * real[b]
                    val ur = real[a]
                    val ui = imaginary[a]
                    real[a] = ur + vr; imaginary[a] = ui + vi
                    real[b] = ur - vr; imaginary[b] = ui - vi
                }
                base += length
            }
            length *= 2
        }
        if (inverse) for (i in 0 until SIZE) { real[i] /= SIZE; imaginary[i] /= SIZE }
    }

    private fun clear() { real.fill(0.0); imaginary.fill(0.0) }
    companion object { const val SIZE = 512; const val BINS = SIZE / 2 + 1 }
}
