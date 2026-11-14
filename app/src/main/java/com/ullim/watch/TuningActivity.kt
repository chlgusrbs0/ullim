package com.ullim.watch

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.SettingsRuntime
import com.ullim.watch.ui.TuningContent
import com.ullim.watch.ui.theme.UllimTheme

/** Opening this screen changes no capture state. Only the user starts listening. */
class TuningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = SettingsRuntime.get(this)
        setContent {
            val settings by preferences.state.collectAsStateWithLifecycle()
            val runtime by CaptureRuntime.state.collectAsStateWithLifecycle()
            val levels by CaptureRuntime.levels.collectAsStateWithLifecycle()
            UllimTheme {
                TuningContent(settings, runtime.listening, levels,
                    onChange = { p, v -> preferences.setTuning(p, v) },
                    onReset = { preferences.resetTuning() },
                    onRetry = { preferences.retrySave() },
                    onPhone = { startActivity(Intent(this, PhoneTuningActivity::class.java)) },
                    onStop = ::stopCapture, onBack = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    private fun stopCapture() {
        val token = CaptureRuntime.stopToken() ?: return
        if (runCatching {
            startService(Intent(this, ListeningService::class.java).setAction(ListeningService.ACTION_STOP)
                .putExtra(ListeningService.EXTRA_SESSION, token))
        }.isFailure && !runCatching { stopService(Intent(this, ListeningService::class.java)) }.getOrDefault(false)) {
            reportCaptureFailure(this, token, CaptureFailure.SERVICE_COMMAND)
        }
    }
}
