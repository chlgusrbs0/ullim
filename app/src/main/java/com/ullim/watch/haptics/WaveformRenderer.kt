package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.HapticRenderer
import com.ullim.watch.core.RenderResult
import kotlin.math.roundToInt

interface WaveformDevice {
    val capabilities: HapticCapabilities
    fun play(timings: LongArray, amplitudes: IntArray)
    fun cancel()
}

interface SessionRenderer : HapticRenderer {
    fun begin(sessionId: Long)
    fun renderBatch(frames: List<HapticFrame>): RenderResult
}

class WaveformRenderer(private val device: WaveformDevice) : SessionRenderer {
    private var sessionId = 0L
    private var highestSessionId = 0L
    private var lastTimestampNs: Long? = null
    val strengthLimited: Boolean get() = !device.capabilities.amplitudeControl

    @Synchronized override fun begin(sessionId: Long) {
        require(sessionId > highestSessionId)
        cancel()
        highestSessionId = sessionId
        this.sessionId = sessionId
    }

    override fun render(frame: HapticFrame): RenderResult = renderBatch(listOf(frame))

    @Synchronized override fun renderBatch(frames: List<HapticFrame>): RenderResult {
        val batch = frames.toList()
        if (batch.isEmpty() || batch.size > 50 || batch.sumOf { it.durationMs } > 1000) return RenderResult.FAILED
        if (sessionId == 0L || batch.any { it.sessionId != sessionId } || lastTimestampNs?.let { batch.first().timestampNs <= it } == true) return RenderResult.STALE
        if (batch.zipWithNext().any { (a, b) -> b.timestampNs != a.timestampNs + a.durationMs * 1_000_000L }) return RenderResult.STALE
        lastTimestampNs = batch.last().timestampNs
        if (!device.capabilities.available) return RenderResult.UNSUPPORTED
        return try {
            if (batch.all { it.isSilent }) {
                device.cancel()
                RenderResult.SILENT
            } else {
                val timings = mutableListOf(0L)
                val amplitudes = mutableListOf(0)
                for (frame in batch) {
                    if (strengthLimited) {
                        val pulse = PulseFallback.encode(frame.commandedIntensity, frame.durationMs)
                        timings.addAll(pulse.timings.toList())
                        amplitudes.addAll(pulse.amplitudes.toList())
                    } else {
                        timings.add(frame.durationMs)
                        amplitudes.add(if (frame.isSilent) 0 else (frame.commandedIntensity.coerceAtMost(0.85f) * 255).roundToInt().coerceIn(1, 217))
                    }
                }
                device.play(timings.toLongArray(), amplitudes.toIntArray())
                RenderResult.SENT
            }
        } catch (_: RuntimeException) {
            runCatching { device.cancel() }
            RenderResult.FAILED
        }
    }

    @Synchronized override fun cancel() {
        sessionId = 0
        lastTimestampNs = null
        runCatching { device.cancel() }
    }
}
