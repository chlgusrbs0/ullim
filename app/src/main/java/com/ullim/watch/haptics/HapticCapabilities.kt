package com.ullim.watch.haptics

data class EnvelopeLimits(val maxPoints: Int, val minPointMs: Long, val maxPointMs: Long, val maxDurationMs: Long)

interface HapticProbe {
    val sdkInt: Int
    fun hasVibrator(): Boolean
    fun hasAmplitudeControl(): Boolean
    fun envelopeLimits(): EnvelopeLimits?
}

data class HapticCapabilities(
    val available: Boolean,
    val amplitudeControl: Boolean,
    val envelope: EnvelopeLimits?,
    val probeFailed: Boolean = false,
) {
    companion object {
        fun detect(probe: HapticProbe): HapticCapabilities = try {
            if (!probe.hasVibrator()) HapticCapabilities(false, false, null)
            else HapticCapabilities(true, probe.hasAmplitudeControl(), if (probe.sdkInt >= 36) probe.envelopeLimits() else null)
        } catch (_: RuntimeException) {
            HapticCapabilities(false, false, null, probeFailed = true)
        }
    }
}

data class DiagnosticWaveform(val timings: LongArray, val amplitudes: IntArray)

enum class DiagnosticPattern {
    WEAK, MEDIUM, STRONG, LONG, RHYTHM;

    fun waveform(amplitudeControl: Boolean): DiagnosticWaveform {
        val strength = when (this) { WEAK -> 60; MEDIUM -> 130; else -> 220 }
        val amplitude = if (amplitudeControl) strength else -1
        return if (this == RHYTHM) {
            DiagnosticWaveform(longArrayOf(0, 70, 100, 70, 250, 70), intArrayOf(0, amplitude, 0, amplitude, 0, amplitude))
        } else {
            DiagnosticWaveform(longArrayOf(0, if (this == LONG) 600 else 100), intArrayOf(0, amplitude))
        }
    }
}
