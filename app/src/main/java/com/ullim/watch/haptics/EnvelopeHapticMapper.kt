package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.Classification
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.HapticMapper
import com.ullim.watch.settings.TuningSettings
import com.ullim.watch.settings.TuningParameter.*
import kotlin.math.pow

class EnvelopeHapticMapper(private val readTuning: () -> TuningSettings = { TuningSettings.DEFAULT }) : HapticMapper {
    private var previous = 0f
    private var sessionId = 0L
    private var lastAudibleNs: Long? = null

    override fun map(features: AudioFeatures, context: Classification?): HapticFrame {
        if (features.sessionId != sessionId) {
            reset()
            sessionId = features.sessionId
        }
        val t = readTuning()
        if (features.isSilent || features.rms <= maxOf(t[MIN_RMS], features.noise * t[NOISE_MARGIN])) {
            val elapsed = lastAudibleNs?.let { features.timestampNs - it }
            previous = if (t[GAP_HOLD_MS] > 0 && elapsed != null && elapsed in 1..(t[GAP_HOLD_MS] * 1_000_000).toLong())
                previous * (1f - t[OUTPUT_RELEASE]) else 0f
        }
        else {
            lastAudibleNs = features.timestampNs
            val level = (features.level + features.onset * t[ONSET_ACCENT] * (1f - features.level)).coerceIn(0f, 1f)
            val anchor = when {
                level <= .25f -> level / .25f * t[WEAK_OUTPUT]
                level <= .5f -> t[WEAK_OUTPUT] + (level - .25f) / .25f * (t[MIDDLE_OUTPUT] - t[WEAK_OUTPUT])
                level <= .8f -> t[MIDDLE_OUTPUT] + (level - .5f) / .3f * (t[STRONG_OUTPUT] - t[MIDDLE_OUTPUT])
                else -> t[STRONG_OUTPUT] + (level - .8f) / .2f * (1f - t[STRONG_OUTPUT])
            }
            val target = (anchor.coerceIn(0f, 1f).pow(t[CONTRAST]) * t[OUTPUT_GAIN]).coerceIn(t[OUTPUT_FLOOR], t[OUTPUT_CEILING])
            previous += (target - previous) * if (target > previous) t[OUTPUT_ATTACK] else t[OUTPUT_RELEASE]
            previous = previous.coerceIn(t[OUTPUT_FLOOR], t[OUTPUT_CEILING])
        }
        return HapticFrame(features.sessionId, features.timestampNs, 20, previous.coerceIn(0f, 1f))
    }

    override fun reset() {
        previous = 0f
        sessionId = 0
        lastAudibleNs = null
    }
}
