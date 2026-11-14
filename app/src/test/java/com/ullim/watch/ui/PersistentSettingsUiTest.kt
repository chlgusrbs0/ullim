package com.ullim.watch.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ullim.watch.MainActivity
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.SettingsRuntime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-watch")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PersistentSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun completeInitialNotice() {
        compose.activity.getSharedPreferences("ullim_first_run", 0).edit().putBoolean("onboarding_complete", true).commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val settings = SettingsRuntime.get(compose.activity)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); settings.state.value.ready }
        compose.runOnIdle { settings.reset() }
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); !settings.state.value.saving }
    }

    @Test fun settingsChangeLiveValuesSurviveActivityRecreationAndResetToExplicitDefaults() {
        val settings = SettingsRuntime.get(compose.activity)
        // DataStore finishes on the Android main dispatcher; PAUSED Robolectric
        // needs its Looper driven as well as the Compose frame clock.
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); settings.state.value.ready }
        compose.onNodeWithText("설정").performScrollTo().performClick()
        compose.onNodeWithText("민감도 100%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("민감도 높이기").performScrollTo().performClick()
        assertEquals(1.25f, CaptureRuntime.sensitivity.value)
        compose.onNodeWithText("민감도 높이기").assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().assertIsOn().performClick()
        assertFalse(CaptureRuntime.aiEnabled.value)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); !settings.state.value.saving }
        assertNull(settings.state.value.error)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("민감도 125%").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().assertIsOff().assertIsEnabled()
        compose.onNodeWithText("기본 설정 복원").performScrollTo().performClick()
        assertEquals(1f, CaptureRuntime.sensitivity.value)
        assertTrue(CaptureRuntime.aiEnabled.value)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); !settings.state.value.saving }
        compose.onNodeWithText("설정 저장됨").performScrollTo().assertIsDisplayed()
    }
}
