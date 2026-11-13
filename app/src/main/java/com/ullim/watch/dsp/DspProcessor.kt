package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatureExtractor
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame

class DspProcessor : AudioFeatureExtractor {
    private val envelope = EnvelopeExtractor()
    private val gate = NoiseGate()
    private val onset = OnsetDetector()
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null
    @Volatile var sensitivity: Float = 1f
        set(value) {
            require(value.isFinite() && value in 0.5f..2f)
            field = value
        }

    override fun extract(frame: AudioFrame): AudioFeatures {
        val previousNs = lastTimestampNs
        if (frame.sampleCount != AudioFrame.DSP_SAMPLES || frame.sessionId < sessionId ||
            (frame.sessionId == sessionId && previousNs != null && frame.timestampNs <= previousNs)
        ) return AudioFeatures(frame.sessionId, frame.timestampNs, 0f, 0f, 0f)
        if (frame.sessionId != sessionId || (previousNs != null && frame.timestampNs != previousNs + 20_000_000L)) reset()
        sessionId = frame.sessionId
        lastTimestampNs = frame.timestampNs
        return onset.process(gate.process(envelope.extract(frame), sensitivity))
    }

    override fun reset() {
        envelope.reset()
        gate.reset()
        onset.reset()
        sessionId = 0
        lastTimestampNs = null
    }
}
