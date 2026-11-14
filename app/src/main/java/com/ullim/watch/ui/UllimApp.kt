package com.ullim.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.ullim.watch.R
import com.ullim.watch.core.ListeningState
import com.ullim.watch.ui.theme.UllimTheme

private enum class UllimScreen { ONBOARDING, HOME, SETTINGS, GUIDE, ERROR }

@Composable
fun UllimApp(
    state: ListeningUiState = ListeningUiState(),
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onAiSettings: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onSensitivity: (Float) -> Unit = {},
    onAiToggle: () -> Unit = {},
    onSettingsReset: () -> Unit = {},
    onSettingsRetry: () -> Unit = {},
    onCompleteOnboarding: () -> Unit = {},
    onRequestMicrophone: () -> Unit = {},
    onTuning: () -> Unit = {},
) {
    var screen by rememberSaveable { mutableStateOf(if (state.showOnboarding) UllimScreen.ONBOARDING else UllimScreen.HOME) }
    val scrollState = rememberScrollState()
    LaunchedEffect(state.showOnboarding) {
        if (state.showOnboarding) screen = UllimScreen.ONBOARDING
        else if (screen == UllimScreen.ONBOARDING) screen = UllimScreen.HOME
    }
    val pageTitle = stringResource(when (screen) {
        UllimScreen.ONBOARDING -> R.string.onboarding_title
        UllimScreen.HOME -> R.string.app_name
        UllimScreen.SETTINGS -> R.string.settings_title
        UllimScreen.GUIDE -> R.string.guide_title
        UllimScreen.ERROR -> R.string.error_guide_title
    })
    BackHandler(enabled = screen != UllimScreen.HOME) {
        if (screen == UllimScreen.ONBOARDING) onCompleteOnboarding()
        screen = if (screen == UllimScreen.ERROR) UllimScreen.GUIDE else UllimScreen.HOME
    }
    LaunchedEffect(screen) { scrollState.scrollTo(0) }
    UllimTheme {
        AppScaffold {
            ScreenScaffold(scrollState = scrollState) { contentPadding ->
                Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxWidth(0.82f).verticalScroll(scrollState).padding(vertical = 16.dp)
                            .semantics { paneTitle = pageTitle; isTraversalGroup = true },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (screen) {
                            UllimScreen.ONBOARDING -> {
                                ScreenTitle(stringResource(R.string.onboarding_title))
                                Body(stringResource(R.string.onboarding_purpose))
                                Body(stringResource(R.string.onboarding_microphone))
                                Body(stringResource(R.string.onboarding_privacy))
                                Body(stringResource(R.string.onboarding_background))
                                Body(stringResource(R.string.onboarding_limits))
                                Action(if (state.microphoneGranted) R.string.onboarding_continue else R.string.onboarding_allow_microphone, {
                                    screen = UllimScreen.HOME
                                    onCompleteOnboarding()
                                    if (!state.microphoneGranted) onRequestMicrophone()
                                })
                                Action(R.string.onboarding_later, {
                                    screen = UllimScreen.HOME
                                    onCompleteOnboarding()
                                })
                            }
                            UllimScreen.HOME -> HomeScreen(state, onStart, onStop, onAppSettings, onDiagnostics, onAiSettings, onRequestMicrophone,
                                onSettings = { screen = UllimScreen.SETTINGS },
                                onGuide = { screen = UllimScreen.GUIDE }, onTuning = onTuning)
                            UllimScreen.SETTINGS -> {
                                ScreenTitle(stringResource(R.string.settings_title))
                                SettingsContent(state.settings, state.runtime.listening, state.strengthLimited,
                                    onSensitivity, onAiToggle, onSettingsReset, onSettingsRetry, onTuning)
                                Action(R.string.ai_details, onAiSettings)
                                Action(R.string.diagnostics, onDiagnostics, enabled = state.startAllowed)
                                Action(R.string.back_action, { screen = UllimScreen.HOME })
                            }
                            UllimScreen.GUIDE -> {
                                ScreenTitle(stringResource(R.string.guide_title))
                                Body(stringResource(R.string.guide_body))
                                Action(R.string.error_guide_action, { screen = UllimScreen.ERROR })
                                Action(R.string.back_action, { screen = UllimScreen.HOME })
                            }
                            UllimScreen.ERROR -> {
                                ScreenTitle(stringResource(R.string.error_guide_title))
                                Body(stringResource(R.string.error_guide_body))
                                Action(R.string.permission_settings, onAppSettings)
                                Action(R.string.back_action, { screen = UllimScreen.GUIDE })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

@Composable
private fun Action(label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeScreen(
    state: ListeningUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAppSettings: () -> Unit,
    onDiagnostics: () -> Unit,
    onAiSettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onSettings: () -> Unit,
    onGuide: () -> Unit,
    onTuning: () -> Unit,
) {
    val phase = state.runtime.listening
    val idle = phase == ListeningState.Idle || phase is ListeningState.Error
    val failure = state.failure.takeIf { phase is ListeningState.Error }
    val phaseDescription = when (phase) {
        ListeningState.Idle -> stringResource(R.string.ready_to_listen)
        is ListeningState.Starting -> stringResource(R.string.starting_listening)
        is ListeningState.Listening -> stringResource(R.string.active_listening)
        is ListeningState.Stopping -> stringResource(R.string.stopping_listening)
        is ListeningState.Error -> phase.reason
    }
    ScreenTitle(stringResource(R.string.app_name))
    Text(
        phaseDescription,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    failure?.let { Body(stringResource(R.string.error_code, it.code)) }
    Button(
        onClick = if (idle) onStart else onStop,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { stateDescription = phaseDescription },
        enabled = if (idle) state.startAllowed && state.microphoneGranted else phase is ListeningState.Listening,
    ) {
        Text(stringResource(when (phase) {
            ListeningState.Idle, is ListeningState.Error -> R.string.start_listening
            is ListeningState.Starting -> R.string.starting_action
            is ListeningState.Listening -> R.string.stop
            is ListeningState.Stopping -> R.string.stopping_action
        }), textAlign = TextAlign.Center)
    }
    if (phase is ListeningState.Starting) Action(R.string.cancel_start, onStop)
    if (failure?.openDiagnostics == true) Action(R.string.check_haptic_support, onDiagnostics, enabled = state.startAllowed)
    if (failure?.restartRequired == true) Action(R.string.open_app_settings, onAppSettings)
    if (phase is ListeningState.Listening) {
        Body(stringResource(R.string.levels, (state.levels.sound * 100).toInt(), (state.levels.commanded * 100).toInt()))
        if (state.strengthLimited) Body(stringResource(R.string.amplitude_unsupported))
    }
    AiResultContent(state.ai)
    Action(R.string.ai_details, onAiSettings)
    Action(R.string.live_tuning, onTuning)
    ComparisonGraph(state.visualization, active = phase is ListeningState.Listening)
    if (!state.microphoneGranted) {
        Body(stringResource(if (state.microphonePermissionPermanentlyDenied) R.string.microphone_permission_blocked else R.string.microphone_permission_needed))
        if (!state.microphonePermissionPermanentlyDenied) Action(R.string.microphone_permission_retry, onRequestMicrophone)
    }
    if (!state.notificationsGranted) Body(stringResource(R.string.notifications_limited))
    if (!state.microphoneGranted || !state.notificationsGranted || (failure?.openSettings == true && !failure.restartRequired)) {
        Action(R.string.permission_settings, onAppSettings)
    }
    Action(R.string.settings_title, onSettings)
    Action(R.string.guide_title, onGuide)
    Text(stringResource(R.string.local_audio_notice), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "울림 앱")
@Composable
private fun UllimAppPreview() {
    UllimApp(ListeningUiState(microphoneGranted = true, notificationsGranted = true))
}
