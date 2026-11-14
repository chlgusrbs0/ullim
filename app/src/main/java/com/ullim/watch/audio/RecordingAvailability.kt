package com.ullim.watch.audio

import java.util.concurrent.atomic.AtomicReference

/** Framework metadata only; a buffer of zeros is never treated as contention. */
internal class RecordingAvailability {
    private val silenced = AtomicReference<Boolean?>(null)
    fun initialize(value: Boolean) { silenced.compareAndSet(null, value) }
    fun update(value: Boolean) { silenced.set(value) }
    fun checkReadable() {
        if (silenced.get() == true) throw CaptureException(CaptureError.SILENCED)
    }
}
