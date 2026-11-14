package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.FrameSpectrum

/** Explicit quiet calibration; only numeric spectral bounds survive. */
class MotorCalibration(private val sessionId: Long, private val startNs: Long, private val pulseFallback: Boolean = false) {
    private val spectrum = FrameSpectrum()
    private val quiet = DoubleArray(FrameSpectrum.BINS)
    private var quietCount = 0
    private val starts = LongArray(2) { -1 }
    private data class Measurement(val timestampNs: Long, val power: DoubleArray)
    private val measurements = Array(2) { mutableListOf<Measurement>() }
    private var outcome = "NOT_FINISHED"

    /** Bounded scalar diagnostics only, read on the UI thread after assessment. */
    @Synchronized fun diagnostics(): String {
        val energies = measurements.map { phase -> phase.map { it.power.sum() }.sorted() }
        val cycles = measurements.indices.map { stabilityEnergies(it).sorted() }
        return "assessment=$outcome quietFrames=$quietCount phaseFrames=${measurements.map { it.size }} " +
            "quietPower=${if (quietCount == 0) 0.0 else quiet.sum() / quietCount} " +
            "medianPower=${energies.map { it.getOrNull(it.size / 2) ?: 0.0 }} " +
            "maxPower=${energies.map { it.lastOrNull() ?: 0.0 }} " +
            "stabilitySamples=${cycles.map { it.size }} " +
            "stabilityMedianPower=${cycles.map { it.getOrNull(it.size / 2) ?: 0.0 }} " +
            "stabilityMaxPower=${cycles.map { it.lastOrNull() ?: 0.0 }}"
    }

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
                measurements[index].add(Measurement(frame.timestampNs, spectrum.power(frame)))
            }
        }
    }

    @Synchronized fun finish(): MotorNoiseProfile? {
        if (quietCount < 8 || measurements.any { it.size < 8 }) return reject("MISSING_FRAMES")
        val baseline = DoubleArray(quiet.size) { quiet[it] / quietCount }
        if (baseline.sum() > .02 * .02) return reject("NOISY_BASELINE")
        val bounds = Array(2) { DoubleArray(quiet.size) }
        for (phase in measurements.indices) {
            val energies = stabilityEnergies(phase).sorted()
            if (energies.size < if (pulseFallback) 3 else 8) return reject("MISSING_STABILITY_SAMPLES_$phase")
            val median = energies[energies.size / 2]
            if (energies.last() > maxOf(1e-5, median * 2)) return reject("UNSTABLE_PHASE_$phase")
            for (measurement in measurements[phase]) for (bin in measurement.power.indices) {
                val power = measurement.power
                bounds[phase][bin] = maxOf(bounds[phase][bin], (power[bin] - baseline[bin]).coerceAtLeast(0.0))
            }
        }
        // An ignored/muted output must not produce a profile that disables protection.
        if (bounds[1].sum() < maxOf(1e-6, baseline.sum() * 4)) return reject("NO_MOTOR_COUPLING")
        outcome = "COMPLETE"
        return MotorNoiseProfile(bounds[0], bounds[1], pulseFallback)
    }

    private fun reject(reason: String): MotorNoiseProfile? { outcome = reason; return null }

    private fun stabilityEnergies(phase: Int): List<Double> {
        if (!pulseFallback) return measurements[phase].map { it.power.sum() }
        // Live PWM is replaced every 60ms. Individual 20ms frames can contain
        // different motor restart/ring-down portions even for a repeatable output.
        // Assess complete request cycles, keeping every frame's spectral maxima
        // for suppression. Missing frames must not be joined into a fake cycle.
        return measurements[phase].groupBy { (it.timestampNs - starts[phase]) / 60_000_000L }
            .values.filter { group -> group.size == 3 && group.zipWithNext().all { (a, b) -> b.timestampNs - a.timestampNs == 20_000_000L } }
            .map { group -> group.sumOf { it.power.sum() } / 3 }
    }
}
