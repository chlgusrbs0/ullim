package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.RenderResult

class HapticScheduler(
    private val renderer: SessionRenderer,
    private val clockNs: () -> Long = System::nanoTime,
    private val chunkMs: Long = 60,
    private val maxLateMs: Long = 200,
) {
    private val pending = mutableListOf<HapticFrame>()
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null
    @Volatile var lastResult = RenderResult.SILENT
        private set

    init {
        require(chunkMs in 20..200 && chunkMs % 20 == 0L)
        require(maxLateMs in 20..500)
    }

    @Synchronized fun begin(sessionId: Long) {
        renderer.begin(sessionId)
        this.sessionId = sessionId
        pending.clear()
        lastTimestampNs = null
        lastResult = RenderResult.SILENT
    }

    @Synchronized fun offer(frame: HapticFrame): Boolean {
        if (sessionId == 0L || frame.sessionId != sessionId || frame.durationMs != 20L ||
            lastTimestampNs?.let { frame.timestampNs <= it } == true
        ) return false
        val ageNs = clockNs() - frame.timestampNs
        if (ageNs < -20_000_000L) return false
        if (ageNs > maxLateMs * 1_000_000L) {
            pending.clear()
            lastTimestampNs = frame.timestampNs
            lastResult = renderer.render(frame.copy(commandedIntensity = 0f))
            return false
        }
        if (lastTimestampNs?.let { frame.timestampNs != it + 20_000_000L } == true) pending.clear()
        lastTimestampNs = frame.timestampNs
        pending.add(frame)
        // Keep silence on the same chunk timeline: an early flush would replace
        // the previous waveform before all of its audible frames have played.
        if (pending.size * 20L >= chunkMs) {
            lastResult = renderer.renderBatch(pending.toList())
            pending.clear()
            return lastResult != RenderResult.FAILED && lastResult != RenderResult.UNSUPPORTED
        }
        return true
    }

    @Synchronized fun stop() {
        sessionId = 0
        pending.clear()
        lastTimestampNs = null
        renderer.cancel()
        lastResult = RenderResult.SILENT
    }
}
