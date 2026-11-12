package com.ullim.watch.haptics

import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.RenderResult

data class EnvelopePoint(val intensity: Float, val durationMs: Long)
class EnvelopePlan private constructor(val points: List<EnvelopePoint>) {
    companion object {
        fun create(frame: HapticFrame, limits: EnvelopeLimits): EnvelopePlan? {
            if (limits.maxPoints < 2 || limits.minPointMs <= 0 || limits.maxPointMs < limits.minPointMs || frame.durationMs > limits.maxDurationMs) return null
            val riseMs = frame.durationMs - limits.minPointMs
            if (riseMs !in limits.minPointMs..limits.maxPointMs) return null
            return EnvelopePlan(listOf(
                EnvelopePoint(0f, 0),
                EnvelopePoint(frame.commandedIntensity.coerceAtMost(0.85f), riseMs),
                EnvelopePoint(0f, limits.minPointMs),
            ))
        }
    }
}

interface EnvelopeDevice : WaveformDevice {
    fun playEnvelope(plan: EnvelopePlan)
}

class AdaptiveHapticRenderer(
    private val device: EnvelopeDevice,
    private val preferEnvelope: Boolean = false,
) : SessionRenderer {
    private val waveform = WaveformRenderer(device)
    private var envelopeEnabled = false
    private var sessionId = 0L
    private var lastTimestampNs: Long? = null

    @Synchronized override fun begin(sessionId: Long) {
        waveform.begin(sessionId)
        this.sessionId = sessionId
        lastTimestampNs = null
        envelopeEnabled = preferEnvelope && device.capabilities.envelope != null
    }

    @Synchronized override fun render(frame: HapticFrame): RenderResult {
        if (sessionId == 0L || frame.sessionId != sessionId || lastTimestampNs?.let { frame.timestampNs <= it } == true) return RenderResult.STALE
        lastTimestampNs = frame.timestampNs
        val plan = if (envelopeEnabled && !frame.isSilent) device.capabilities.envelope?.let { EnvelopePlan.create(frame, it) } else null
        if (plan != null) {
            try {
                device.playEnvelope(plan)
                return RenderResult.SENT
            } catch (_: RuntimeException) {
                envelopeEnabled = false
                runCatching { device.cancel() }
            }
        }
        return waveform.render(frame)
    }

    @Synchronized override fun cancel() {
        sessionId = 0
        lastTimestampNs = null
        envelopeEnabled = false
        waveform.cancel()
    }
}
