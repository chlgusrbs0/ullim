package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatureExtractor
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.settings.TuningSettings

/** Reads an already validated in-memory choice, never disk, once per DSP frame. */
class LiveDsp(
    private val preprocess: (AudioFrame) -> AudioFrame = { it },
    private val canLearnNoise: () -> Boolean = { true },
    private val readTuning: () -> TuningSettings = { TuningSettings.DEFAULT },
    private val readSensitivity: () -> Float,
) : AudioFeatureExtractor {
    private val processor = DspProcessor()
    override fun extract(frame: AudioFrame): AudioFeatures {
        processor.sensitivity = readSensitivity()
        val processed = preprocess(frame)
        return processor.extract(processed, learnNoise = canLearnNoise(), tuning = readTuning())
    }
    override fun reset() = processor.reset()
}
