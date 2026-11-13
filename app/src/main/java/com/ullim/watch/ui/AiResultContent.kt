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
}

@Composable
private fun AiText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}
