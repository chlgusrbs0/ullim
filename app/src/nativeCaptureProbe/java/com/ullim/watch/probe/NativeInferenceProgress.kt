package com.ullim.watch.probe

internal class NativeInferenceProgress(private val successes: () -> Long) {
    private var previous = successes()
    fun checkpoint() {
        val current = successes()
        check(current > previous) { "INFERENCE_PROGRESS_STOPPED" }
        previous = current
    }
}
