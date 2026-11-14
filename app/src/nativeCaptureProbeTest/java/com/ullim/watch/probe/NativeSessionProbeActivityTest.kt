package com.ullim.watch.probe

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.runtime.CaptureRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeSessionProbeActivityTest {
    class UnsafePackageActivity : NativeSessionProbeActivity() {
        override fun getPackageName() = "com.ullim.watch"
    }
    @Test fun missingActionHasNoSideEffects() {
        val activity = Robolectric.buildActivity(NativeSessionProbeActivity::class.java).setup()
        try {
            activity.get().onWindowFocusChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(CaptureRuntime.canStart())
            assertTrue(ShadowLog.getLogsForTag("UllimSessionProbe").isEmpty())
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun missingPermissionFailsBeforeAnyCalibrationOrSession() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(NativeSessionProbeActivity::class.java,
            Intent("com.ullim.watch.PROBE_SESSIONS")).setup()
        try {
            controller.get().onWindowFocusChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(CaptureRuntime.canStart())
            assertTrue(ShadowLog.getLogsForTag("UllimSessionProbe").any { "result=FAIL code=PERMISSION" in it.msg })
            assertFalse(java.io.File(app.noBackupFilesDir, "motor-noise-v2.bin").exists())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun originalPackageFailsBeforeAnyCalibrationOrSession() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(UnsafePackageActivity::class.java,
            Intent("com.ullim.watch.PROBE_SESSIONS")).setup()
        try {
            controller.get().onWindowFocusChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(CaptureRuntime.canStart())
            assertTrue(ShadowLog.getLogsForTag("UllimSessionProbe").any { "result=FAIL code=PROBE_PACKAGE" in it.msg })
            assertFalse(java.io.File(app.noBackupFilesDir, "motor-noise-v2.bin").exists())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun missingNotificationPermissionCannotBypassNotificationVerification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val controller = Robolectric.buildActivity(NativeSessionProbeActivity::class.java,
            Intent("com.ullim.watch.PROBE_SESSIONS")).setup()
        try {
            controller.get().onWindowFocusChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(ShadowLog.getLogsForTag("UllimSessionProbe").any { "result=FAIL code=PERMISSION" in it.msg })
            assertTrue(CaptureRuntime.canStart())
        } finally { controller.pause().stop().destroy() }
    }
}
