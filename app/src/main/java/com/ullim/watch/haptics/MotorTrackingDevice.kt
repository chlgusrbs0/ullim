package com.ullim.watch.haptics

/** Tracks successful output on the same monotonic clock as the microphone. */
class MotorTrackingDevice(
    private val delegate: EnvelopeDevice,
    private val timeline: MotorOutputTimeline,
    private val clockNs: () -> Long = System::nanoTime,
) : EnvelopeDevice {
    override val capabilities: HapticCapabilities get() = delegate.capabilities
    override fun play(timings: LongArray, amplitudes: IntArray) {
        val now = clockNs()
        delegate.play(timings, amplitudes)
        timeline.record(timings, amplitudes, now)
    }
    override fun cancel() {
        val now = clockNs()
        try { delegate.cancel() } finally { timeline.cancel(now) }
    }
    override fun playEnvelope(plan: EnvelopePlan) {
        val now = clockNs()
        delegate.playEnvelope(plan)
        timeline.record(plan.points.map { it.durationMs }.toLongArray(),
            plan.points.map { (it.intensity * 255).toInt() }.toIntArray(), now)
    }
}
