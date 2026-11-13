package com.ullim.watch.probe

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ullim.watch.ListeningService
import com.ullim.watch.ai.AiPhase
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.core.ListeningState
import com.ullim.watch.haptics.MotorCalibrationRunner
import com.ullim.watch.haptics.MotorCalibrationState
import com.ullim.watch.haptics.MotorProfileStore
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.SettingsRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Explicit local verification action only, isolated package; never persists PCM/labels. */
open class NativeSessionProbeActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var run: Job? = null
    private var calibration: MotorCalibrationRunner? = null
    private var resumed = false
    private var focused = false
    private var started = false
    private var ending = false
    private var terminal = false
    private var began = 0L
    private var currentToken = 0L
    private var cycles = 0
    private var totalInferences = 0L
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply {
            text = "감지 반복 시험 준비"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(44, 44, 44, 44)
        }
        setContentView(label)
    }

    override fun onResume() { super.onResume(); resumed = true; maybeStart() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
        if (hasFocus) maybeStart() else if (started && !ending) end("CANCELLED", "FOCUS")
    }
    override fun onPause() {
        resumed = false
        if (started && !ending) end("CANCELLED", "PAUSED")
        super.onPause()
    }
    override fun onDestroy() {
        if (started && !ending) end("CANCELLED", "DESTROYED")
        // Cleanup scope survives Activity teardown until its bounded terminal check.
        if (!started || terminal) scope.cancel()
        super.onDestroy()
    }

    private fun maybeStart() {
        if (started || !resumed || !focused || intent.action != ACTION) return
        started = true
        began = SystemClock.elapsedRealtime()
        if (packageName != "com.ullim.watch.nativeprobe") { immediateFailure("PROBE_PACKAGE"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            immediateFailure("PERMISSION"); return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        run = scope.launch {
            try {
                checkProbe(CaptureRuntime.canStart() && recordings() == 0, "BASELINE_BUSY")
                await(3_000, "SETTINGS_READY") { SettingsRuntime.get(this@NativeSessionProbeActivity).state.value.ready }
                setAi(false)
                calibrate()
                repeat(3) { captureCycle(it + 1) }
                checkProbe(cycles == 3 && totalInferences >= 3, "MISSING_CYCLES")
                end("PASS", "NONE")
            } catch (_: CancellationException) { /* end() owns cleanup and terminal logging. */ }
            catch (failure: ProbeFailure) { end("FAIL", failure.code) }
            catch (_: Exception) { end("FAIL", "UNEXPECTED") }
        }
    }

    private suspend fun calibrate() {
        label.text = "모터 보정 시험 중"
        var state = MotorCalibrationState.IDLE
        calibration = MotorCalibrationRunner(this, getSystemService(VibratorManager::class.java).defaultVibrator,
            { state = it }, onDiagnostic = { Log.i(TAG, "calibration $it") })
        calibration!!.start()
        await(10_000, "CALIBRATION_TIMEOUT") {
            if (state in setOf(MotorCalibrationState.FAILED, MotorCalibrationState.PERMISSION_REQUIRED, MotorCalibrationState.BUSY)) {
                throw ProbeFailure("CALIBRATION_$state")
            }
            state == MotorCalibrationState.COMPLETE && CaptureRuntime.canStart() && recordings() == 0
        }
        checkProbe(MotorProfileStore.load(this, MotorProfileStore.deviceKey(this)) != null, "CALIBRATION_NOT_SAVED")
        calibration = null
        Log.i(TAG, "calibrationSaved=true recordingConfigurations=0")
    }

    private suspend fun captureCycle(number: Int) {
        checkProbe(CaptureRuntime.canStart() && recordings() == 0, "BETWEEN_CYCLES_BUSY")
        setAi(false)
        val token = CaptureRuntime.requestStart() ?: throw ProbeFailure("START_REFUSED")
        currentToken = token
        label.text = "감지 반복 $number / 3"
        checkProbe((1..8).all { CaptureRuntime.requestStart() == null }, "DUPLICATE_ADMITTED")
        startForegroundService(command(ListeningService.ACTION_START, token))
        repeat(3) { startForegroundService(command(ListeningService.ACTION_START, token)) }
        await(5_000, "LISTENING_TIMEOUT") {
            rejectRuntimeError()
            CaptureRuntime.state.value.listening == ListeningState.Listening(token) && recordings() == 1 && notificationCount() == 1
        }
        // A prior/invalid token must not stop the current run.
        startService(command(ListeningService.ACTION_STOP, token - 1))
        val offSequence = hold(3_000, token, false)
        val worker = AiRuntime.worker(this)
        checkProbe(worker.state.value.sessionId == token && worker.state.value.successfulInferences == 0L &&
            !worker.state.value.modelLoaded && !worker.state.value.busy, "OFF_MODEL_USED")
        Log.i(TAG, "cycle=$number phase=OFF session=$token dspSequence=$offSequence recordingConfigurations=1 duplicateRequestsRejected=8 duplicateCommands=3 staleStopIgnored=true")

        setAi(true)
        val onSequence = hold(8_000, token, true)
        val active = worker.state.value
        checkProbe(active.sessionId == token && active.successfulInferences > 0 && active.modelLoaded &&
            active.fault == null && active.phase == AiPhase.LISTENING && onSequence > offSequence, "ON_NOT_VERIFIED")
        val inferences = active.successfulInferences
        totalInferences += inferences
        Log.i(TAG, "cycle=$number phase=ON session=$token dspSequence=$onSequence inferences=$inferences loadNs=${active.loadDurationNs} maxInferenceNs=${active.maxInferenceNs} dropped=${active.droppedWindows} recordingConfigurations=1")

        setAi(false)
        await(8_000, "AI_OFF_CLEANUP") { rejectRuntimeError(); aiClean() }
        val postSequence = hold(2_000, token, false)
        checkProbe(postSequence > onSequence && worker.state.value.successfulInferences == inferences, "OFF_NOT_VERIFIED")
        await(3_000, "SETTINGS_SAVE") { !SettingsRuntime.get(this).state.value.saving }
        checkProbe(SettingsRuntime.get(this).state.value.error == null, "SETTINGS_ERROR")
        startService(command(ListeningService.ACTION_STOP, token))
        repeat(3) { startService(command(ListeningService.ACTION_STOP, token)) }
        await(8_000, "STOP_TIMEOUT") {
            rejectRuntimeError()
            CaptureRuntime.canStart() && CaptureRuntime.state.value.listening == ListeningState.Idle &&
                recordings() == 0 && aiClean() && notificationCount() == 0
        }
        currentToken = 0
        cycles++
        Log.i(TAG, "cycle=$number phase=STOPPED dspSequence=$postSequence recordingConfigurations=0 notificationCount=0 canStart=true modelLoaded=false busy=false cleanupPending=false")
    }

    private suspend fun hold(durationMs: Long, token: Long, enabled: Boolean): Long {
        val until = SystemClock.elapsedRealtime() + durationMs
        var sequence = 0L
        while (SystemClock.elapsedRealtime() < until) {
            rejectRuntimeError()
            checkProbe(resumed && focused && CaptureRuntime.state.value.listening == ListeningState.Listening(token), "LOST_SESSION")
            checkProbe(recordings() == 1, "RECORDING_COUNT")
            checkProbe(notificationCount() == 1, "NOTIFICATION_COUNT")
            val snapshot = AiRuntime.worker(this).state.value
            checkProbe(snapshot.fault == null, "AI_FAULT")
            if (!enabled) checkProbe(!snapshot.modelLoaded && !snapshot.busy && !snapshot.cleanupPending, "OFF_NOT_CLEAN")
            CaptureRuntime.visualization.snapshot(System.nanoTime()).points.lastOrNull()?.let { sequence = it.sequence }
            delay(100)
        }
        checkProbe(sequence > 0, "DSP_NOT_OBSERVED")
        return sequence
    }

    private suspend fun await(timeoutMs: Long, code: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            checkProbe(SystemClock.elapsedRealtime() < until, code)
            delay(50)
        }
    }
    private fun command(action: String, token: Long) = Intent(this, ListeningService::class.java)
        .setAction(action).putExtra(ListeningService.EXTRA_SESSION, token)
    private fun recordings() = getSystemService(AudioManager::class.java).activeRecordingConfigurations.size
    private fun notificationCount() = getSystemService(NotificationManager::class.java).activeNotifications.size
    private fun aiClean(): Boolean = AiRuntime.worker(this).state.value.let {
        !it.modelLoaded && !it.busy && !it.cleanupPending && it.fault == null
    }
    private fun rejectRuntimeError() { checkProbe(CaptureRuntime.state.value.listening !is ListeningState.Error, "RUNTIME_ERROR") }
    private fun setAi(enabled: Boolean) {
        checkProbe(SettingsRuntime.get(this).setAiEnabled(enabled), "SETTINGS_NOT_READY")
        checkProbe(CaptureRuntime.aiEnabled.value == enabled, "SETTINGS_NOT_APPLIED")
    }
    private fun checkProbe(condition: Boolean, code: String) { if (!condition) throw ProbeFailure(code) }

    private fun immediateFailure(code: String) {
        ending = true
        terminal = true
        label.text = "반복 시험을 시작하지 않았어요"
        Log.i(TAG, "result=FAIL code=$code cycles=0 microphoneStarted=false")
    }

    private fun end(requested: String, code: String) {
        if (ending) return
        ending = true
        run?.cancel()
        calibration?.cancel()
        if (!SettingsRuntime.get(this).setAiEnabled(false)) CaptureRuntime.setAiEnabled(false)
        val token = CaptureRuntime.stopToken() ?: currentToken
        if (token > 0) runCatching { startService(command(ListeningService.ACTION_STOP, token)) }
        Log.i(TAG, "requested=$requested code=$code cycles=$cycles cleanupVerified=false")
        scope.launch {
            val clean = try {
                await(8_000, "FINAL_CLEANUP") {
                    CaptureRuntime.canStart() && CaptureRuntime.state.value.listening == ListeningState.Idle &&
                        recordings() == 0 && aiClean() && notificationCount() == 0
                }
                true
            } catch (_: Exception) { false }
            terminal = true
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val result = if (clean) requested else "FAIL"
            label.text = if (result == "PASS") "감지 반복 시험 완료" else "감지 반복 시험 종료"
            Log.i(TAG, "result=$result code=${if (clean) code else "FINAL_CLEANUP"} cycles=$cycles totalInferences=$totalInferences cleanupVerified=$clean elapsedMs=${SystemClock.elapsedRealtime() - began} rawAudioStored=false")
            scope.cancel()
        }
    }

    private class ProbeFailure(val code: String) : Exception()
    companion object {
        const val ACTION = "com.ullim.watch.PROBE_SESSIONS"
        private const val TAG = "UllimSessionProbe"
    }
}
