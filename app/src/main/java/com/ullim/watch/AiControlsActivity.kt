package com.ullim.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.ullim.watch.ai.AiFault
import com.ullim.watch.ai.AiPhase
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime

class AiControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val worker = AiRuntime.worker(this)
        setContent {
            val enabled by CaptureRuntime.aiEnabled.collectAsState()
            val snapshot by worker.state.collectAsState()
            val runtime by CaptureRuntime.state.collectAsState()
            MaterialTheme {
                AppScaffold {
                    Column(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.ai_controls), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        val status = when {
                            !enabled -> R.string.ai_disabled
                            snapshot.phase == AiPhase.FAILED -> if (snapshot.fault == AiFault.CLEANUP || snapshot.fault == AiFault.EXECUTOR) R.string.ai_restart_needed else R.string.ai_failed
                            runtime.listening == ListeningState.Idle || runtime.listening is ListeningState.Error -> R.string.ai_waiting_for_capture
                            snapshot.phase == AiPhase.LOADING -> R.string.ai_loading
                            snapshot.phase == AiPhase.DELAYED -> R.string.ai_delayed
                            snapshot.phase == AiPhase.LISTENING -> R.string.ai_listening
                            else -> R.string.ai_waiting_for_capture
                        }
                        Text(stringResource(status), textAlign = TextAlign.Center)
                        if (runtime.listening is ListeningState.Listening && snapshot.phase != AiPhase.LISTENING) {
                            Text(stringResource(R.string.ai_baseline_continues), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.ai_optional_notice), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        if (snapshot.cleanupPending) Text(stringResource(R.string.ai_cleanup_pending), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            val next = !enabled
                            CaptureRuntime.setAiEnabled(next)
                            worker.setEnabled(worker.state.value.sessionId, next)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (enabled) R.string.ai_turn_off else R.string.ai_turn_on))
                        }
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.ai_back)) }
                    }
                }
            }
        }
    }
}
