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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.ullim.watch.ui.theme.UllimTheme

private enum class UllimScreen { HOME, SETTINGS, GUIDE, ERROR }

@Composable
fun UllimApp(onDiagnostics: () -> Unit = {}) {
    var screen by rememberSaveable { mutableStateOf(UllimScreen.HOME) }
    val scrollState = rememberScrollState()

    BackHandler(enabled = screen != UllimScreen.HOME) {
        screen = when (screen) {
            UllimScreen.SETTINGS, UllimScreen.GUIDE -> UllimScreen.HOME
            UllimScreen.ERROR -> UllimScreen.GUIDE
            UllimScreen.HOME -> UllimScreen.HOME
        }
    }

    LaunchedEffect(screen) {
        scrollState.scrollTo(0)
    }

    UllimTheme {
        AppScaffold {
            ScreenScaffold(scrollState = scrollState) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .verticalScroll(scrollState)
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (screen) {
                            UllimScreen.HOME -> HomeScreen(
                                onSettings = { screen = UllimScreen.SETTINGS },
                                onGuide = { screen = UllimScreen.GUIDE },
                                onDiagnostics = onDiagnostics,
                            )
                            UllimScreen.SETTINGS -> SettingsScreen(
                                onBack = { screen = UllimScreen.HOME },
                            )
                            UllimScreen.GUIDE -> GuideScreen(
                                onErrorGuide = { screen = UllimScreen.ERROR },
                                onBack = { screen = UllimScreen.HOME },
                            )
                            UllimScreen.ERROR -> ErrorGuideScreen(
                                onBack = { screen = UllimScreen.GUIDE },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HomeScreen(onSettings: () -> Unit, onGuide: () -> Unit, onDiagnostics: () -> Unit = {}) {
    ScreenTitle(stringResource(R.string.app_name))
    Text(
        text = stringResource(R.string.tagline),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.foundation_status),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onSettings,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.settings_title))
    }
    OutlinedButton(
        onClick = onGuide,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.guide_title))
    }
    OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.diagnostics))
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    ScreenTitle(stringResource(R.string.settings_title))
    Text(
        text = stringResource(R.string.settings_placeholder),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.back_action))
    }
}

@Composable
private fun GuideScreen(onErrorGuide: () -> Unit, onBack: () -> Unit) {
    ScreenTitle(stringResource(R.string.guide_title))
    Text(
        text = stringResource(R.string.guide_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(
        onClick = onErrorGuide,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.error_guide_action))
    }
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.back_action))
    }
}

@Composable
private fun ErrorGuideScreen(onBack: () -> Unit) {
    ScreenTitle(stringResource(R.string.error_guide_title))
    Text(
        text = stringResource(R.string.error_guide_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.back_action))
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "울림 앱")
@Composable
private fun UllimAppPreview() {
    UllimApp()
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "울림 홈")
@Composable
private fun HomeScreenPreview() {
    UllimTheme { HomeScreen(onSettings = {}, onGuide = {}) }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "설정")
@Composable
private fun SettingsScreenPreview() {
    UllimTheme { SettingsScreen(onBack = {}) }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "사용 안내")
@Composable
private fun GuideScreenPreview() {
    UllimTheme { GuideScreen(onErrorGuide = {}, onBack = {}) }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true, name = "문제 해결")
@Composable
private fun ErrorGuideScreenPreview() {
    UllimTheme { ErrorGuideScreen(onBack = {}) }
}
