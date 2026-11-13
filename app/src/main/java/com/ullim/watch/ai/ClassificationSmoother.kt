package com.ullim.watch.ai

import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.Classification

/** Small bounded worker-side history. Empty scores explicitly mean uncertain. */
class ClassificationSmoother {
    private var session = 0L
    private var lastTimestampNs = -1L
    private var latest: Classification? = null
    private var history = emptyMap<String, Float>()
    private var candidate: String? = null
    private var candidateSinceNs = 0L
    private var observations = 0

    @Synchronized fun accept(result: Classification, sessionId: Long, nowNs: Long): Classification? {
        if (!result.isFresh(sessionId, nowNs)) return current(sessionId, nowNs)
        if (session != sessionId) { reset(); session = sessionId }
        if (result.timestampNs <= lastTimestampNs) return current(sessionId, nowNs)
        if (latest?.let { result.timestampNs >= it.validUntilNs } == true) clearHistory()
        lastTimestampNs = result.timestampNs

        val raw = result.scores.groupBy { it.label }.map { (label, scores) ->
            ClassScore(label, scores.maxOf { it.confidence })
        }.sortedByDescending { it.confidence }.take(5)
        val incoming = raw.associate { it.label to it.confidence }
        val smoothed = if (raw.isEmpty()) emptyList() else if (history.isEmpty()) raw else {
            (history.keys + incoming.keys).map { label ->
                val previous = history[label] ?: 0f
                // Equivalent EMA, preserving an unchanged value at confidence/margin boundaries.
                ClassScore(label, previous + .6f * ((incoming[label] ?: 0f) - previous))
            }.sortedByDescending { it.confidence }.take(5)
        }
        history = smoothed.associate { it.label to it.confidence }
        val winner = smoothed.firstOrNull()?.label
        val confident = credible(raw) && credible(smoothed) && raw.first().label == winner
        if (!confident) { candidate = null; observations = 0 }
        else if (candidate != winner) {
            candidate = winner
            candidateSinceNs = result.timestampNs
            observations = 1
        } else observations = minOf(2, observations + 1)
        val confirmed = confident && observations >= 2 && result.timestampNs - candidateSinceNs >= 400_000_000L
        latest = Classification(result.sessionId, result.timestampNs, result.validUntilNs,
            if (confirmed) smoothed else emptyList())
        return latest
    }

    @Synchronized fun current(sessionId: Long, nowNs: Long): Classification? =
        latest?.takeIf { it.isFresh(sessionId, nowNs) }

    @Synchronized fun reset() {
        session = 0
        lastTimestampNs = -1
        latest = null
        clearHistory()
    }

    private fun clearHistory() { history = emptyMap(); candidate = null; observations = 0 }
    private fun credible(scores: List<ClassScore>): Boolean = scores.isNotEmpty() &&
        scores[0].confidence >= .35f && scores[0].confidence - (scores.getOrNull(1)?.confidence ?: 0f) >= .08f
}
