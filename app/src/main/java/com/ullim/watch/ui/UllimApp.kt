package com.ullim.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.ullim.watch.R
import com.ullim.watch.ui.theme.UllimTheme

private enum class UllimScreen { HOME, SETTINGS, GUIDE, ERROR }

@Composable
fun UllimApp() {
    var screen by rememberSaveable { mutableStateOf(UllimScreen.HOME) }
    BackHandler(enabled = screen != UllimScreen.HOME) {
        screen = when (screen) {
            UllimScreen.SETTINGS, UllimScreen.GUIDE -> UllimScreen.HOME
            UllimScreen.ERROR -> UllimScreen.GUIDE
            UllimScreen.HOME -> UllimScreen.HOME
        }
    }

    UllimTheme {
        AppScaffold {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (screen) {
                        UllimScreen.HOME -> HomeScreen(
                            onSettings = { screen = UllimScreen.SETTINGS },
                            onGuide = { screen = UllimScreen.GUIDE },
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

@Composable
private fun HomeScreen(onSettings: () -> Unit, onGuide: () -> Unit) {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
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
    Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_title))
    }
    OutlinedButton(onClick = onGuide, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.guide_title))
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.settings_placeholder),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.back_action))
    }
}

@Composable
private fun GuideScreen(onErrorGuide: () -> Unit, onBack: () -> Unit) {
    Text(
        text = stringResource(R.string.guide_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.guide_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(onClick = onErrorGuide, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.error_guide_action))
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.back_action))
    }
}

@Composable
private fun ErrorGuideScreen(onBack: () -> Unit) {
    Text(
        text = stringResource(R.string.error_guide_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.error_guide_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.back_action))
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun UllimAppPreview() {
    UllimApp()
}
