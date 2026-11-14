package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatures
import kotlin.math.sqrt
import com.ullim.watch.settings.TuningSettings
import com.ullim.watch.settings.TuningParameter.*

class NoiseGate(private val loudnessKnee: Float = DEFAULT_LOUDNESS_KNEE) {
    private var noise = 0.003f
    private var open = false
    private var sessionId = 0L

    init { require(loudnessKnee.isFinite() && loudnessKnee > 0f && loudnessKnee <= 2f) }

    fun process(features: AudioFeatures, sensitivity: Float = 1f, learnNoise: Boolean = true,
        tuning: TuningSettings? = null): AudioFeatures {
        val t = tuning ?: TuningSettings.DEFAULT
        require(sensitivity.isFinite() && sensitivity in 0.5f..2f)
        if (sessionId != features.sessionId) {
            reset()
            sessionId = features.sessionId
        }
        val threshold = if (open) noise * t[CLOSE_RATIO] + t[CLOSE_OFFSET] / sensitivity
            else noise * t[OPEN_RATIO] + t[OPEN_OFFSET] / sensitivity
        open = features.envelope > threshold && features.rms > maxOf(t[MIN_RMS], noise * t[NOISE_MARGIN])
        // An audible sustained event must not become its own noise floor. Motor
        // output/tails are also excluded, even if subtraction closes the gate.
        if (!open && learnNoise) {
            // Use current input, not the releasing envelope of an ended event.
            noise += (features.rms - noise) * if (features.rms < noise) t[NOISE_FALL] else t[NOISE_RISE]
            noise = noise.coerceIn(0f, 1f)
        }
        val x = if (open) sqrt((features.envelope - noise * t[NOISE_MARGIN]).coerceAtLeast(0f) * sensitivity / t[REFERENCE_RMS]).coerceIn(0f, 1f) else 0f
        // PCM full scale is not an ordinary acoustic loudness reference. Use
        // more of the tactile range without flattening every loud input to 1.
        // This fixed, category-independent curve is not automatic gain control;
        // the gate/noise history, PCM and microphone gain remain unchanged.
        val knee = tuning?.get(LOUDNESS_KNEE) ?: loudnessKnee
        val level = if (open) x / (x + knee * (1f - x)) else 0f
        return features.copy(noise = noise, level = level)
    }

    fun reset() {
        noise = 0.003f
        open = false
        sessionId = 0
    }

    companion object { const val DEFAULT_LOUDNESS_KNEE = .3f }
}
