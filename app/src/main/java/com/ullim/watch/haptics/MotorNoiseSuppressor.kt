package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.FrameSpectrum

class MotorNoiseSuppressor(private val profile: MotorNoiseProfile?, private val timeline: MotorOutputTimeline) {
    private val spectrum = FrameSpectrum()
    private val noisePower = DoubleArray(FrameSpectrum.BINS)
    private var sessionId = 0L
    var lastRawRms = 0f
        private set
    var lastFiltered = false
        private set

    fun process(frame: AudioFrame): AudioFrame {
        lastFiltered = false
        if (sessionId != 0L && frame.sessionId != sessionId) timeline.clear()
        sessionId = frame.sessionId
        if (profile == null || !profile.hasCoupling || frame.sampleCount != AudioFrame.DSP_SAMPLES) return frame
        val amplitude = timeline.amplitudeAt(frame.timestampNs, frame.durationNs)
        if (amplitude == 0) return frame
        profile.fillNoisePower(amplitude, noisePower)
        val cleaned = spectrum.subtract(frame, noisePower)
        lastRawRms = spectrum.lastRawRms
        lastFiltered = true
        return cleaned
    }
}
