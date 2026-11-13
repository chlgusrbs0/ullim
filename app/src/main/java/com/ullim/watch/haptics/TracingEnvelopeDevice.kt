package com.ullim.watch.haptics

/** Debug-only caller creates this wrapper. Requests and failures are passed through. */
class TracingEnvelopeDevice(
    private val delegate: EnvelopeDevice,
    private val telemetry: HapticTelemetry,
    private val clockNs: () -> Long = System::nanoTime,
) : EnvelopeDevice {
    override val capabilities: HapticCapabilities get() = delegate.capabilities
    override fun play(timings: LongArray, amplitudes: IntArray) {
        telemetry.recordWaveform(timings, amplitudes, clockNs())
        delegate.play(timings, amplitudes)
    }
    override fun cancel() { telemetry.recordCancel(); delegate.cancel() }
    override fun playEnvelope(plan: EnvelopePlan) { delegate.playEnvelope(plan) }
}
