package com.ullim.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ullim.watch.R
import com.ullim.watch.ai.AiSnapshot
import com.ullim.watch.core.RuntimeState
import kotlinx.coroutines.flow.StateFlow
import com.ullim.watch.runtime.LiveLevels
import com.ullim.watch.haptics.AiTactileStatus

@Composable
internal fun collectAiDisplay(
    enabled: StateFlow<Boolean>,
    runtime: StateFlow<RuntimeState>,
    worker: StateFlow<AiSnapshot>,
    clockNs: () -> Long = System::nanoTime,
): AiDisplayState {
    val choice by enabled.collectAsStateWithLifecycle()
    val capture by runtime.collectAsStateWithLifecycle()
    val snapshot by worker.collectAsStateWithLifecycle()
    return AiDisplay.project(choice, capture, snapshot, clockNs())
}

@Composable
internal fun AiResultContent(state: AiDisplayState, showOriginal: Boolean = false, showTitle: Boolean = true) {
    if (showTitle) Text(stringResource(R.string.ai_controls), Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    val statusText = when (state.status) {
        AiDisplayStatus.OFF -> R.string.ai_disabled
        AiDisplayStatus.WAITING -> R.string.ai_waiting_for_capture
        AiDisplayStatus.LOADING -> R.string.ai_loading
        AiDisplayStatus.LISTENING -> R.string.ai_listening
        AiDisplayStatus.DELAYED -> R.string.ai_delayed
        AiDisplayStatus.FAILED -> if (state.restartRequired) R.string.ai_restart_needed else R.string.ai_failed
    }
    AiText(stringResource(statusText))
    state.faultCode?.let { AiText(stringResource(R.string.error_code, it)) }
    when (state.result) {
        AiResultKind.NONE -> Unit
        AiResultKind.WAITING -> AiText(stringResource(R.string.ai_result_waiting))
        AiResultKind.UNCERTAIN -> AiText(stringResource(R.string.ai_uncertain))
        AiResultKind.EXPIRED -> AiText(stringResource(R.string.ai_result_expired))
        AiResultKind.UNSUPPORTED -> AiText(stringResource(R.string.ai_unsupported))
        AiResultKind.RECOGNIZED -> {
            state.label?.let { label ->
                AiText(if (state.originalLabel == null) stringResource(R.string.ai_original_result, label) else label)
            }
            state.modelScorePercent?.let { AiText(stringResource(R.string.ai_model_score, it)) }
            if (showOriginal) state.originalLabel?.let { AiText(stringResource(R.string.ai_original_label, it)) }
        }
    }
    if (state.baselineContinues) AiText(stringResource(R.string.ai_baseline_continues))
    if (state.cleanupPending) AiText(stringResource(R.string.ai_cleanup_pending))
    if (state.restartRequired && state.status != AiDisplayStatus.FAILED) AiText(stringResource(R.string.ai_restart_needed))
    if (showOriginal) {
        AiText(stringResource(R.string.ai_execution_counts, state.completedInferences, state.automaticRetries))
        state.candidates.forEach { AiText(stringResource(R.string.ai_candidate, it.label, it.scorePercent)) }
        if (state.candidates.isNotEmpty()) AiText(stringResource(R.string.ai_candidate_notice))
    }
}

@Composable
internal fun SignalPathContent(levels: LiveLevels, aiEnabled: Boolean) {
    AiText(stringResource(R.string.signal_path_input, (levels.rawSound * 100).toInt(), (levels.sound * 100).toInt()))
    AiText(stringResource(R.string.signal_path_output, (levels.baselineCommanded * 100).toInt(), (levels.commanded * 100).toInt()))
    AiText(stringResource(if (levels.motorFiltered) R.string.motor_filter_active else R.string.motor_filter_idle))
    val policy = if (!aiEnabled) R.string.ai_policy_off else when (levels.aiPolicy) {
        AiTactileStatus.NO_CONTEXT -> R.string.ai_policy_waiting
        AiTactileStatus.UNSUPPORTED -> R.string.ai_policy_unsupported
        AiTactileStatus.UNCERTAIN -> R.string.ai_policy_uncertain
        AiTactileStatus.WAITING_EVENT -> R.string.ai_policy_event
        AiTactileStatus.SILENT -> R.string.ai_policy_silent
        AiTactileStatus.DETAIL -> R.string.ai_policy_detail
        AiTactileStatus.CONTRAST -> R.string.ai_policy_contrast
    }
    AiText(stringResource(policy))
    AiText(stringResource(R.string.signal_path_notice))
}

@Composable
private fun AiText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}
