package com.ullim.watch.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ullim.watch.AiControlsActivity
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.SettingsRuntime
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-watch")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AiControlsActivityTest {
    @get:Rule val compose = createAndroidComposeRule<AiControlsActivity>()
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @After fun resetProcessChoice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CaptureRuntime.setAiEnabled(false)
            val worker = AiRuntime.worker(application)
            worker.setEnabled(worker.state.value.sessionId, false)
        }
    }

    @Test fun realAiScreenToggleRecreationAndBackNeverStartIdleMicrophoneOrNativeModel() {
        val settings = SettingsRuntime.get(compose.activity)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); settings.state.value.ready }
        compose.onNodeWithText("AI 보조 꺼짐").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("AI 보조 켜기").performScrollTo().performClick()
        compose.onNodeWithText("AI 보조 켜짐 · 감지 시작을 기다려요").performScrollTo().assertIsDisplayed()
        assertTrue(CaptureRuntime.aiEnabled.value)
        assertEquals(ListeningState.Idle, CaptureRuntime.state.value.listening)
        val worker = AiRuntime.worker(application)
        assertFalse(worker.state.value.modelLoaded)
        assertFalse(worker.state.value.busy)
        assertEquals(0L, worker.state.value.inferenceCount)
        assertNull(shadowOf(application).nextStartedService)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("AI 보조 끄기").performScrollTo().performClick()
        compose.onNodeWithText("AI 보조 꺼짐").performScrollTo().assertIsDisplayed()
        assertFalse(CaptureRuntime.aiEnabled.value)
        val activity = compose.activity
        compose.onNodeWithText("돌아가기").performScrollTo().performClick()
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(application).nextStartedService)
    }
}
