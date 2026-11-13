package com.ullim.watch

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.ui.ListeningUiState
import com.ullim.watch.ui.UllimApp

class MainActivity : ComponentActivity() {
    private var pendingStart = false
    private var microphoneGranted by mutableStateOf(false)
    private var notificationsGranted by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(true)
    private var microphonePermissionPermanentlyDenied by mutableStateOf(false)

    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
    }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
        if (pendingStart) {
            pendingStart = false
            if (microphoneGranted) startCapture()
            else CaptureRuntime.fail(null, getString(R.string.microphone_permission_needed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingStart = savedInstanceState?.getBoolean(STATE_PENDING_START) ?: false
        showOnboarding = !preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        refreshPermissions()
        setContent {
            val runtime by CaptureRuntime.state.collectAsStateWithLifecycle()
            val startAllowed by CaptureRuntime.startAllowed.collectAsStateWithLifecycle()
            val levels by CaptureRuntime.levels.collectAsStateWithLifecycle()
            val limited by CaptureRuntime.strengthLimited.collectAsStateWithLifecycle()
            UllimApp(
                state = ListeningUiState(
                    runtime = runtime,
                    startAllowed = startAllowed,
                    levels = levels,
                    strengthLimited = limited,
                    microphoneGranted = microphoneGranted,
                    notificationsGranted = notificationsGranted,
                    showOnboarding = showOnboarding,
                    microphonePermissionPermanentlyDenied = microphonePermissionPermanentlyDenied,
                ),
                onStart = ::requestCapture,
                onStop = ::stopCapture,
                onAiSettings = { startActivity(Intent(this, AiControlsActivity::class.java)) },
                onDiagnostics = {
                    if (CaptureRuntime.canStart()) startActivity(Intent(this, DiagnosticsActivity::class.java))
                },
                onAppSettings = ::openAppSettings,
                onCompleteOnboarding = ::completeOnboarding,
                onRequestMicrophone = ::requestMicrophonePermission,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun completeOnboarding() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        showOnboarding = false
    }

    private fun refreshPermissions() {
        microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val requestedBefore = preferences.getBoolean(KEY_MICROPHONE_REQUESTED, false)
        microphonePermissionPermanentlyDenied = !microphoneGranted && requestedBefore &&
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestMicrophonePermission() {
        refreshPermissions()
        if (microphoneGranted) return
        if (microphonePermissionPermanentlyDenied) {
            openAppSettings()
            return
        }
        preferences.edit().putBoolean(KEY_MICROPHONE_REQUESTED, true).apply()
        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_START, pendingStart)
        super.onSaveInstanceState(outState)
    }

    private fun requestCapture() {
        if (pendingStart || !CaptureRuntime.canStart()) return
        refreshPermissions()
        if (!microphoneGranted) {
            requestMicrophonePermission()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            pendingStart = true
            permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            return
        }
        startCapture()
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
        val token = when (val phase = CaptureRuntime.state.value.listening) {
            is ListeningState.Starting -> phase.sessionId
            is ListeningState.Listening -> phase.sessionId
            is ListeningState.Stopping -> phase.sessionId
            else -> return
        }
        if (runCatching {
            startService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_STOP)
                .putExtra(ListeningService.EXTRA_SESSION, token))
        }.isFailure) {
            stopService(Intent(this, ListeningService::class.java))
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "ullim_first_run"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_MICROPHONE_REQUESTED = "microphone_permission_requested"
        private const val STATE_PENDING_START = "pendingStart"
    }
}
