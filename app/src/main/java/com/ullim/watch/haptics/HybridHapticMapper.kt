package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.Classification
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.HapticMapper

/** AI never changes the baseline mapper's history or creates a sound-name pattern. */
class HybridHapticMapper(private val baseline: HapticMapper = EnvelopeHapticMapper()) : HapticMapper {
    private enum class Profile(val gain: Float, val focus: Float) {
        SPEECH(1.02f, 0f), NOISE(.94f, 0f), TRANSIENT(1f, .08f)
    }
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null
    private var gain = 1f
    private var focus = 0f

    override fun map(features: AudioFeatures, context: Classification?): HapticFrame {
        val base = baseline.map(features)
        if (sessionId != features.sessionId || lastTimestampNs?.let { features.timestampNs != it + 20_000_000L } == true) {
            clearPolicy()
        }
        sessionId = features.sessionId
        lastTimestampNs = features.timestampNs
        val target = target(context, features)
        if (target == null) {
            clearPolicy()
            return base
        }
        gain += (target.gain - gain) * .1f
        focus += (target.focus - focus) * .1f
        if (base.isSilent) return base
        val intensity = base.commandedIntensity *
            (gain + features.onset * focus * (1f - base.commandedIntensity))
        return base.copy(commandedIntensity = intensity.coerceIn(0f, .85f))
    }

    private fun target(context: Classification?, features: AudioFeatures): Profile? {
        if (context == null || !context.isFresh(features.sessionId, features.timestampNs)) return null
        val scores = context.scores
        var winnerIndex = -1
        var runnerUp = 0f
        for (index in scores.indices) {
            if (winnerIndex == -1 || scores[index].confidence > scores[winnerIndex].confidence) {
                runnerUp = if (winnerIndex == -1) 0f else scores[winnerIndex].confidence
                winnerIndex = index
            } else runnerUp = maxOf(runnerUp, scores[index].confidence)
        }
        if (winnerIndex == -1) return null
        val winner = scores[winnerIndex]
        if (winner.confidence < .65f || winner.confidence - runnerUp < .15f) return null
        return when (winner.label) {
            "Speech", "Conversation" -> Profile.SPEECH
            "Noise" -> Profile.NOISE
            "Knock", "Tap", "Clapping" -> Profile.TRANSIENT
            else -> null
        }
    }

    override fun reset() {
        baseline.reset()
        sessionId = 0
        lastTimestampNs = null
        clearPolicy()
    }

    private fun clearPolicy() { gain = 1f; focus = 0f }
}
