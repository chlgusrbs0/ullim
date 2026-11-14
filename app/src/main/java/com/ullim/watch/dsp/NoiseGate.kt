package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatures
import kotlin.math.sqrt

class NoiseGate(private val loudnessKnee: Float = DEFAULT_LOUDNESS_KNEE) {
    private var noise = 0.003f
    private var open = false
    private var sessionId = 0L

    init { require(loudnessKnee.isFinite() && loudnessKnee > 0f && loudnessKnee <= 1f) }

    fun process(features: AudioFeatures, sensitivity: Float = 1f, learnNoise: Boolean = true): AudioFeatures {
        require(sensitivity.isFinite() && sensitivity in 0.5f..2f)
        if (sessionId != features.sessionId) {
            reset()
            sessionId = features.sessionId
        }
        val threshold = if (open) noise * 1.25f + 0.004f / sensitivity else noise * 1.7f + 0.008f / sensitivity
        open = features.envelope > threshold && features.rms > maxOf(.002f, noise * 1.1f)
        // An audible sustained event must not become its own noise floor. Motor
        // output/tails are also excluded, even if subtraction closes the gate.
        if (!open && learnNoise) {
            // Use current input, not the releasing envelope of an ended event.
            noise += (features.rms - noise) * if (features.rms < noise) 0.05f else 0.005f
            noise = noise.coerceIn(0f, 1f)
        }
        val x = if (open) sqrt((features.envelope - noise * 1.1f).coerceAtLeast(0f) * sensitivity * 2f).coerceIn(0f, 1f) else 0f
        // PCM full scale is not an ordinary acoustic loudness reference. Use
        // more of the tactile range without flattening every loud input to 1.
        // This fixed, category-independent curve is not automatic gain control;
        // the gate/noise history, PCM and microphone gain remain unchanged.
        val level = if (open) x / (x + loudnessKnee * (1f - x)) else 0f
        return features.copy(noise = noise, level = level)
    }

    fun reset() {
        noise = 0.003f
        open = false
        sessionId = 0
    }

    companion object { const val DEFAULT_LOUDNESS_KNEE = .3f }
}
