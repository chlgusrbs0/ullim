package com.ullim.watch.haptics

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

class AndroidEnvelopeDevice(private val vibrator: Vibrator) : EnvelopeDevice {
    private val waveform = AndroidWaveformDevice(vibrator)
    override val capabilities: HapticCapabilities get() = waveform.capabilities

    override fun play(timings: LongArray, amplitudes: IntArray) { waveform.play(timings, amplitudes) }
    override fun cancel() { waveform.cancel() }

    override fun playEnvelope(plan: EnvelopePlan) {
        if (Build.VERSION.SDK_INT < 36) error("Envelope API unavailable")
        check(capabilities.envelope != null)
        val builder = VibrationEffect.BasicEnvelopeBuilder().setInitialSharpness(0.5f)
        plan.points.drop(1).forEach { builder.addControlPoint(it.intensity, 0.5f, it.durationMs) }
        vibrator.vibrate(builder.build(), VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
    }
}
