package com.ullim.watch.ui

import com.ullim.watch.core.RuntimeState
import com.ullim.watch.runtime.LiveLevels
import com.ullim.watch.runtime.VisualizationSnapshot
import com.ullim.watch.settings.SettingsState
import com.ullim.watch.runtime.CaptureFailure

/** Values observed from the controller; previews use static samples only. */
data class ListeningUiState(
    val runtime: RuntimeState = RuntimeState(),
    val startAllowed: Boolean = true,
    val levels: LiveLevels = LiveLevels(),
    val strengthLimited: Boolean = false,
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val visualization: VisualizationSnapshot = VisualizationSnapshot(),
    val ai: AiDisplayState = AiDisplayState(),
    val settings: SettingsState = SettingsState(),
    val failure: CaptureFailure? = null,
    val showOnboarding: Boolean = false,
    val microphonePermissionPermanentlyDenied: Boolean = false,
)
