package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.HapticFrame
import java.util.concurrent.atomic.AtomicReference

/** Numeric debug evidence only. No PCM, labels or physical motor measurements. */
data class HapticTelemetrySnapshot(
    val sessionId: Long,
    val firstSampleNs: Long,
    val lastSampleNs: Long,
    val frames: Int,
    val rejectedFrames: Int,
    val nonzeroCommandFrames: Int,
    val rmsMin: Float, val rmsMax: Float,
    val envelopeMin: Float, val envelopeMax: Float,
    val noiseMin: Float, val noiseMax: Float,
    val levelMin: Float, val levelMax: Float,
    val commandMin: Float, val commandMax: Float,
    val minFrameAgeNs: Long, val maxFrameAgeNs: Long,
    val maxCallbackGapNs: Long,
    val waveformRequests: Int,
    val cancelRequests: Int,
    val requestedDurationMs: Long,
    val amplitudeMin: Int, val amplitudeMax: Int,
    val defaultAmplitudeSegments: Int,
    val maxWaveformGapNs: Long,
    val rawRmsMin: Float = rmsMin, val rawRmsMax: Float = rmsMax,
    val motorFilteredFrames: Int = 0,
    val filteredQuietFrames: Int = 0,
    val aiAdjustedFrames: Int = 0,
    val lowCommandFrames: Int = 0,
    val middleCommandFrames: Int = 0,
    val highCommandFrames: Int = 0,
)

class HapticTelemetry(private val sessionId: Long, private val windowFrames: Int = 25) {
    private class Range {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        fun add(value: Float) { min = minOf(min, value); max = maxOf(max, value) }
        fun clear() { min = Float.POSITIVE_INFINITY; max = Float.NEGATIVE_INFINITY }
    }
    private val rms = Range()
    private val rawRms = Range()
    private val envelope = Range()
    private val noise = Range()
    private val level = Range()
    private val command = Range()
    private val latest = AtomicReference<HapticTelemetrySnapshot?>()
    private var frames = 0
    private var firstSampleNs = 0L
    private var rejected = 0
    private var audible = 0
    private var minAgeNs = Long.MAX_VALUE
    private var maxAgeNs = Long.MIN_VALUE
    private var lastCallbackNs: Long? = null
    private var callbackGapNs = 0L
    private var lastWaveformNs: Long? = null
    private var waveformGapNs = 0L
    private var waveforms = 0
    private var cancels = 0
    private var requestedMs = 0L
    private var amplitudeMin = Int.MAX_VALUE
    private var amplitudeMax = 0
    private var defaultSegments = 0
    private var motorFilteredFrames = 0
    private var filteredQuietFrames = 0
    private var aiAdjustedFrames = 0
    private var lowCommandFrames = 0
    private var middleCommandFrames = 0
    private var highCommandFrames = 0

    init { require(sessionId > 0 && windowFrames in 1..250) }

    @Synchronized fun recordWaveform(timings: LongArray, amplitudes: IntArray, requestedAtNs: Long) {
        waveforms++
        requestedMs += timings.sum()
        for (amplitude in amplitudes) {
            if (amplitude == -1) defaultSegments++
            if (amplitude > 0) {
                amplitudeMin = minOf(amplitudeMin, amplitude)
                amplitudeMax = maxOf(amplitudeMax, amplitude)
            }
        }
        lastWaveformNs?.let { waveformGapNs = maxOf(waveformGapNs, requestedAtNs - it) }
        lastWaveformNs = requestedAtNs
    }

    @Synchronized fun recordCancel() { cancels++ }

    @Synchronized fun offer(features: AudioFeatures, output: HapticFrame, accepted: Boolean, callbackNs: Long,
        rawRms: Float = features.rms, motorFiltered: Boolean = false, aiAdjusted: Boolean = false) {
        if (features.sessionId != sessionId || output.sessionId != sessionId || features.timestampNs != output.timestampNs) return
        if (frames == 0) firstSampleNs = features.timestampNs
        frames++
        if (!accepted) rejected++
        if (!output.isSilent) audible++
        if (motorFiltered) {
            motorFilteredFrames++
            // Raw energy may be the motor itself: this is a diagnostic count,
            // not proof that an external sound was removed.
            if (features.isSilent && rawRms > maxOf(.002f, features.noise * 1.1f)) filteredQuietFrames++
        }
        if (aiAdjusted) aiAdjustedFrames++
        if (!output.isSilent) when {
            output.commandedIntensity < .35f -> lowCommandFrames++
            output.commandedIntensity < .65f -> middleCommandFrames++
            else -> highCommandFrames++
        }
        rms.add(features.rms)
        this.rawRms.add(rawRms)
        envelope.add(features.envelope)
        noise.add(features.noise)
        level.add(features.level)
        command.add(output.commandedIntensity)
        val ageNs = callbackNs - features.timestampNs
        minAgeNs = minOf(minAgeNs, ageNs)
        maxAgeNs = maxOf(maxAgeNs, ageNs)
        lastCallbackNs?.let { callbackGapNs = maxOf(callbackGapNs, callbackNs - it) }
        lastCallbackNs = callbackNs
        if (frames < windowFrames) return
        latest.set(HapticTelemetrySnapshot(
            sessionId, firstSampleNs, features.timestampNs, frames, rejected, audible,
            rms.min, rms.max, envelope.min, envelope.max, noise.min, noise.max,
            level.min, level.max, command.min, command.max, minAgeNs, maxAgeNs, callbackGapNs,
            waveforms, cancels, requestedMs, if (amplitudeMax == 0) 0 else amplitudeMin,
            amplitudeMax, defaultSegments, waveformGapNs, rawRmsMin = this.rawRms.min, rawRmsMax = this.rawRms.max,
            motorFilteredFrames = motorFilteredFrames, filteredQuietFrames = filteredQuietFrames,
            aiAdjustedFrames = aiAdjustedFrames, lowCommandFrames = lowCommandFrames,
            middleCommandFrames = middleCommandFrames, highCommandFrames = highCommandFrames,
        ))
        frames = 0
        rejected = 0
        audible = 0
        rms.clear(); this.rawRms.clear(); envelope.clear(); noise.clear(); level.clear(); command.clear()
        minAgeNs = Long.MAX_VALUE
        maxAgeNs = Long.MIN_VALUE
        callbackGapNs = 0
        waveformGapNs = 0
        waveforms = 0
        cancels = 0
        requestedMs = 0
        amplitudeMin = Int.MAX_VALUE
        amplitudeMax = 0
        defaultSegments = 0
        motorFilteredFrames = 0; filteredQuietFrames = 0; aiAdjustedFrames = 0
        lowCommandFrames = 0; middleCommandFrames = 0; highCommandFrames = 0
    }

    /** At most one snapshot survives a slow UI/consumer; no historical queue. */
    fun takeLatest(): HapticTelemetrySnapshot? = latest.getAndSet(null)
}
