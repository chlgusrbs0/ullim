package com.ullim.watch.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ullim.watch.haptics.AiTactileStatus
import com.ullim.watch.runtime.LiveLevels
import com.ullim.watch.ui.theme.UllimTheme
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
class AiAnalysisContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsModelCandidatesActualPolicyAndInputVersusOutputOnWatch() {
        compose.setContent {
            UllimTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AiResultContent(AiDisplayState(status = AiDisplayStatus.LISTENING,
                        result = AiResultKind.UNCERTAIN,
                        candidates = listOf(AiCandidateDisplay("걷는 소리·발소리", 50)),
                        completedInferences = 8), showOriginal = true)
                    SignalPathContent(LiveLevels(sound = .02f, commanded = 1f,
                        rawSound = .1f, baselineCommanded = .8f, motorFiltered = true,
                        aiAdjusted = true, aiPolicy = AiTactileStatus.CONTRAST), true)
                }
            }
        }
        for (text in listOf("후보: 걷는 소리·발소리 · 모델 점수 50%",
            "마이크 입력 10% → 모터 보정 후 2%", "기본 진동 80% → AI 후 요청 100%",
            "모터 소음 보정 중", "AI 적용: 강약 대비 조절")) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }
}
