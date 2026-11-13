package com.ullim.watch

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ullim.watch.core.ListeningState
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.VisualizationSnapshot
import com.ullim.watch.ui.ListeningUiState
import com.ullim.watch.ui.UllimApp
import com.ullim.watch.ui.collectAiDisplay
import com.ullim.watch.settings.SettingsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingStart = false
    private var microphoneGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
        if (pendingStart) {
            pendingStart = false
            if (microphoneGranted) startCapture()
            else reportCaptureFailure(this, null, CaptureFailure.MIC_PERMISSION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingStart = savedInstanceState?.getBoolean("pendingStart") ?: false
        refreshPermissions()
        val aiWorker = AiRuntime.worker(this)
        val preferences = SettingsRuntime.get(this)
        setContent {
            val runtime by CaptureRuntime.state.collectAsStateWithLifecycle()
            val startAllowed by CaptureRuntime.startAllowed.collectAsStateWithLifecycle()
            val levels by CaptureRuntime.levels.collectAsStateWithLifecycle()
            val limited by CaptureRuntime.strengthLimited.collectAsStateWithLifecycle()
            val settings by preferences.state.collectAsStateWithLifecycle()
            val failure by CaptureRuntime.failure.collectAsStateWithLifecycle()
            val ai = collectAiDisplay(CaptureRuntime.aiEnabled, CaptureRuntime.state, aiWorker.state)
            val uiLifecycle = LocalLifecycleOwner.current.lifecycle
            val visualization by produceState(VisualizationSnapshot(), runtime.listening, uiLifecycle) {
                value = VisualizationSnapshot()
                if (runtime.listening is ListeningState.Listening) {
                    uiLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (isActive) {
                            value = withContext(Dispatchers.Default) {
                                CaptureRuntime.visualization.snapshot(System.nanoTime())
                            }
                            delay(100)
                        }
                    }
                }
            }
            UllimApp(
                state = ListeningUiState(runtime, startAllowed, levels, limited, microphoneGranted, notificationsGranted, visualization, ai, settings, failure),
                onStart = ::requestCapture,
                onStop = ::stopCapture,
                onAiSettings = { startActivity(Intent(this, AiControlsActivity::class.java)) },
                onDiagnostics = {
                    if (CaptureRuntime.canStart()) startActivity(Intent(this, DiagnosticsActivity::class.java))
                },
                onAppSettings = ::openAppSettings,
                onSensitivity = { preferences.setSensitivity(it) },
                onAiToggle = { preferences.setAiEnabled(!preferences.state.value.values.aiEnabled) },
                onSettingsReset = { preferences.reset() },
                onSettingsRetry = { preferences.retrySave() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!microphoneGranted) {
            val token = CaptureRuntime.stopToken()
            if (token != null) {
                reportCaptureFailure(this, token, CaptureFailure.MIC_PERMISSION)
                stopCapture()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pendingStart", pendingStart)
        super.onSaveInstanceState(outState)
    }

    private fun requestCapture() {
        if (pendingStart || !CaptureRuntime.canStart()) return
        refreshPermissions()
        if (microphoneGranted) startCapture()
        else {
            pendingStart = true
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun startCapture() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            reportCaptureFailure(this, null, CaptureFailure.FOREGROUND_START)
            return
        }
        val token = CaptureRuntime.requestStart() ?: return
        try {
            startForegroundService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_START).putExtra(ListeningService.EXTRA_SESSION, token))
        } catch (_: ForegroundServiceStartNotAllowedException) {
            reportCaptureFailure(this, token, CaptureFailure.FOREGROUND_START)
        } catch (_: SecurityException) {
            reportCaptureFailure(this, token, CaptureFailure.MIC_PERMISSION)
        } catch (_: RuntimeException) {
            reportCaptureFailure(this, token, CaptureFailure.FOREGROUND_START)
        }
    }

    private fun stopCapture() {
        val token = CaptureRuntime.stopToken() ?: return
        if (runCatching {
            startService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_STOP)
                .putExtra(ListeningService.EXTRA_SESSION, token))
        }.isFailure) {
            if (runCatching { stopService(Intent(this, ListeningService::class.java)) }.getOrDefault(false).not()) {
                reportCaptureFailure(this, token, CaptureFailure.SERVICE_COMMAND)
            }
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }
}
