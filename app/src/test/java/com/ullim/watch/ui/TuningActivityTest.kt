package com.ullim.watch.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.TuningActivity
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.settings.SettingsRuntime
import com.ullim.watch.settings.TuningParameter
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
class TuningActivityTest {
    @get:Rule val compose = createAndroidComposeRule<TuningActivity>()

    @Test fun actualRoundWatchScreenEditsAutosavesRestoresAndNeverStartsMicrophone() {
        val controller = SettingsRuntime.get(compose.activity)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); controller.state.value.ready }
        compose.onNodeWithText("입력·무음 판단").performScrollTo().performClick()
        compose.onNodeWithText("강한 입력 기준\n50%").performScrollTo().performClick()
        compose.onNodeWithContentDescription("강한 입력 기준 숫자 입력").performScrollTo().performTextReplacement("15")
        compose.onNodeWithText("입력값 적용").performScrollTo().performClick()
        assertEquals(.15f, CaptureRuntime.tuning.value[TuningParameter.REFERENCE_RMS], .00001f)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); !controller.state.value.saving }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("15%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("+").performScrollTo().performClick()
        assertEquals(.16f, CaptureRuntime.tuning.value[TuningParameter.REFERENCE_RMS], .00001f)
        compose.onNodeWithText("이 항목 기본값").performScrollTo().performClick()
        assertEquals(.5f, CaptureRuntime.tuning.value[TuningParameter.REFERENCE_RMS], .00001f)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); !controller.state.value.saving }
        assertEquals(ListeningState.Idle, CaptureRuntime.state.value.listening)
        val application = ApplicationProvider.getApplicationContext<Application>()
        assertNull(shadowOf(application).nextStartedService)
        assertFalse(controller.state.value.error != null)
    }
}
