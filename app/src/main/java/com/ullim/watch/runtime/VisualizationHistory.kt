package com.ullim.watch.runtime

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.HapticFrame
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

data class VisualizationPoint(
    val sequence: Long,
    val timestampNs: Long,
    val rms: Float,
    val envelope: Float,
    val commanded: Float,
    val durationMs: Long,
)

data class VisualizationSnapshot(
    val startNs: Long = 0,
    val endNs: Long = 0,
    val points: List<VisualizationPoint> = emptyList(),
)

/** Single DSP producer, arbitrary readers. Never stores PCM or calls UI code. */
class VisualizationHistory {
    private class Run(val id: Long) {
        val ring = AtomicReferenceArray<VisualizationPoint?>(CAPACITY)
        val published = AtomicLong(0)
        var lastTimestampNs = -1L // single producer only
    }
    private val current = AtomicReference<Run?>()

    fun begin(id: Long) {
        require(id > 0)
        current.set(Run(id))
    }

    fun end(id: Long) {
        val run = current.get() ?: return
        if (run.id == id) current.compareAndSet(run, null)
    }

    fun offer(input: AudioFeatures, command: HapticFrame) {
        val run = current.get() ?: return
        if (input.sessionId != run.id || command.sessionId != run.id ||
            input.timestampNs != command.timestampNs || input.timestampNs <= run.lastTimestampNs) return
        val sequence = run.published.get() + 1
        val point = VisualizationPoint(sequence, input.timestampNs, input.rms, input.envelope,
            command.commandedIntensity, command.durationMs)
        run.ring.set((sequence % CAPACITY).toInt(), point)
        run.lastTimestampNs = input.timestampNs
        run.published.set(sequence)
    }

    fun snapshot(nowNs: Long): VisualizationSnapshot {
        require(nowNs >= 0)
        val start = maxOf(0, nowNs - WINDOW_NS)
        val run = current.get() ?: return VisualizationSnapshot(start, nowNs)
        val last = run.published.get()
        val points = ArrayList<VisualizationPoint>(CAPACITY)
        for (sequence in maxOf(1, last - CAPACITY + 1)..last) {
            val point = run.ring.get((sequence % CAPACITY).toInt()) ?: continue
            // Concurrent overwrite can only omit a point, never mix two timelines.
            if (point.sequence == sequence && point.timestampNs in start..nowNs) points.add(point)
        }
        if (current.get() !== run) return VisualizationSnapshot(start, nowNs)
        return VisualizationSnapshot(start, nowNs, points.toList())
    }

    companion object {
        const val WINDOW_NS = 5_000_000_000L
        const val CAPACITY = 256
    }
}
