package com.ullim.watch.core

import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

private fun unitValue(value: Float) = require(value.isFinite() && value in 0f..1f)
private fun identity(sessionId: Long, timestampNs: Long) {
    require(sessionId > 0)
    require(timestampNs >= 0)
}

class AudioFrame(val sessionId: Long, val timestampNs: Long, samples: ShortArray) {
    private val pcm = samples.copyOf()
    val sampleCount: Int get() = pcm.size
    val durationNs: Long get() = sampleCount * 1_000_000_000L / SAMPLE_RATE_HZ

    init {
        identity(sessionId, timestampNs)
        require(pcm.isNotEmpty())
    }

    fun sampleAt(index: Int): Short = pcm[index]
    fun normalizedAt(index: Int): Float = pcm[index] / 32768f
    fun copySamples(): ShortArray = pcm.copyOf()

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val DSP_SAMPLES = 320
        const val AI_SAMPLES = 15_600
    }
}

data class AudioFeatures(
    val sessionId: Long,
    val timestampNs: Long,
    val rms: Float,
    val peak: Float,
    val envelope: Float,
    val noise: Float = 0f,
    val onset: Float = 0f,
    val level: Float = envelope,
) {
    init {
        identity(sessionId, timestampNs)
        listOf(rms, peak, envelope, noise, onset, level).forEach(::unitValue)
    }
    val isSilent: Boolean get() = level == 0f
}

data class ClassScore(val label: String, val confidence: Float) {
    init {
        require(label.isNotBlank())
        unitValue(confidence)
    }
}

class Classification(
    val sessionId: Long,
    val timestampNs: Long,
    val validUntilNs: Long,
    scores: List<ClassScore>,
) {
    val scores: List<ClassScore> = Collections.unmodifiableList(scores.toList())

    init {
        identity(sessionId, timestampNs)
        require(validUntilNs > timestampNs)
    }

    fun isFresh(sessionId: Long, nowNs: Long): Boolean =
        this.sessionId == sessionId && nowNs >= timestampNs && nowNs < validUntilNs
}

class LatestClassification {
    private val latest = AtomicReference<Classification?>()

    fun publish(result: Classification) {
        latest.updateAndGet { previous ->
            if (previous == null || result.sessionId > previous.sessionId ||
                (result.sessionId == previous.sessionId && result.timestampNs >= previous.timestampNs)
            ) result else previous
        }
    }

    fun current(sessionId: Long, nowNs: Long): Classification? =
        latest.get()?.takeIf { it.isFresh(sessionId, nowNs) }

    fun clear() { latest.set(null) }
}

data class HapticFrame(
    val sessionId: Long,
    val timestampNs: Long,
    val durationMs: Long,
    val commandedIntensity: Float,
) {
    init {
        identity(sessionId, timestampNs)
        require(durationMs in 1..1000)
        unitValue(commandedIntensity)
    }
    val isSilent: Boolean get() = commandedIntensity == 0f
}

sealed interface ListeningState {
    data object Idle : ListeningState
    data class Starting(val sessionId: Long) : ListeningState
    data class Listening(val sessionId: Long) : ListeningState
    data class Stopping(val sessionId: Long) : ListeningState
    data class Error(val reason: String) : ListeningState
}

enum class AiStatus { DISABLED, STARTING, AVAILABLE, UNAVAILABLE }
data class RuntimeState(val listening: ListeningState = ListeningState.Idle, val ai: AiStatus = AiStatus.DISABLED)

interface AudioFeatureExtractor {
    fun extract(frame: AudioFrame): AudioFeatures
    fun reset()
}

interface SoundClassifier : AutoCloseable {
    fun classify(frame: AudioFrame): Classification
}

interface HapticMapper {
    fun map(features: AudioFeatures, context: Classification? = null): HapticFrame
    fun reset()
}

enum class RenderResult { SENT, SILENT, UNSUPPORTED, STALE, FAILED }

interface HapticRenderer {
    fun render(frame: HapticFrame): RenderResult
    fun cancel()
}
