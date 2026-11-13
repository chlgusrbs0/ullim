package com.ullim.watch

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.Button

class MainActivity : ComponentActivity() {
    private var pendingStart = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (pendingStart) {
            pendingStart = false
            if (result[Manifest.permission.RECORD_AUDIO] == true || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startCapture()
            else CaptureRuntime.fail(null, getString(R.string.microphone_permission_needed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingStart = savedInstanceState?.getBoolean("pendingStart") ?: false
        setContent { UllimApp(::requestCapture, ::stopCapture) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingStart", pendingStart)
        super.onSaveInstanceState(outState)
    }

    private fun requestCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startCapture()
        else {
            pendingStart = true
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun startCapture() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            CaptureRuntime.fail(null, getString(R.string.service_start_failed))
            return
        }
        val token = CaptureRuntime.requestStart() ?: return
        try {
            startForegroundService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_START).putExtra(ListeningService.EXTRA_SESSION, token))
        } catch (_: ForegroundServiceStartNotAllowedException) {
            CaptureRuntime.fail(token, getString(R.string.service_start_failed))
        } catch (_: SecurityException) {
            CaptureRuntime.fail(token, getString(R.string.microphone_permission_needed))
        } catch (_: RuntimeException) {
            CaptureRuntime.fail(token, getString(R.string.service_start_failed))
        }
    }

    private fun stopCapture() {
        if (runCatching { startService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_STOP)) }.isFailure) {
            stopService(Intent(this, ListeningService::class.java))
        }
    }
}

@Composable
private fun UllimApp(onStart: () -> Unit = {}, onStop: () -> Unit = {}) {
    val context = LocalContext.current
    val runtime by CaptureRuntime.state.collectAsState()
    val levels by CaptureRuntime.levels.collectAsState()
    val limited by CaptureRuntime.strengthLimited.collectAsState()
    val phase = runtime.listening
    val idle = phase == ListeningState.Idle || phase is ListeningState.Error
    MaterialTheme {
        AppScaffold {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        text = when (phase) {
                            ListeningState.Idle -> stringResource(R.string.ready_to_listen)
                            is ListeningState.Starting -> stringResource(R.string.starting_listening)
                            is ListeningState.Listening -> stringResource(R.string.active_listening)
                            is ListeningState.Stopping -> stringResource(R.string.stopping_listening)
                            is ListeningState.Error -> phase.reason
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(stringResource(R.string.local_audio_notice), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    if (phase is ListeningState.Listening) {
                        Text(stringResource(R.string.levels, (levels.sound * 100).toInt(), (levels.commanded * 100).toInt()), textAlign = TextAlign.Center)
                        if (limited) Text(stringResource(R.string.amplitude_unsupported), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Text(stringResource(R.string.notifications_limited), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = if (idle) onStart else onStop, enabled = phase !is ListeningState.Stopping, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (idle) R.string.start_listening else R.string.stop))
                    }
                    TextButton(onClick = { context.startActivity(Intent(context, AiControlsActivity::class.java)) }) {
                        Text(stringResource(R.string.ai_controls))
                    }
                    TextButton(onClick = { context.startActivity(Intent(context, DiagnosticsActivity::class.java)) }, enabled = idle) {
                        Text(stringResource(R.string.diagnostics))
                    }
                }
            }
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun UllimAppPreview() {
    UllimApp()
}
