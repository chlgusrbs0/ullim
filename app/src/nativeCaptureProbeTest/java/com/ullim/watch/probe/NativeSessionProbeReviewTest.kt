package com.ullim.watch.probe

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.*
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeSessionProbeReviewTest {
    class SettingsActivity : NativeSessionProbeActivity() {
        lateinit var controller: SettingsController
        override fun settingsController() = controller
    }

    @Test fun failedOnSaveMustBeObservedBeforeAnotherToggleCanClearItsError() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        withTimeout(3_000) {
            while (!SettingsRuntime.get(app).state.value.ready) { shadowOf(Looper.getMainLooper()).idle(); delay(2) }
        }
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = SettingsController(object : SettingsStore {
            override suspend fun load() = UserSettings()
            override suspend fun save(settings: UserSettings) { if (settings.aiEnabled) throw IOException("injected") }
        }, settingsScope, CaptureRuntime::applySettings)
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        activity.get().controller = controller
        try {
            withTimeout(3_000) { while (!controller.state.value.ready) delay(2) }
            val failure = runCatching { withTimeout(3_000) { activity.get().setAi(true) } }.exceptionOrNull()
            assertEquals("SETTINGS_ERROR", failure?.message)
        } finally { settingsScope.cancel(); CaptureRuntime.setAiEnabled(false); activity.pause().stop().destroy() }
    }

    @Test fun cancellingANonServiceMicrophoneLeaseMustNotCreateAService() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val activity = Robolectric.buildActivity(NativeSessionProbeActivity::class.java).setup()
        val token = CaptureRuntime.requestStart()!!
        val owner = MicrophoneCapture({ error("must never open input") }, {}, {})
        assertTrue(CaptureRuntime.attach(token, owner))
        CaptureRuntime.stopping(token) // Calibration owns this lease, not ListeningService.
        try {
            activity.get().requestCleanup()
            assertNull(shadowOf(app).nextStartedService)
        } finally {
            CaptureRuntime.detach(owner); CaptureRuntime.finished(token)
            activity.pause().stop().destroy()
        }
    }

    @Test fun cleanedRetryableErrorKeepsOriginalFailureWhilePassStillRequiresIdle() {
        val activity = Robolectric.buildActivity(NativeSessionProbeActivity::class.java).setup()
        val token = CaptureRuntime.requestStart()!!
        CaptureRuntime.fail(token, "injected", CaptureFailure.MIC_READ)
        CaptureRuntime.finished(token)
        try {
            assertTrue(CaptureRuntime.canStart())
            assertTrue(activity.get().cleanupRuntimeAccepted("FAIL"))
            assertTrue(activity.get().cleanupRuntimeAccepted("CANCELLED"))
            assertFalse(activity.get().cleanupRuntimeAccepted("PASS"))
        } finally { activity.pause().stop().destroy() }
    }
}
