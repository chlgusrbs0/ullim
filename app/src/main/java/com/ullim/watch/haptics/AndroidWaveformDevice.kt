package com.ullim.watch.haptics

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

class AndroidWaveformDevice(private val vibrator: Vibrator) : WaveformDevice {
    override val capabilities = HapticCapabilities.detect(AndroidHapticProbe(vibrator))
    private val attributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()

    override fun play(timings: LongArray, amplitudes: IntArray) {
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), attributes)
    }

    override fun cancel() { vibrator.cancel() }
}
