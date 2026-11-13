package com.ullim.watch.haptics

import android.app.Application
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotorSettingsWatcherTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun notifyGlobal() {
        context.contentResolver.notifyChange(Settings.Global.getUriFor("setting_vibration_intensity"), null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun changedGlobalIntensityInvalidatesOnceAndClosesObservation() {
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 0)
        var invalidations = 0
        val watcher = MotorSettingsWatcher(context, MotorProfileStore.deviceKey(context)) { invalidations++ }
        try {
            assertTrue(watcher.start())
            Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 1)
            notifyGlobal()
            assertEquals(1, invalidations)
            Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 2)
            notifyGlobal()
            assertEquals(1, invalidations)
        } finally { watcher.close() }
    }

    @Test fun systemIntensityChangesAreAlsoObserved() {
        var invalidations = 0
        val watcher = MotorSettingsWatcher(context, MotorProfileStore.deviceKey(context)) { invalidations++ }
        try {
            assertTrue(watcher.start())
            Settings.System.putInt(context.contentResolver, "haptic_feedback_intensity", 2)
            context.contentResolver.notifyChange(Settings.System.getUriFor("haptic_feedback_intensity"), null)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, invalidations)
        } finally { watcher.close() }
    }

    @Test fun sameKeyAndUnrelatedOrParentNotificationsDoNotStopCapture() {
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 3)
        var invalidations = 0
        val watcher = MotorSettingsWatcher(context, MotorProfileStore.deviceKey(context)) { invalidations++ }
        try {
            assertTrue(watcher.start())
            notifyGlobal()
            context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), null)
            context.contentResolver.notifyChange(Settings.Global.CONTENT_URI, null)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, invalidations)
            Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 1)
            notifyGlobal()
            assertEquals(1, invalidations)
        } finally { watcher.close() }
    }

    @Test fun anExplicitChangeToAnUnverifiableSettingCannotBeAssumedSafe() {
        Settings.Global.putString(context.contentResolver, "setting_vibration_intensity", null)
        var callbacks = 0
        val watcher = MotorSettingsWatcher(context, MotorProfileStore.deviceKey(context)) { callbacks++ }
        try {
            assertTrue(watcher.start())
            notifyGlobal()
            assertEquals(1, callbacks)
        } finally { watcher.close() }
    }

    @Test fun profileChangeDuringRegistrationIsCaughtBeforeCaptureCanStart() {
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 0)
        val key = MotorProfileStore.deviceKey(context)
        val uri = Settings.Global.getUriFor("setting_vibration_intensity")
        var registeredWhenRead = false
        val watcher = MotorSettingsWatcher(context, key, {
            registeredWhenRead = shadowOf(context.contentResolver).getContentObservers(uri).isNotEmpty()
            Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 1)
            MotorProfileStore.deviceKey(context)
        }) { fail("preflight mismatch is returned, not a live callback") }
        try {
            assertFalse(watcher.start())
            assertTrue("key must be re-read after registration", registeredWhenRead)
        } finally { watcher.close() }
    }

    @Test fun recheckAfterRegistrationRejectsAKeyChangedSinceProfileLoad() {
        val expected = MotorProfileStore.deviceKey(context)
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 1)
        var callbacks = 0
        val watcher = MotorSettingsWatcher(context, expected) { callbacks++ }
        try {
            assertFalse(watcher.start())
            notifyGlobal()
            assertEquals(0, callbacks)
        } finally { watcher.close() }
    }

    @Test fun readFailurePreventsStartOrInvalidatesAnActiveObservation() {
        var fail = true
        var callbacks = 0
        val key = MotorProfileStore.deviceKey(context)
        fun read(): String = if (fail) throw SecurityException("injected provider failure") else key
        val first = MotorSettingsWatcher(context, key, ::read) { callbacks++ }
        try { assertFalse(first.start()) } finally { first.close() }
        fail = false
        val active = MotorSettingsWatcher(context, key, ::read) { callbacks++ }
        try {
            assertTrue(active.start())
            fail = true
            notifyGlobal()
            assertEquals(1, callbacks)
        } finally { active.close() }
    }

    @Test fun queuedOldNotificationsAfterCloseCannotInvalidateTheNextObservation() {
        var oldCallbacks = 0
        var newCallbacks = 0
        val old = MotorSettingsWatcher(context, MotorProfileStore.deviceKey(context)) { oldCallbacks++ }
        assertTrue(old.start())
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 1)
        context.contentResolver.notifyChange(Settings.Global.getUriFor("setting_vibration_intensity"), null)
        old.close()
        val next = MotorSettingsWatcher(context, MotorProfileStore.deviceKey(context)) { newCallbacks++ }
        try {
            assertTrue(next.start())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, oldCallbacks)
            assertEquals(0, newCallbacks)
            Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 2)
            notifyGlobal()
            assertEquals(0, oldCallbacks)
            assertEquals(1, newCallbacks)
        } finally { next.close(); old.close() }
    }
}
