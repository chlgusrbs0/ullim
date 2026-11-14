package com.ullim.watch.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import com.ullim.watch.core.ListeningState
import com.ullim.watch.core.RuntimeState
import com.ullim.watch.settings.SettingsState
import com.ullim.watch.settings.UserSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-watch")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WearAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(ListeningUiState(microphoneGranted = true, notificationsGranted = true, settings = SettingsState(values = UserSettings(sensitivity = 1f), ready = true)))
    private var starts = 0

    private fun render() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                UllimApp(state, onStart = { starts++ },
                    onSensitivity = { state = state.copy(settings = state.settings.copy(values = state.settings.values.copy(sensitivity = it))) },
                    onAiToggle = { state = state.copy(settings = state.settings.copy(values = state.settings.values.copy(aiEnabled = !state.settings.values.aiEnabled))) })
            }
        }
    }

    private fun checkTextLayout(text: String, mustWrap: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        val layout = layouts.single()
        assertFalse("overflow: $text", layout.hasVisualOverflow)
        assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
        if (mustWrap) assertTrue("label should wrap at2x on192dp", layout.lineCount > 1)
    }

    @Test fun primaryCaptureActionHasActualPhaseAndLargeTouchTargetWithoutTruncatingText() {
        render()
        val start = compose.onNodeWithText("소리 감지 시작").performScrollTo()
        start.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "소리를 진동으로 느껴보세요"))
        checkTextLayout("소리 감지 시작", mustWrap = true)
        val view = compose.activity.window.decorView
        val preview = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(preview)) }
        val artifact = File("build/host-ui/large-font-home.png")
        requireNotNull(artifact.parentFile).mkdirs()
        artifact.outputStream().use { assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        compose.runOnIdle { state = state.copy(runtime = RuntimeState(ListeningState.Starting(1)), startAllowed = false) }
        compose.onNodeWithText("시작 중").performScrollTo().assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "감지를 시작하고 있어요"))
        compose.runOnIdle { state = state.copy(runtime = RuntimeState(ListeningState.Listening(1))) }
        compose.onNodeWithText("중지").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "소리 감지 중"))
    }

    @Test fun sensitivityAndAiExposeCurrentValuesAndSystemBackDoesNotStartCapture() {
        render()
        compose.onNodeWithText("설정").performScrollTo().performClick()
        compose.onNodeWithText("민감도 높이기").performScrollTo().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "현재 민감도 100%"))
            .performClick()
        compose.onNodeWithText("민감도 높이기").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "현재 민감도 125%"))
        checkTextLayout("민감도 높이기", mustWrap = true)
        checkTextLayout("민감도 125%")
        checkTextLayout("AI 보조")
        compose.onNode(isToggleable()).performScrollTo().assertIsOn().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        compose.onNode(isToggleable()).assertIsOff()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsDisplayed()
        assertEquals(0, starts)
    }

    @Test fun chartLabelsRemainWrappedAndReadableWithNumericNonColorDescriptions() {
        render()
        checkTextLayout("입력 소리 · 실선 RMS / 점선 엔벌로프", mustWrap = true)
        checkTextLayout("진동 명령 · 물리 측정값 아님", mustWrap = true)
        compose.onNodeWithContentDescription("입력 소리 RMS 최근 0%, 엔벌로프 최근 0%. 실선은 RMS, 점선은 엔벌로프예요.")
            .performScrollTo().assertIsDisplayed()
    }
}
