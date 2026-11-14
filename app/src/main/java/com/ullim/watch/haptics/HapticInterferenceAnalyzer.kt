package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.HapticFrame

/**
 * Measures temporal overlap between haptic commands and audio features.
 *
 * This class deliberately does not mute or suppress audio. A time-correlated
 * sound can still be an external event, so suppression must be based on
 * measured device data and a separately validated policy.
 */
class HapticInterferenceAnalyzer(
    private val baselineWindowNs: Long = DEFAULT_BASELINE_WINDOW_NS,
    private val postCommandTailNs: Long = DEFAULT_POST_COMMAND_TAIL_NS,
    private val maxRecentAudioFrames: Int = DEFAULT_MAX_RECENT_AUDIO_FRAMES,
    private val maxCommandHistory: Int = DEFAULT_MAX_COMMAND_HISTORY,
) {
    private data class CommandMeasurement(
        val command: HapticFrame,
        val baselineFrameCount: Int,
        val baselineRmsSum: Double,
        var correlatedFrameCount: Int = 0,
        var correlatedRmsSum: Double = 0.0,
        var correlatedPeak: Float? = null,
        var correlatedOnset: Float? = null,
    )

    private val recentAudio = ArrayDeque<AudioFeatures>()
    private val commands = ArrayDeque<CommandMeasurement>()

    init {
        require(baselineWindowNs > 0)
        require(postCommandTailNs >= 0)
        require(maxRecentAudioFrames > 0)
        require(maxCommandHistory > 0)
    }

    @Synchronized
    fun recordCommand(command: HapticFrame) {
        val baselineStartNs = (command.timestampNs - baselineWindowNs).coerceAtLeast(0)
        val baseline = recentAudio.filter {
            it.sessionId == command.sessionId &&
                it.timestampNs >= baselineStartNs &&
                it.timestampNs < command.timestampNs
        }
        commands.addLast(
            CommandMeasurement(
                command = command,
                baselineFrameCount = baseline.size,
                baselineRmsSum = baseline.sumOf { it.rms.toDouble() },
            ),
        )
        while (commands.size > maxCommandHistory) commands.removeFirst()
    }

    /**
     * Adds one audio feature frame and updates every command window it overlaps.
     * Returns snapshots for commands correlated with this frame.
     */
    @Synchronized
    fun observe(features: AudioFeatures): List<HapticInterferenceSnapshot> {
        recentAudio.addLast(features)
        while (recentAudio.size > maxRecentAudioFrames) recentAudio.removeFirst()

        val matching = commands.filter { measurement ->
            val command = measurement.command
            val commandEndNs = saturatingAdd(
                command.timestampNs,
                command.durationMs * NANOS_PER_MILLISECOND,
            )
            val observationEndNs = saturatingAdd(commandEndNs, postCommandTailNs)
            features.sessionId == command.sessionId &&
                features.timestampNs >= command.timestampNs &&
                features.timestampNs <= observationEndNs
        }

        matching.forEach { measurement ->
            measurement.correlatedFrameCount++
            measurement.correlatedRmsSum += features.rms.toDouble()
            measurement.correlatedPeak = maxOf(measurement.correlatedPeak ?: 0f, features.peak)
            measurement.correlatedOnset = maxOf(measurement.correlatedOnset ?: 0f, features.onset)
        }
        return matching.map(::snapshot)
    }

    @Synchronized
    fun snapshots(): List<HapticInterferenceSnapshot> = commands.map(::snapshot)

    @Synchronized
    fun clear() {
        recentAudio.clear()
        commands.clear()
    }

    private fun snapshot(measurement: CommandMeasurement): HapticInterferenceSnapshot {
        val correlatedCount = measurement.correlatedFrameCount
        return HapticInterferenceSnapshot(
            sessionId = measurement.command.sessionId,
            commandTimestampNs = measurement.command.timestampNs,
            commandDurationMs = measurement.command.durationMs,
            commandedIntensity = measurement.command.commandedIntensity,
            baselineFrameCount = measurement.baselineFrameCount,
            baselineMeanRms = measurement.baselineRmsSum
                .takeIf { measurement.baselineFrameCount > 0 }
                ?.div(measurement.baselineFrameCount)
                ?.toFloat(),
            correlatedFrameCount = correlatedCount,
            correlatedMeanRms = measurement.correlatedRmsSum
                .takeIf { correlatedCount > 0 }
                ?.div(correlatedCount)
                ?.toFloat(),
            correlatedPeak = measurement.correlatedPeak,
            correlatedOnset = measurement.correlatedOnset,
        )
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    companion object {
        const val DEFAULT_BASELINE_WINDOW_NS = 200_000_000L
        const val DEFAULT_POST_COMMAND_TAIL_NS = 120_000_000L
        const val DEFAULT_MAX_RECENT_AUDIO_FRAMES = 64
        const val DEFAULT_MAX_COMMAND_HISTORY = 64
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

data class HapticInterferenceSnapshot(
    val sessionId: Long,
    val commandTimestampNs: Long,
    val commandDurationMs: Long,
    val commandedIntensity: Float,
    val baselineFrameCount: Int,
    val baselineMeanRms: Float?,
    val correlatedFrameCount: Int,
    val correlatedMeanRms: Float?,
    val correlatedPeak: Float?,
    val correlatedOnset: Float?,
)
