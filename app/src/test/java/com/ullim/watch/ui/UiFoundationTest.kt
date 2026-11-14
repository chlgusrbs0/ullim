package com.ullim.watch.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.DiagnosticsActivity
import com.ullim.watch.MainActivity
import com.ullim.watch.settings.SettingsRuntime
import java.io.File
import org.junit.Assert.*
import org.junit.Before
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
class UiFoundationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun useTheCompletedNoticeStateForNavigation() {
        compose.activity.getSharedPreferences("ullim_first_run", 0).edit()
            .putBoolean("onboarding_complete", true).commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val settings = SettingsRuntime.get(compose.activity)
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); settings.state.value.ready }
        compose.runOnIdle { settings.reset() }
        compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); !settings.state.value.saving }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val file = File("build/host-ui/issue24-$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun theExistingHardwareDiagnosticEntryRemainsReachableWithoutStartingAudio() {
        compose.onNodeWithText("설정").performScrollTo().performClick()
        compose.onNodeWithText("진동 진단").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            val app = ApplicationProvider.getApplicationContext<Application>()
            assertEquals(DiagnosticsActivity::class.java.name, shadowOf(app).nextStartedActivity.component!!.className)
            assertNull(shadowOf(app).nextStartedService)
        }
    }

    @Test fun fourScreenNavigationAndBackWorkOnTheSmallRoundViewport() {
        shot("home")
        compose.onNodeWithText("설정").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("민감도 100%").performScrollTo().assertIsDisplayed()
        shot("settings")
        compose.onNodeWithText("뒤로").performScrollTo().performClick()
        compose.onNodeWithText("사용 안내").performScrollTo().performClick()
        shot("guide")
        compose.onNodeWithText("문제 해결 안내").performScrollTo().performClick()
        compose.onNodeWithText("문제가 생겼나요?").performScrollTo().assertIsDisplayed()
        shot("error")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("문제 해결 안내").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("설정").performScrollTo().assertIsDisplayed()
    }
}
