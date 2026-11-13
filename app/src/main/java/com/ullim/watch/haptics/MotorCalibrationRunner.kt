package com.ullim.watch.haptics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import com.ullim.watch.audio.AndroidPcmInput
import com.ullim.watch.audio.CaptureError
import com.ullim.watch.audio.CaptureStatus
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime

enum class MotorCalibrationState { IDLE, RUNNING, STOPPING, COMPLETE, STOPPED, FAILED, PERMISSION_REQUIRED, BUSY }

/** Activity-visible, explicit user action only; shares the existing single-mic lease. */
class MotorCalibrationRunner(
    private val context: Context,
    vibrator: Vibrator,
    private val onUpdate: (MotorCalibrationState) -> Unit,
    private val sourceFactory: () -> PcmInput = { AndroidPcmInput.create(context) },
    private val clockNs: () -> Long = System::nanoTime,
    private val device: WaveformDevice = AndroidWaveformDevice(vibrator),
    private val onDiagnostic: (String) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val timers = Handler(Looper.getMainLooper())
    private var capture: MicrophoneCapture? = null
    private var token = 0L
    @Volatile private var accumulator: MotorCalibration? = null
    private var pending: MotorNoiseProfile? = null
    private var endState = MotorCalibrationState.STOPPED
    private var stopping = false
    private var firmwareKey = ""
    private var pulseFallback = false

    fun start() {
        if (capture != null) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onUpdate(MotorCalibrationState.PERMISSION_REQUIRED); return
        }
        if (!device.capabilities.available) { onUpdate(MotorCalibrationState.FAILED); return }
        val id = CaptureRuntime.requestStart() ?: run { onUpdate(MotorCalibrationState.BUSY); return }
        token = id
        firmwareKey = MotorProfileStore.deviceKey(context)
        pulseFallback = !device.capabilities.amplitudeControl ||
            MotorVibrationPolicy.requiresPulseFallback(Build.MODEL, firmwareKey)
        onDiagnostic("start pulse=$pulseFallback amplitudeControl=${device.capabilities.amplitudeControl}")
        stopping = false
        pending = null
        accumulator = null
        endState = MotorCalibrationState.STOPPED
        lateinit var attempt: MicrophoneCapture
        attempt = MicrophoneCapture(sourceFactory, { accumulator?.accept(it) }, { status ->
            handler.post {
                when (status) {
                    CaptureStatus.Recording -> if (token == id && !stopping) {
                        CaptureRuntime.listening(id, !device.capabilities.amplitudeControl)
                        accumulator = MotorCalibration(id, clockNs(), pulseFallback)
                        onUpdate(MotorCalibrationState.RUNNING)
                        timers.postDelayed({ playPhase(id, 0, 1) }, 500)
                        timers.postDelayed({ playPhase(id, 1, 255) }, 1500)
                        timers.postDelayed({
                            if (token == id) {
                                pending = accumulator?.finish()
                                onDiagnostic(accumulator?.diagnostics() ?: "assessment=NO_ACCUMULATOR")
                                endState = if (pending == null) MotorCalibrationState.FAILED else MotorCalibrationState.COMPLETE
                                stopNative()
                            }
                        }, 2800)
                    }
                    CaptureStatus.Stopped, is CaptureStatus.Failed -> {
                        val cleanupFailed = status is CaptureStatus.Failed && status.reason == CaptureError.CLEANUP
                        if (!cleanupFailed) CaptureRuntime.detach(attempt)
                        if (status is CaptureStatus.Failed) CaptureRuntime.fail(id, "진동 보정을 완료하지 못했어요",
                            if (cleanupFailed) CaptureFailure.CLEANUP else CaptureFailure.from(status.reason))
                        CaptureRuntime.finished(id)
                        if (token == id) {
                            timers.removeCallbacksAndMessages(null)
                            runCatching { device.cancel() }
                            val profile = pending
                            val success = status == CaptureStatus.Stopped && endState == MotorCalibrationState.COMPLETE && profile != null &&
                                MotorProfileStore.deviceKey(context) == firmwareKey && MotorProfileStore.save(context, firmwareKey, profile)
                            onDiagnostic("terminal=$status end=$endState saved=$success")
                            val finalState = if (success) MotorCalibrationState.COMPLETE
                                else if (status is CaptureStatus.Failed || endState == MotorCalibrationState.COMPLETE) MotorCalibrationState.FAILED else endState
                            pending = null; accumulator = null; capture = null; token = 0
                            onUpdate(finalState)
                        }
                    }
                    else -> Unit
                }
            }
        }, clockNs)
        if (!CaptureRuntime.attach(id, attempt)) {
            CaptureRuntime.finished(id)
            token = 0
            onUpdate(MotorCalibrationState.BUSY)
            return
        }
        capture = attempt
        onUpdate(MotorCalibrationState.RUNNING)
        attempt.start(id)
    }

    private fun playPhase(id: Long, index: Int, amplitude: Int) {
        if (token != id || capture == null || stopping) return
        val startedNs = clockNs()
        accumulator?.recordPhase(index, startedNs)
        val waveform = if (pulseFallback) {
            val part = PulseFallback.encode(if (index == 0) .2f else .85f, 20)
            DiagnosticWaveform(LongArray(part.timings.size * 3) { part.timings[it % part.timings.size] },
                IntArray(part.amplitudes.size * 3) { part.amplitudes[it % part.amplitudes.size] })
        } else DiagnosticWaveform(longArrayOf(0, 60), intArrayOf(0, if (device.capabilities.amplitudeControl) amplitude else -1))
        fun pulse(call: Int) {
            if (token != id || capture == null || stopping || call >= 10) return
            val dueNs = startedNs + call * 60_000_000L
            if (clockNs() - dueNs > 40_000_000L) {
                onDiagnostic("assessment=LATE phase=$index call=$call lateNs=${clockNs() - dueNs}")
                endState = MotorCalibrationState.FAILED; pending = null; stopNative(); return
            }
            try {
                // Match live replacement noise, including re-start edges, rather than
                // calibrating only the smoother single-call diagnostic waveform.
                device.play(waveform.timings, waveform.amplitudes)
                if (call < 9) timers.postDelayed({ pulse(call + 1) },
                    ((startedNs + (call + 1) * 60_000_000L - clockNs()).coerceAtLeast(0) / 1_000_000))
            } catch (_: RuntimeException) { endState = MotorCalibrationState.FAILED; pending = null; stopNative() }
        }
        pulse(0)
    }

    fun cancel() {
        if (capture == null) return
        pending = null
        endState = MotorCalibrationState.STOPPED
        stopNative()
    }

    private fun stopNative() {
        stopping = true
        timers.removeCallbacksAndMessages(null)
        runCatching { device.cancel() }
        if (token > 0) CaptureRuntime.stopping(token)
        capture?.stop()
        onUpdate(MotorCalibrationState.STOPPING)
    }
}
