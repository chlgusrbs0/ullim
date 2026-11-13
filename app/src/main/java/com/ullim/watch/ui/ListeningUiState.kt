package com.ullim.watch.ui

import com.ullim.watch.core.RuntimeState
import com.ullim.watch.runtime.LiveLevels

/** Values observed from the controller; previews use static samples only. */
data class ListeningUiState(
    val runtime: RuntimeState = RuntimeState(),
    val startAllowed: Boolean = true,
    val levels: LiveLevels = LiveLevels(),
    val strengthLimited: Boolean = false,
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
)
