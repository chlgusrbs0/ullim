package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import kotlin.math.roundToLong

object PulseFallback {
    private fun onTime(intensity: Float, durationMs: Long): Long =
        if (intensity == 0f) 0 else maxOf(minOf(4L, durationMs), (durationMs * intensity).roundToLong()).coerceAtMost(durationMs)

    fun encode(intensity: Float, durationMs: Long): DiagnosticWaveform {
        require(intensity.isFinite() && intensity in 0f..1f)
        require(durationMs in 1..1000)
        if (intensity == 0f) return DiagnosticWaveform(longArrayOf(durationMs), intArrayOf(0))
        val onMs = onTime(intensity, durationMs)
        return DiagnosticWaveform(longArrayOf(0, onMs, durationMs - onMs), intArrayOf(0, -1, 0))
    }

    /** Redistribute ON time only inside similar, already-buffered live frames. */
    fun encodeBatch(frames: List<HapticFrame>): DiagnosticWaveform {
        require(frames.isNotEmpty() && frames.size <= 50 && frames.sumOf { it.durationMs } <= 1000)
        val timings = mutableListOf(0L)
        val amplitudes = mutableListOf(0)
        fun append(durationMs: Long, amplitude: Int) {
            if (durationMs == 0L) return
            if (amplitudes.last() == amplitude) timings[timings.lastIndex] += durationMs
            else { timings.add(durationMs); amplitudes.add(amplitude) }
        }
        var start = 0
        while (start < frames.size) {
            val first = frames[start]
            val onTimes = mutableListOf(onTime(first.commandedIntensity, first.durationMs))
            var durationMs = first.durationMs
            var end = start + 1
            var lowestLevel = first.commandedIntensity
            if (onTimes.first() > 0 && first.durationMs == 20L) {
                var minimum = onTimes.first()
                var maximum = onTimes.first()
                while (end < frames.size && durationMs < 60L) {
                    val next = frames[end]
                    if (next.durationMs != 20L) break
                    val nextOn = onTime(next.commandedIntensity, next.durationMs)
                    val nextMin = minOf(minimum, nextOn)
                    val nextMax = maxOf(maximum, nextOn)
                    // At most 10 percentage points apart. Never cross real silence
                    // or flatten a distinct strength change into one long pulse.
                    if (nextOn == 0L || nextMax - nextMin > 2L) break
                    minimum = nextMin; maximum = nextMax
                    onTimes.add(nextOn); durationMs += next.durationMs
                    lowestLevel = minOf(lowestLevel, next.commandedIntensity)
                    end++
                }
            }
            // Below 75% retain the original short pulses. Transfer ON budget
            // from later pulses to the first one gradually, reaching the long
            // pulse at 85%. No extra time, amplitude or future frames needed.
            val blend = ((lowestLevel - .75f) / .10f).coerceIn(0f, 1f)
            val transferred = onTimes.mapIndexed { index, on -> if (index == 0) 0L else (on * blend).roundToLong() }
            for (index in onTimes.indices) {
                val on = onTimes[index] + if (index == 0) transferred.sum() else -transferred[index]
                append(on, -1)
                append(frames[start + index].durationMs - onTimes[index], 0)
            }
            start = end
        }
        return DiagnosticWaveform(timings.toLongArray(), amplitudes.toIntArray())
    }
}
