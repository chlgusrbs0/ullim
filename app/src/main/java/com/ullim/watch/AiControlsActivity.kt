package com.ullim.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.ui.AiResultContent
import com.ullim.watch.ui.collectAiDisplay
import com.ullim.watch.ui.theme.UllimTheme
import com.ullim.watch.settings.SettingsRuntime
import com.ullim.watch.ui.SettingsSaveStatus

class AiControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val worker = AiRuntime.worker(this)
        val preferences = SettingsRuntime.get(this)
        setContent {
            val enabled by CaptureRuntime.aiEnabled.collectAsStateWithLifecycle()
            val settings by preferences.state.collectAsStateWithLifecycle()
            val display = collectAiDisplay(CaptureRuntime.aiEnabled, CaptureRuntime.state, worker.state)
            val scroll = rememberScrollState()
            UllimTheme {
                AppScaffold {
                    ScreenScaffold(scrollState = scroll) { contentPadding ->
                        Column(
                            Modifier.padding(contentPadding).fillMaxWidth().verticalScroll(scroll)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AiResultContent(display, showOriginal = true)
                            SettingsSaveStatus(settings) { preferences.retrySave() }
                            Text(stringResource(R.string.ai_optional_notice), textAlign = TextAlign.Center)
                            Button(onClick = {
                                preferences.setAiEnabled(!preferences.state.value.values.aiEnabled)
                            }, enabled = settings.ready, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (enabled) R.string.ai_turn_off else R.string.ai_turn_on))
                            }
                            TextButton(onClick = { finish() }) { Text(stringResource(R.string.ai_back)) }
                        }
                    }
                }
            }
        }
    }
}
