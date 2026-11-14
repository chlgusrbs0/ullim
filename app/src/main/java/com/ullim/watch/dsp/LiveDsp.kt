package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatureExtractor
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame

/** Reads an already validated in-memory choice, never disk, once per DSP frame. */
class LiveDsp(
    private val preprocess: (AudioFrame) -> AudioFrame = { it },
    private val readSensitivity: () -> Float,
) : AudioFeatureExtractor {
    private val processor = DspProcessor()
    override fun extract(frame: AudioFrame): AudioFeatures {
        processor.sensitivity = readSensitivity()
        return processor.extract(preprocess(frame))
    }
    override fun reset() = processor.reset()
}
