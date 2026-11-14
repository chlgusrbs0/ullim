package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.Classification
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.HapticMapper
import kotlin.math.pow

/** AI never changes the baseline mapper's history or creates a sound-name pattern. */
class HybridHapticMapper(private val baseline: HapticMapper = EnvelopeHapticMapper()) : HapticMapper {
    private enum class Profile { DETAIL, CONTRAST }
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null
    private var eventStartNs: Long? = null
    private var profile: Profile? = null
    private var mix = 0f

    override fun map(features: AudioFeatures, context: Classification?): HapticFrame {
        val base = baseline.map(features)
        if (sessionId != features.sessionId || lastTimestampNs?.let { features.timestampNs != it + 20_000_000L } == true) {
            clearPolicy()
        }
        sessionId = features.sessionId
        lastTimestampNs = features.timestampNs
        if (base.isSilent) {
            clearPolicy()
            return base
        }
        val newEvent = eventStartNs == null
        if (newEvent) eventStartNs = features.timestampNs
        val target = target(context, features)
        if (target == null) {
            profile = null
            mix = 0f
            return base
        }
        // Keep one curve within a short event. A late first result can assist a
        // sustained sound after 500 ms, fading in rather than creating an onset.
        if (profile == null && (newEvent || features.timestampNs - eventStartNs!! >= 500_000_000L)) {
            profile = target
            mix = if (newEvent) 1f else 0f
        }
        val selected = profile ?: return base
        mix += (1f - mix) * .1f
        val x = (base.commandedIntensity / .85f).coerceIn(0f, 1f)
        val shaped = when (selected) {
            Profile.DETAIL -> x.pow(.65f)
            Profile.CONTRAST -> x * x / (x * x + (1f - x) * (1f - x))
        } * .85f
        val intensity = base.commandedIntensity + (shaped - base.commandedIntensity) * mix
        return base.copy(commandedIntensity = intensity.coerceIn(0f, .85f))
    }

    private fun target(context: Classification?, features: AudioFeatures): Profile? {
        if (context == null || !context.isFresh(features.sessionId, features.timestampNs)) return null
        val scores = context.scores
        var winnerIndex = -1
        for (index in scores.indices) {
            if (winnerIndex == -1 || scores[index].confidence > scores[winnerIndex].confidence) {
                winnerIndex = index
            }
        }
        if (winnerIndex == -1) return null
        val winner = scores[winnerIndex]
        val targetProfile = profile(winner.label) ?: return null
        // Correlated ontology labels can describe the same policy. Do not sum their scores.
        var competitor = 0f
        for (score in scores) {
            if (profile(score.label) != targetProfile) competitor = maxOf(competitor, score.confidence)
        }
        if (winner.confidence < .65f || winner.confidence - competitor < .15f) return null
        return targetProfile
    }

    private fun profile(label: String): Profile? = when (label) {
        "Speech", "Conversation" -> Profile.DETAIL
        "Noise", "Static", "White noise", "Pink noise", "Wind noise (microphone)",
        "Knock", "Tap", "Clapping" -> Profile.CONTRAST
        else -> null
    }

    override fun reset() {
        baseline.reset()
        sessionId = 0
        lastTimestampNs = null
        clearPolicy()
    }

    private fun clearPolicy() { eventStartNs = null; profile = null; mix = 0f }
}
