package com.ullim.watch.haptics

import kotlin.math.roundToLong

object PulseFallback {
    fun encode(intensity: Float, durationMs: Long): DiagnosticWaveform {
        require(intensity.isFinite() && intensity in 0f..1f)
        require(durationMs in 1..1000)
        if (intensity == 0f) return DiagnosticWaveform(longArrayOf(durationMs), intArrayOf(0))
        val onMs = maxOf(minOf(4L, durationMs), (durationMs * intensity).roundToLong()).coerceAtMost(durationMs)
        return DiagnosticWaveform(longArrayOf(0, onMs, durationMs - onMs), intArrayOf(0, -1, 0))
    }
}
