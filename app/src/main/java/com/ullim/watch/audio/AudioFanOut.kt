package com.ullim.watch.audio

import com.ullim.watch.core.AudioFrame
import java.util.concurrent.atomic.AtomicReference

class AudioFanOut(private val onDspFrame: (AudioFrame) -> Unit) {
    private var preprocess: ((AudioFrame) -> AudioFrame)? = null

    /** One correction per 20 ms frame, shared by DSP and the AI ring. */
    constructor(preprocess: (AudioFrame) -> AudioFrame, onDspFrame: (AudioFrame) -> Unit) : this(onDspFrame) {
        this.preprocess = preprocess
    }
    private val ring = ShortArray(AudioFrame.AI_SAMPLES)
    private val partial = ShortArray(AudioFrame.DSP_SAMPLES)
    private val latestAi = AtomicReference<AudioFrame?>()
    private var sessionId = 0L
    private var highestSessionId = 0L
    private var expectedNs: Long? = null
    private var partialStartNs = 0L
    private var partialSize = 0
    private var ringSize = 0
    private var writeIndex = 0
    private var sinceAiSnapshot = 0

    @Synchronized fun start(sessionId: Long) {
        require(sessionId > highestSessionId)
        highestSessionId = sessionId
        this.sessionId = sessionId
        clearBuffers()
    }

    @Synchronized fun stop() {
        sessionId = 0
        clearBuffers()
    }

    @Synchronized fun accept(frame: AudioFrame): Boolean {
        if (sessionId == 0L || frame.sessionId != sessionId) return false
        val expected = expectedNs
        if (expected != null && frame.timestampNs < expected) return false
        if (expected != null && frame.timestampNs != expected) clearBuffers()
        for (index in 0 until frame.sampleCount) {
            val sample = frame.sampleAt(index)
            val timestampNs = frame.timestampNs + index * 62_500L
            if (partialSize == 0) partialStartNs = timestampNs
            partial[partialSize++] = sample
            if (preprocess == null) appendAiSample(sample, timestampNs)
            if (partialSize == partial.size) {
                val raw = AudioFrame(sessionId, partialStartNs, partial)
                val processed = preprocess?.invoke(raw) ?: raw
                require(processed.sessionId == raw.sessionId && processed.timestampNs == raw.timestampNs &&
                    processed.sampleCount == raw.sampleCount)
                onDspFrame(processed)
                if (preprocess != null) for (sampleIndex in 0 until processed.sampleCount) {
                    appendAiSample(processed.sampleAt(sampleIndex), processed.timestampNs + sampleIndex * 62_500L)
                }
                partialSize = 0
            }
        }
        expectedNs = frame.timestampNs + frame.durationNs
        return true
    }

    fun takeLatestAi(): AudioFrame? = latestAi.getAndSet(null)

    private fun appendAiSample(sample: Short, timestampNs: Long) {
        ring[writeIndex] = sample
        writeIndex = (writeIndex + 1) % ring.size
        ringSize = minOf(ringSize + 1, ring.size)
        sinceAiSnapshot++
        if (ringSize == ring.size && sinceAiSnapshot >= 8000) {
            val snapshot = ShortArray(ring.size) { ring[(writeIndex + it) % ring.size] }
            latestAi.set(AudioFrame(sessionId, timestampNs - (ring.size - 1) * 62_500L, snapshot))
            sinceAiSnapshot = 0
        }
    }

    private fun clearBuffers() {
        ring.fill(0)
        partial.fill(0)
        partialSize = 0
        ringSize = 0
        writeIndex = 0
        sinceAiSnapshot = 0
        expectedNs = null
        latestAi.set(null)
    }
}
