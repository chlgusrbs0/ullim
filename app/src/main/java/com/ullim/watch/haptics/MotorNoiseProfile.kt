package com.ullim.watch.haptics

import com.ullim.watch.dsp.FrameSpectrum

/** Numeric motor-only spectral bounds from an explicit quiet calibration. */
class MotorNoiseProfile(lowPower: DoubleArray, highPower: DoubleArray, val pulseFallback: Boolean = false) {
    private val low = lowPower.copyOf()
    private val high = highPower.copyOf()
    val hasCoupling: Boolean
    val flatCoupling: Boolean

    init {
        require(low.size == FrameSpectrum.BINS && high.size == FrameSpectrum.BINS)
        require(low.all { it.isFinite() && it in 0.0..1.0 } && high.all { it.isFinite() && it in 0.0..1.0 })
        val lowSum = low.sum()
        val highSum = high.sum()
        hasCoupling = maxOf(lowSum, highSum) > 1e-8
        flatCoupling = highSum > 1e-8 && lowSum / highSum > .5
    }

    fun fillNoisePower(amplitude: Int, destination: DoubleArray) {
        require(amplitude in 1..255 && destination.size == low.size)
        val weight = ((amplitude.toDouble() * amplitude - 1) / (255.0 * 255 - 1)).coerceIn(0.0, 1.0)
        for (bin in destination.indices) destination[bin] = if (pulseFallback || flatCoupling) maxOf(low[bin], high[bin])
            else (low[bin] + (high[bin] - low[bin]) * weight).coerceAtLeast(0.0)
    }

    fun lowPowerCopy(): DoubleArray = low.copyOf()
    fun highPowerCopy(): DoubleArray = high.copyOf()
}
