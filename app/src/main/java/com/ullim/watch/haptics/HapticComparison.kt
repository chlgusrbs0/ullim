package com.ullim.watch.haptics

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

enum class ComparisonPattern(val calls: Int, val segments: Int, val totalMs: Long = 600, val overlapMs: Long = 0) {
    SINGLE(1, 1), SEGMENTS(1, 30), CHUNKS_60(10, 1), CHUNKS_200(3, 1),
    STRENGTH_RAMP(1, 3, 1800), CHUNKS_OVERLAP_60(10, 1, 600, 60),
    MIN_AMPLITUDE(1, 1), MAX_AMPLITUDE(1, 1);
}

enum class ComparisonPhase { READY, RUNNING, COMPLETE, STOPPED, LATE, FAILED, UNSUPPORTED }
data class ComparisonState(
    val pattern: ComparisonPattern? = null,
    val phase: ComparisonPhase = ComparisonPhase.READY,
    val commands: Int = 0,
    val maxDispatchLateMs: Long = 0,
)

/** Main-thread-only, finite synthetic output. Dispatch timing is not physical motor latency. */
class HapticComparison(
    private val device: WaveformDevice,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val clockMs: () -> Long = SystemClock::uptimeMillis,
    private val onUpdate: (ComparisonState) -> Unit = {},
) {
    private var generation = 0L
    private var pending: Runnable? = null
    private var state = ComparisonState()

    fun start(pattern: ComparisonPattern) {
        stop()
        val token = generation
        val startedMs = clockMs()
        state = ComparisonState(pattern, ComparisonPhase.RUNNING)
        if (!device.capabilities.available || !device.capabilities.amplitudeControl) {
            state = state.copy(phase = ComparisonPhase.UNSUPPORTED)
            onUpdate(state)
            return
        }
        val callDurationMs = pattern.totalMs / pattern.calls
        fun dispatch(index: Int) {
            if (token != generation) return
            pending = null
            if (index == pattern.calls) {
                state = state.copy(phase = ComparisonPhase.COMPLETE)
                onUpdate(state)
                return // Let the last finite effect finish; do not truncate it with cancel().
            }
            val lateness = (clockMs() - startedMs - index * callDurationMs).coerceAtLeast(0)
            state = state.copy(maxDispatchLateMs = maxOf(state.maxDispatchLateMs, lateness))
            if (lateness > 40) {
                runCatching { device.cancel() }
                state = state.copy(phase = ComparisonPhase.LATE)
                onUpdate(state)
                return
            }
            try {
                val amplitudes = when (pattern) {
                    ComparisonPattern.STRENGTH_RAMP -> intArrayOf(60, 130, 220)
                    ComparisonPattern.MIN_AMPLITUDE -> intArrayOf(1)
                    ComparisonPattern.MAX_AMPLITUDE -> intArrayOf(255)
                    else -> IntArray(pattern.segments) { 220 }
                }
                // Only synthetic comparison F extends a request into the following interval.
                // The last request has no tail; nothing is scheduled beyond the finite trial.
                val playDurationMs = callDurationMs + minOf(pattern.overlapMs,
                    (pattern.calls - index - 1) * callDurationMs)
                device.play(LongArray(pattern.segments) { playDurationMs / pattern.segments }, amplitudes)
            } catch (_: RuntimeException) {
                runCatching { device.cancel() }
                state = state.copy(phase = ComparisonPhase.FAILED)
                onUpdate(state)
                return
            }
            state = state.copy(commands = state.commands + 1)
            onUpdate(state)
            val next = Runnable { dispatch(index + 1) }
            pending = next
            handler.postDelayed(next, (startedMs + (index + 1) * callDurationMs - clockMs()).coerceAtLeast(0))
        }
        dispatch(0)
    }

    fun stop() {
        generation++
        pending?.let(handler::removeCallbacks)
        pending = null
        runCatching { device.cancel() }
        state = state.copy(phase = ComparisonPhase.STOPPED)
        onUpdate(state)
    }
}
