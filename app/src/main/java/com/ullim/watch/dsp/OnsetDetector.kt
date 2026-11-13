package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatures

class OnsetDetector {
    private var previous = 0f
    private var lastOnsetNs: Long? = null
    private var sessionId = 0L

    fun process(features: AudioFeatures): AudioFeatures {
        if (sessionId != features.sessionId) {
            reset()
            sessionId = features.sessionId
        }
        val delta = (features.envelope - previous).coerceAtLeast(0f)
        val ready = lastOnsetNs?.let { features.timestampNs - it >= 100_000_000L } ?: true
        val onset = if (ready && features.level > 0f && delta > 0.015f + features.noise * 0.5f) {
            lastOnsetNs = features.timestampNs
            (delta / (previous + features.noise + 0.02f)).coerceIn(0f, 1f)
        } else 0f
        previous = features.envelope
        return features.copy(onset = onset)
    }

    fun reset() {
        previous = 0f
        lastOnsetNs = null
        sessionId = 0
    }
}
