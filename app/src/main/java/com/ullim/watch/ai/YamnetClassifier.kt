package com.ullim.watch.ai

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.Classification
import com.ullim.watch.core.SoundClassifier

interface YamnetEngine : AutoCloseable {
    /** Worker-thread only. The returned scores may be reused on the next call. */
    fun infer(samples: FloatArray): FloatArray
}

class YamnetClassifier(labels: List<String>, private val engine: YamnetEngine) : SoundClassifier {
    private val labels = labels.toList()
    private val input = FloatArray(AudioFrame.AI_SAMPLES)
    private var closed = false

    init {
        if (this.labels.size != 521 || this.labels.any { it.isBlank() }) {
            engine.close()
            throw IllegalArgumentException("Invalid YAMNet label map")
        }
    }

    @Synchronized override fun classify(frame: AudioFrame): Classification {
        check(!closed) { "Classifier closed" }
        require(frame.sampleCount == AudioFrame.AI_SAMPLES) { "Invalid YAMNet window" }
        val endNs = Math.addExact(frame.timestampNs, frame.durationNs)
        val expiresNs = Math.addExact(endNs, 1_500_000_000L)
        for (index in input.indices) input[index] = frame.normalizedAt(index)
        val scores = try { engine.infer(input) } finally { input.fill(0f) }
        require(scores.size == 521 && scores.all { it.isFinite() && it in 0f..1f }) { "Invalid YAMNet scores" }
        val top = scores.indices.sortedByDescending { scores[it] }.take(5)
            .map { ClassScore(labels[it], scores[it]) }
        return Classification(frame.sessionId, endNs, expiresNs, top)
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        input.fill(0f)
        engine.close()
    }
}
