package com.ullim.watch

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import com.ullim.watch.ui.theme.UllimTheme
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ullim.watch.haptics.AndroidHapticProbe
import com.ullim.watch.haptics.DiagnosticPattern
import com.ullim.watch.haptics.HapticCapabilities
import com.ullim.watch.haptics.AndroidWaveformDevice
import com.ullim.watch.haptics.ComparisonPattern
import com.ullim.watch.haptics.ComparisonPhase
import com.ullim.watch.haptics.ComparisonState
import com.ullim.watch.haptics.HapticComparison

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var vibrator: Vibrator
    private var diagnosticMessage by mutableStateOf(R.string.diagnostic_not_measured)
    private lateinit var comparison: HapticComparison
    private var comparisonState by mutableStateOf(ComparisonState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val capability = HapticCapabilities.detect(AndroidHapticProbe(vibrator))
        comparison = HapticComparison(AndroidWaveformDevice(vibrator), onUpdate = {
            comparisonState = it
            Log.i("UllimHaptics", "pattern=${it.pattern} phase=${it.phase} commands=${it.commands} maxDispatchLateMs=${it.maxDispatchLateMs}")
        })
        setContent {
            val pageName = stringResource(R.string.diagnostics)
            UllimTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp)
                        .semantics { paneTitle = pageName },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(pageName, Modifier.semantics { heading() }, textAlign = TextAlign.Center)
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
                        Button(onClick = { play(pattern, capability) }, enabled = capability.available, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(label))
                        }
                    }
                    Button(onClick = { cancelOutput() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.stop)) }
                    Text(stringResource(R.string.comparison_title), Modifier.semantics { heading() }, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.comparison_notice), textAlign = TextAlign.Center)
                    val status = when (comparisonState.phase) {
                        ComparisonPhase.READY -> R.string.comparison_ready
                        ComparisonPhase.RUNNING -> R.string.comparison_running
                        ComparisonPhase.COMPLETE -> R.string.comparison_complete
                        ComparisonPhase.STOPPED -> R.string.output_stopped
                        ComparisonPhase.LATE -> R.string.comparison_late
                        ComparisonPhase.FAILED -> R.string.output_failed
                        ComparisonPhase.UNSUPPORTED -> R.string.comparison_unsupported
                    }
                    Text(stringResource(status), textAlign = TextAlign.Center)
                    Text(stringResource(R.string.comparison_commands, comparisonState.commands,
                        comparisonState.maxDispatchLateMs), textAlign = TextAlign.Center)
                    ComparisonPattern.entries.forEach { pattern ->
                        val label = when (pattern) {
                            ComparisonPattern.SINGLE -> R.string.comparison_single
                            ComparisonPattern.SEGMENTS -> R.string.comparison_segments
                            ComparisonPattern.CHUNKS_60 -> R.string.comparison_chunks_60
                            ComparisonPattern.CHUNKS_200 -> R.string.comparison_chunks_200
                            ComparisonPattern.STRENGTH_RAMP -> R.string.comparison_strength_ramp
                            ComparisonPattern.CHUNKS_OVERLAP_60 -> R.string.comparison_overlap
                        }
                        Button(onClick = { comparison.start(pattern) },
                            enabled = capability.available && capability.amplitudeControl,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(label), textAlign = TextAlign.Center)
                        }
                    }
                    Button(onClick = { cancelOutput() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.stop)) }
                }
            }
        }
    }

    private fun play(pattern: DiagnosticPattern, capability: HapticCapabilities) {
        comparison.stop()
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
        if (::comparison.isInitialized) comparison.stop()
        runCatching { vibrator.cancel() }
        diagnosticMessage = R.string.output_stopped
    }

    override fun onStop() {
        cancelOutput()
        super.onStop()
    }
}
