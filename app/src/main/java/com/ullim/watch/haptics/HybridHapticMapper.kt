package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.Classification
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.HapticMapper
import kotlin.math.abs
import com.ullim.watch.settings.TuningSettings
import com.ullim.watch.settings.TuningParameter.*

enum class AiTactileStatus { NO_CONTEXT, UNCERTAIN, WAITING_EVENT, SILENT, TRACKING, STABILIZING }

/** Labels establish continuity only; no sound category selects a strength curve. */
class HybridHapticMapper(private val baseline: HapticMapper = EnvelopeHapticMapper(),
    private val readTuning: () -> TuningSettings = { TuningSettings.DEFAULT }) : HapticMapper {
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null
    private var eventStartNs: Long? = null
    private var activeLabel: String? = null
    private var previousBase: Float? = null
    private var previousOutput: Float? = null
    var lastAiApplied = false
        private set
    var lastBaseline = 0f
        private set
    var lastStatus = AiTactileStatus.NO_CONTEXT
        private set

    override fun map(features: AudioFeatures, context: Classification?): HapticFrame {
        lastAiApplied = false
        lastStatus = AiTactileStatus.NO_CONTEXT
        val t = readTuning()
        val base = baseline.map(features)
        lastBaseline = base.commandedIntensity
        if (sessionId != features.sessionId || lastTimestampNs?.let { features.timestampNs != it + 20_000_000L } == true) {
            clearPolicy()
        }
        sessionId = features.sessionId
        lastTimestampNs = features.timestampNs
        if (base.isSilent) {
            lastStatus = AiTactileStatus.SILENT
            clearPolicy()
            return base
        }
        val newEvent = eventStartNs == null
        if (newEvent) eventStartNs = features.timestampNs
        val label = reliableLabel(context, features, t) ?: run { clearFilter(); return base }
        if (activeLabel == null && !newEvent && features.timestampNs - eventStartNs!! < (t[AI_WAIT_MS] * 1_000_000).toLong()) {
            lastStatus = AiTactileStatus.WAITING_EVENT
            return base
        }
        val x = base.commandedIntensity
        val oldBase = previousBase
        val oldOutput = previousOutput
        val sameContext = activeLabel == label
        activeLabel = label
        previousBase = x
        lastStatus = AiTactileStatus.TRACKING
        // First response, source changes, onsets and substantial level changes
        // pass straight through. Invalid/OFF context returns exact baseline.
        val y = if (!sameContext || oldBase == null || oldOutput == null ||
            features.onset > .2f || abs(x - oldBase) > .08f || abs(x - oldOutput) > .08f
        ) x else {
            val smoothed = (oldOutput + (x - oldOutput) * t[AI_SMOOTHING]).coerceIn(x - t[AI_LIMIT], x + t[AI_LIMIT])
            // AI cannot move a sound into a different pulse-output band.
            when {
                x <= t[LONG_START] -> smoothed.coerceIn(0f, t[LONG_START])
                x >= t[LONG_END] -> smoothed.coerceIn(t[LONG_END], 1f)
                else -> smoothed.coerceIn(t[LONG_START], t[LONG_END])
            }
        }
        val bounded = y.coerceIn(if (features.isSilent) 0f else t[OUTPUT_FLOOR], t[OUTPUT_CEILING])
        previousOutput = bounded
        lastAiApplied = abs(bounded - x) > .000001f
        if (lastAiApplied) lastStatus = AiTactileStatus.STABILIZING
        return base.copy(commandedIntensity = bounded)
    }

    private fun reliableLabel(context: Classification?, features: AudioFeatures, t: TuningSettings): String? {
        if (context == null || !context.isFresh(features.sessionId, features.timestampNs)) return null
        val scores = context.scores.sortedByDescending { it.confidence }
        val winner = scores.firstOrNull() ?: return null
        if (winner.confidence < t[AI_CONFIDENCE] || winner.confidence - (scores.getOrNull(1)?.confidence ?: 0f) < t[AI_MARGIN]) {
            lastStatus = AiTactileStatus.UNCERTAIN
            return null
        }
        return winner.label
    }

    override fun reset() {
        baseline.reset()
        sessionId = 0
        lastTimestampNs = null
        clearPolicy()
        lastAiApplied = false
        lastBaseline = 0f
        lastStatus = AiTactileStatus.NO_CONTEXT
    }

    private fun clearFilter() { activeLabel = null; previousBase = null; previousOutput = null }
    private fun clearPolicy() { eventStartNs = null; clearFilter() }
}
