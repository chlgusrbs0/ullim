package com.ullim.watch.audio

internal enum class PcmSource { UNPROCESSED, VOICE_RECOGNITION }

internal fun <T : PcmInput> openPcmInput(
    supportsUnprocessed: Boolean,
    hasPermission: () -> Boolean,
    open: (PcmSource) -> T,
): T {
    val sources = if (supportsUnprocessed) PcmSource.entries else listOf(PcmSource.VOICE_RECOGNITION)
    var lastFailure: RuntimeException? = null
    for (source in sources) {
        if (!hasPermission()) throw SecurityException("Microphone permission unavailable")
        try { return open(source) }
        catch (failure: RuntimeException) {
            if (failure !is IllegalArgumentException && failure !is UnsupportedOperationException) throw failure
            // Builder.build also uses UnsupportedOperationException for permission loss.
            if (!hasPermission()) throw SecurityException("Microphone permission unavailable", failure)
            lastFailure = failure
        }
    }
    throw IllegalStateException("Microphone initialization failed", lastFailure)
}
