package com.ullim.watch.haptics

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread

/** Main/control-thread ownership; callbacks never run on the audio thread. */
interface MotorSettingsObservation : AutoCloseable {
    fun start(): Boolean
}

@MainThread
class MotorSettingsWatcher(
    context: Context,
    private val expectedKey: String,
    private val readKey: () -> String = { MotorProfileStore.deviceKey(context) },
    private val onInvalidated: () -> Unit,
) : MotorSettingsObservation {
    private val resolver = context.contentResolver
    private var started = false
    private var active = false
    private var registered = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { changed(null) }
        override fun onChange(selfChange: Boolean, uri: Uri?) { changed(uri) }
        private fun changed(uri: Uri?) {
            if (!active || (matches() && MotorProfileStore.settingValueKnown(resolver, uri) != false)) return
            // A provider teardown error must not prevent microphone/motor shutdown.
            runCatching { close() }
            onInvalidated()
        }
    }

    override fun start(): Boolean {
        if (started) return active
        started = true
        try {
            // Also unregister partially completed registrations if a later URI fails.
            registered = true
            MotorProfileStore.settingUris().forEach { resolver.registerContentObserver(it, false, observer) }
            active = true
            if (matches()) return true
        } catch (_: RuntimeException) { /* Caller prevents capture startup. */ }
        runCatching { close() }
        return false
    }

    private fun matches(): Boolean = runCatching { readKey() == expectedKey }.getOrDefault(false)

    override fun close() {
        active = false // Already queued callbacks cannot invalidate a later capture.
        if (registered) {
            resolver.unregisterContentObserver(observer)
            registered = false
        }
    }
}
