package com.ullim.watch.haptics

import android.os.Build
import android.os.Vibrator
import androidx.annotation.RequiresApi

class AndroidHapticProbe(private val vibrator: Vibrator) : HapticProbe {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
    override fun hasVibrator(): Boolean = vibrator.hasVibrator()
    override fun hasAmplitudeControl(): Boolean = vibrator.hasAmplitudeControl()

    @RequiresApi(36)
    override fun envelopeLimits(): EnvelopeLimits? {
        if (!vibrator.areEnvelopeEffectsSupported()) return null
        val info = vibrator.envelopeEffectInfo
        return EnvelopeLimits(info.maxSize, info.minControlPointDurationMillis, info.maxControlPointDurationMillis, info.maxDurationMillis)
    }
}
