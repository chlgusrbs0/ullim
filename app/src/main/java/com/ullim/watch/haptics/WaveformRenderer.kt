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

    @Synchronized override fun render(frame: HapticFrame): RenderResult {
        if (sessionId == 0L || frame.sessionId != sessionId || lastTimestampNs?.let { frame.timestampNs <= it } == true) return RenderResult.STALE
        lastTimestampNs = frame.timestampNs
        if (!device.capabilities.available) return RenderResult.UNSUPPORTED
        return try {
            if (frame.isSilent) {
                device.cancel()
                RenderResult.SILENT
            } else {
                if (strengthLimited) {
                    val pulse = PulseFallback.encode(frame.commandedIntensity, frame.durationMs)
                    device.play(pulse.timings, pulse.amplitudes)
                } else {
                    val amplitude = (frame.commandedIntensity.coerceAtMost(0.85f) * 255).roundToInt().coerceIn(1, 217)
                    device.play(longArrayOf(0, frame.durationMs), intArrayOf(0, amplitude))
                }
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
