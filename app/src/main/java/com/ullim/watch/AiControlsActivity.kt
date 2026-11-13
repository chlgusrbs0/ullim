package com.ullim.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import com.ullim.watch.ui.AiModeControl
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.OutlinedButton
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
            val pageName = stringResource(R.string.ai_controls)
            UllimTheme {
                AppScaffold {
                    ScreenScaffold(scrollState = scroll) { contentPadding ->
                        Column(
                            Modifier.padding(contentPadding).fillMaxWidth().verticalScroll(scroll)
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .semantics { paneTitle = pageName; isTraversalGroup = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AiResultContent(display, showOriginal = true)
                            SettingsSaveStatus(settings) { preferences.retrySave() }
                            Text(stringResource(R.string.ai_optional_notice), textAlign = TextAlign.Center)
                            AiModeControl(enabled, settings.ready) {
                                preferences.setAiEnabled(!preferences.state.value.values.aiEnabled)
                            }
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.ai_back), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
