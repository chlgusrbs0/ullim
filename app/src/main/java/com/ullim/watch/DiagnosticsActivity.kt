package com.ullim.watch

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ullim.watch.haptics.AndroidHapticProbe
import com.ullim.watch.haptics.DiagnosticPattern
import com.ullim.watch.haptics.HapticCapabilities
import com.ullim.watch.ui.theme.UllimTheme

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var vibrator: Vibrator
    private var diagnosticMessage by mutableStateOf(R.string.diagnostic_not_measured)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val capability = HapticCapabilities.detect(AndroidHapticProbe(vibrator))
        setContent {
            UllimTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.diagnostics), textAlign = TextAlign.Center)
                    Text("${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", textAlign = TextAlign.Center)
                    Text(stringResource(if (capability.available) R.string.motor_available else R.string.motor_unavailable))
                    Text(stringResource(if (capability.amplitudeControl) R.string.amplitude_supported else R.string.amplitude_unsupported), textAlign = TextAlign.Center)
                    Text(stringResource(if (capability.envelope != null) R.string.envelope_supported else R.string.envelope_unsupported))
                    capability.envelope?.let { limits ->
                        Text(stringResource(R.string.envelope_limits, limits.maxPoints, limits.minPointMs, limits.maxPointMs, limits.maxDurationMs), textAlign = TextAlign.Center)
                    }
                    if (capability.probeFailed) Text(stringResource(R.string.probe_failed))
                    Text(stringResource(diagnosticMessage), textAlign = TextAlign.Center)
                    DiagnosticPattern.entries.forEach { pattern ->
                        val label = when (pattern) {
                            DiagnosticPattern.WEAK -> R.string.diagnostic_weak
                            DiagnosticPattern.MEDIUM -> R.string.diagnostic_medium
                            DiagnosticPattern.STRONG -> R.string.diagnostic_strong
                            DiagnosticPattern.LONG -> R.string.diagnostic_long
                            DiagnosticPattern.RHYTHM -> R.string.diagnostic_rhythm
                        }
                        Button(onClick = { play(pattern, capability) }, enabled = capability.available, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(label))
                        }
                    }
                    Button(onClick = { cancelOutput() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop)) }
                }
            }
        }
    }

    private fun play(pattern: DiagnosticPattern, capability: HapticCapabilities) {
        diagnosticMessage = try {
            val waveform = pattern.waveform(capability.amplitudeControl)
            vibrator.cancel()
            vibrator.vibrate(VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, -1))
            R.string.command_sent
        } catch (_: RuntimeException) {
            cancelOutput()
            R.string.output_failed
        }
    }

    private fun cancelOutput() {
        runCatching { vibrator.cancel() }
        diagnosticMessage = R.string.output_stopped
    }

    override fun onStop() {
        cancelOutput()
        super.onStop()
    }
}
