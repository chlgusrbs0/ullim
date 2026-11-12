package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatureExtractor
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class EnvelopeExtractor(attackMs: Float = 20f, releaseMs: Float = 120f) : AudioFeatureExtractor {
    private val attack: Float
    private val release: Float
    private var envelope = 0f
    private var sessionId = 0L

    init {
        require(attackMs.isFinite() && attackMs > 0f)
        require(releaseMs.isFinite() && releaseMs > 0f)
        attack = 1f - exp(-20f / attackMs)
        release = 1f - exp(-20f / releaseMs)
    }

    override fun extract(frame: AudioFrame): AudioFeatures {
        require(frame.sampleCount == AudioFrame.DSP_SAMPLES)
        if (sessionId != frame.sessionId) {
            reset()
            sessionId = frame.sessionId
        }
        var sum = 0.0
        var peak = 0f
        for (index in 0 until frame.sampleCount) {
            val sample = frame.normalizedAt(index)
            sum += sample.toDouble() * sample
            peak = maxOf(peak, abs(sample))
        }
        val rms = sqrt(sum / frame.sampleCount).toFloat().coerceIn(0f, 1f)
        envelope += (rms - envelope) * if (rms > envelope) attack else release
        if (envelope < 0.00001f) envelope = 0f
        return AudioFeatures(frame.sessionId, frame.timestampNs, rms, peak, envelope.coerceIn(0f, 1f))
    }

    override fun reset() {
        envelope = 0f
        sessionId = 0
    }
}
