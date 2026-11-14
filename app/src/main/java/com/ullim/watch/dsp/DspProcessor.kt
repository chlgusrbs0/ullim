package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatureExtractor
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.settings.TuningSettings
import com.ullim.watch.settings.TuningParameter.*

class DspProcessor(loudnessKnee: Float = NoiseGate.DEFAULT_LOUDNESS_KNEE) : AudioFeatureExtractor {
    private val envelope = EnvelopeExtractor()
    private val gate = NoiseGate(loudnessKnee)
    private val onset = OnsetDetector()
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null
    @Volatile var sensitivity: Float = 1f
        set(value) {
            require(value.isFinite() && value in 0.5f..2f)
            field = value
        }

    override fun extract(frame: AudioFrame): AudioFeatures = extract(frame, learnNoise = true)

    fun extract(frame: AudioFrame, learnNoise: Boolean, tuning: TuningSettings? = null): AudioFeatures {
        val previousNs = lastTimestampNs
        if (frame.sampleCount != AudioFrame.DSP_SAMPLES || frame.sessionId < sessionId ||
            (frame.sessionId == sessionId && previousNs != null && frame.timestampNs <= previousNs)
        ) return AudioFeatures(frame.sessionId, frame.timestampNs, 0f, 0f, 0f)
        if (frame.sessionId != sessionId || (previousNs != null && frame.timestampNs != previousNs + 20_000_000L)) reset()
        sessionId = frame.sessionId
        lastTimestampNs = frame.timestampNs
        tuning?.let { envelope.configure(it[ATTACK_MS], it[RELEASE_MS]) }
        return onset.process(gate.process(envelope.extract(frame), sensitivity, learnNoise, tuning))
    }

    override fun reset() {
        envelope.reset()
        gate.reset()
        onset.reset()
        sessionId = 0
        lastTimestampNs = null
    }
}
