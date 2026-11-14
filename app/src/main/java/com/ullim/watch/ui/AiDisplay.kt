package com.ullim.watch.ui

import com.ullim.watch.ai.AiFault
import com.ullim.watch.ai.AiPhase
import com.ullim.watch.ai.AiSnapshot
import com.ullim.watch.ai.SoundLabels
import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.ListeningState
import com.ullim.watch.core.RuntimeState
import kotlin.math.roundToInt

enum class AiDisplayStatus { OFF, WAITING, LOADING, LISTENING, DELAYED, FAILED }
enum class AiResultKind { NONE, WAITING, UNCERTAIN, EXPIRED, UNSUPPORTED, RECOGNIZED }
data class AiCandidateDisplay(val label: String, val scorePercent: Int)
data class AiDisplayState(
    val status: AiDisplayStatus = AiDisplayStatus.OFF,
    val result: AiResultKind = AiResultKind.NONE,
    val label: String? = null,
    val originalLabel: String? = null,
    val modelScorePercent: Int? = null,
    val cleanupPending: Boolean = false,
    val restartRequired: Boolean = false,
    val baselineContinues: Boolean = false,
    val faultCode: String? = null,
    val candidates: List<AiCandidateDisplay> = emptyList(),
    val completedInferences: Long = 0,
    val automaticRetries: Int = 0,
)

/** Presentation only: never changes worker, microphone or haptic state. */
object AiDisplay {
    fun project(enabled: Boolean, runtime: RuntimeState, snapshot: AiSnapshot, nowNs: Long): AiDisplayState {
        val session = when (val phase = runtime.listening) {
            is ListeningState.Starting -> phase.sessionId
            is ListeningState.Listening -> phase.sessionId
            else -> null
        }
        val status = when {
            !enabled -> AiDisplayStatus.OFF
            session == null -> if (snapshot.phase == AiPhase.FAILED) AiDisplayStatus.FAILED else AiDisplayStatus.WAITING
            session != snapshot.sessionId -> AiDisplayStatus.LOADING
            else -> when (snapshot.phase) {
                AiPhase.DISABLED, AiPhase.LOADING -> AiDisplayStatus.LOADING
                AiPhase.LISTENING -> AiDisplayStatus.LISTENING
                AiPhase.DELAYED -> AiDisplayStatus.DELAYED
                AiPhase.FAILED -> AiDisplayStatus.FAILED
            }
        }
        val candidates = snapshot.rawClassification?.takeIf {
            status == AiDisplayStatus.LISTENING && session != null && it.isFresh(session, nowNs)
        }?.scores?.sortedByDescending { it.confidence }?.take(3)?.map {
            AiCandidateDisplay(SoundLabels.display(it.label), (it.confidence * 100).roundToInt())
        }.orEmpty()
        val base = AiDisplayState(status = status, cleanupPending = snapshot.cleanupPending,
            candidates = candidates, completedInferences = if (snapshot.sessionId == session) snapshot.successfulInferences else 0,
            automaticRetries = if (snapshot.sessionId == session) snapshot.automaticRetries else 0,
            restartRequired = snapshot.fault == AiFault.CLEANUP || snapshot.fault == AiFault.EXECUTOR,
            baselineContinues = runtime.listening is ListeningState.Listening && status != AiDisplayStatus.LISTENING,
            faultCode = snapshot.fault?.takeIf { status == AiDisplayStatus.FAILED || it == AiFault.CLEANUP || it == AiFault.EXECUTOR }?.let { "AI_${it.name}" })
        if (status != AiDisplayStatus.LISTENING || runtime.listening !is ListeningState.Listening) return base
        val classification = snapshot.classification
            ?: return base.copy(result = if (snapshot.resultExpired) AiResultKind.EXPIRED else AiResultKind.WAITING)
        if (classification.sessionId != session || classification.timestampNs > nowNs) return base.copy(result = AiResultKind.WAITING)
        if (!classification.isFresh(session, nowNs)) return base.copy(result = AiResultKind.EXPIRED)
        val scores = classification.scores.groupBy { it.label }.map { (label, values) ->
            ClassScore(label, values.maxOf { it.confidence })
        }.sortedByDescending { it.confidence }
        val top = scores.firstOrNull()
        if (top == null || top.confidence < .35f || top.confidence - (scores.getOrNull(1)?.confidence ?: 0f) < .08f) {
            return base.copy(result = AiResultKind.UNCERTAIN)
        }
        if (top.label.trim().lowercase() in setOf("unknown", "<unknown>", "unsupported", "unrecognized")) {
            return base.copy(result = AiResultKind.UNSUPPORTED)
        }
        val translated = SoundLabels.display(top.label)
        return base.copy(result = AiResultKind.RECOGNIZED, label = translated,
            originalLabel = top.label.takeIf { it != translated }, modelScorePercent = (top.confidence * 100).roundToInt())
    }
}
