package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatures
import kotlin.math.sqrt

class NoiseGate {
    private var noise = 0.003f
    private var open = false
    private var sessionId = 0L

    fun process(features: AudioFeatures, sensitivity: Float = 1f): AudioFeatures {
        require(sensitivity.isFinite() && sensitivity in 0.5f..2f)
        if (sessionId != features.sessionId) {
            reset()
            sessionId = features.sessionId
        }
        noise += (features.envelope - noise) * if (features.envelope < noise) 0.05f else 0.005f
        noise = noise.coerceIn(0f, 1f)
        val threshold = if (open) noise * 1.25f + 0.004f / sensitivity else noise * 1.7f + 0.008f / sensitivity
        open = features.envelope > threshold
        val level = if (open) sqrt((features.envelope - noise * 1.1f).coerceAtLeast(0f) * sensitivity * 2f).coerceIn(0f, 1f) else 0f
        return features.copy(noise = noise, level = level)
    }

    fun reset() {
        noise = 0.003f
        open = false
        sessionId = 0
    }
}
