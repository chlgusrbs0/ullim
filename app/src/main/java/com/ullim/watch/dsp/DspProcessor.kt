package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatureExtractor
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame

class DspProcessor : AudioFeatureExtractor {
    private val envelope = EnvelopeExtractor()
    private val gate = NoiseGate()
    private val onset = OnsetDetector()
    @Volatile var sensitivity: Float = 1f
        set(value) {
            require(value.isFinite() && value in 0.5f..2f)
            field = value
        }

    override fun extract(frame: AudioFrame): AudioFeatures = onset.process(gate.process(envelope.extract(frame), sensitivity))

    override fun reset() {
        envelope.reset()
        gate.reset()
        onset.reset()
    }
}
