package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.FrameSpectrum

/** Explicit quiet calibration; only numeric spectral bounds survive. */
class MotorCalibration(private val sessionId: Long, private val startNs: Long, private val pulseFallback: Boolean = false) {
    private val spectrum = FrameSpectrum()
    private val quiet = DoubleArray(FrameSpectrum.BINS)
    private var quietCount = 0
    private val starts = LongArray(2) { -1 }
    private val measurements = Array(2) { mutableListOf<DoubleArray>() }

    @Synchronized fun recordPhase(index: Int, actualStartNs: Long) {
        require(index in 0..1)
        starts[index] = actualStartNs
    }

    @Synchronized fun accept(frame: AudioFrame) {
        if (frame.sessionId != sessionId || frame.sampleCount != AudioFrame.DSP_SAMPLES) return
        val relative = frame.timestampNs - startNs
        if (relative in 50_000_000 until 400_000_000 && quietCount < 24) {
            val power = spectrum.power(frame)
            for (bin in quiet.indices) quiet[bin] += power[bin]
            quietCount++
            return
        }
        for (index in starts.indices) {
            val phaseStart = starts[index]
            if (phaseStart < 0) continue
            val since = frame.timestampNs - phaseStart
            if (since in 150_000_000 until 500_000_000 && measurements[index].size < 24) {
                measurements[index].add(spectrum.power(frame))
            }
        }
    }

    @Synchronized fun finish(): MotorNoiseProfile? {
        if (quietCount < 8 || measurements.any { it.size < 8 }) return null
        val baseline = DoubleArray(quiet.size) { quiet[it] / quietCount }
        if (baseline.sum() > .02 * .02) return null
        val bounds = Array(2) { DoubleArray(quiet.size) }
        for (phase in measurements.indices) {
            val energies = measurements[phase].map { it.sum() }.sorted()
            val median = energies[energies.size / 2]
            if (energies.last() > maxOf(1e-5, median * 2)) return null
            for (power in measurements[phase]) for (bin in power.indices) {
                bounds[phase][bin] = maxOf(bounds[phase][bin], (power[bin] - baseline[bin]).coerceAtLeast(0.0))
            }
        }
        // An ignored/muted output must not produce a profile that disables protection.
        if (bounds[1].sum() < maxOf(1e-6, baseline.sum() * 4)) return null
        return MotorNoiseProfile(bounds[0], bounds[1], pulseFallback)
    }
}
