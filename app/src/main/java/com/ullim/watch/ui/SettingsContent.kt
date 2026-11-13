package com.ullim.watch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.ullim.watch.R
import com.ullim.watch.core.ListeningState
import com.ullim.watch.settings.SettingsError
import com.ullim.watch.settings.SettingsState
import kotlin.math.roundToInt

@Composable
internal fun SettingsContent(
    settings: SettingsState,
    phase: ListeningState,
    limited: Boolean,
    onSensitivity: (Float) -> Unit,
    onAiToggle: () -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
) {
    val value = settings.values.sensitivity
    SettingsText(stringResource(R.string.sensitivity_value, (value * 100).roundToInt()))
    SettingsText(stringResource(R.string.sensitivity_defaults))
    val currentSensitivity = stringResource(R.string.sensitivity_state, (value * 100).roundToInt())
    SettingAction(R.string.sensitivity_down, settings.ready && value > .75f, currentSensitivity) { onSensitivity((value - .25f).coerceAtLeast(.75f)) }
    SettingAction(R.string.sensitivity_up, settings.ready && value < 1.25f, currentSensitivity) { onSensitivity((value + .25f).coerceAtMost(1.25f)) }
    AiModeControl(settings.values.aiEnabled, settings.ready, onAiToggle)
    SettingsText(stringResource(when {
        phase !is ListeningState.Listening -> R.string.output_capability_pending
        limited -> R.string.amplitude_unsupported
        else -> R.string.output_control_available
    }))
    SettingsSaveStatus(settings, onRetry)
    SettingAction(R.string.settings_reset, settings.ready, onClick = onReset)
}

@Composable
internal fun SettingsSaveStatus(settings: SettingsState, onRetry: () -> Unit) {
    SettingsText(stringResource(when {
        !settings.ready -> R.string.settings_loading
        settings.saving -> R.string.settings_saving
        settings.error == SettingsError.READ -> R.string.settings_read_failed
        settings.error == SettingsError.WRITE -> R.string.settings_write_failed
        else -> R.string.settings_saved
    }))
    if (settings.ready && settings.error != null && !settings.saving) SettingAction(R.string.settings_retry, true, onClick = onRetry)
}

@Composable
private fun SettingAction(label: Int, enabled: Boolean, currentValue: String? = null, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
        currentValue?.let { stateDescription = it }
    }, enabled = enabled) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}
