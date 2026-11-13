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
import com.ullim.watch.R
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
    private var state by mutableStateOf(ListeningUiState(microphoneGranted = true, notificationsGranted = true, settings = SettingsState(ready = true)))
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
        compose.onNode(isToggleable()).performScrollTo().assertIsOff().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        compose.onNode(isToggleable()).assertIsOn()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsDisplayed()
        assertEquals(0, starts)
    }

    @Test fun primaryActionKeepsItsHeightAcrossCapturePhasesAtLargeFont() {
        render()
        val heights = mutableListOf<Float>()
        for ((phase, label) in listOf(
            ListeningState.Idle to "소리 감지 시작",
            ListeningState.Starting(1) to "시작 중",
            ListeningState.Listening(1) to "중지",
            ListeningState.Stopping(1) to "중지 중",
        )) {
            compose.runOnIdle { state = state.copy(runtime = RuntimeState(phase)) }
            val action = compose.onNodeWithText(label).performScrollTo()
            heights += action.fetchSemanticsNode().boundsInRoot.height
            checkTextLayout(label)
        }
        assertTrue("primary action changes height: $heights", heights.max() - heights.min() < 1f)
        println("Large-font primary action heights (host px): $heights")
    }

    @Test fun chartLabelsRemainWrappedAndReadableWithNumericNonColorDescriptions() {
        render()
        checkTextLayout("입력 소리 · 실선 크기 / 점선 강약 흐름", mustWrap = true)
        checkTextLayout("진동 명령 · 물리 측정값 아님", mustWrap = true)
        compose.onNodeWithContentDescription("최근 소리 크기 0%, 강약 흐름 0%. 실선은 소리 크기, 점선은 강약 흐름이에요.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun coreScreenRoutesRemainReadableAtLargeFontAndProduceHostPreviews() {
        fun save(name: String) {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val artifact = File("build/host-ui/issue35/$name.png")
            requireNotNull(artifact.parentFile).mkdirs()
            artifact.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        render()
        checkTextLayout("소리 감지 시작")
        save("idle-primary-2x")
        compose.runOnIdle { state = state.copy(runtime = RuntimeState(ListeningState.Listening(1))) }
        checkTextLayout("중지")
        save("listening-primary-2x")
        compose.onNodeWithText("설정").performScrollTo().performClick()
        checkTextLayout("민감도 높이기")
        save("settings-2x")
        compose.activity.getString(R.string.sensitivity_defaults).let { checkTextLayout(it) }
        compose.onNodeWithText("뒤로").performScrollTo().performClick()
        compose.onNodeWithText("사용 안내").performScrollTo().performClick()
        checkTextLayout("사용 안내")
        save("guide-2x")
        compose.onNodeWithText("문제 해결 안내").performScrollTo().performClick()
        checkTextLayout("문제가 생겼나요?")
        save("error-guide-2x")
        compose.onNodeWithText("권한 설정").performScrollTo().assertHeightIsAtLeast(48.dp)
        assertEquals(0, starts)

        for ((name, id, size) in listOf(
            Triple("launcher", R.mipmap.ic_launcher, 192),
            Triple("monochrome", R.drawable.ic_launcher_monochrome, 108),
            Triple("notification", R.drawable.ic_notification, 48),
            Triple("notification-stop", R.drawable.ic_stop, 48),
        )) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            requireNotNull(compose.activity.getDrawable(id)).apply { setBounds(0, 0, size, size); draw(canvas) }
            File("build/host-ui/issue35/$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }
}
