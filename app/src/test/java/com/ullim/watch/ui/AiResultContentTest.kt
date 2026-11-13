package com.ullim.watch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ullim.watch.ai.*
import com.ullim.watch.core.*
import com.ullim.watch.ui.theme.UllimTheme
import kotlinx.coroutines.flow.MutableStateFlow
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
class AiResultContentTest {
    @get:Rule val compose = createComposeRule()
    private val enabled = MutableStateFlow(true)
    private val runtime = MutableStateFlow(RuntimeState(ListeningState.Listening(7)))
    private val worker = MutableStateFlow(AiSnapshot(sessionId = 7, phase = AiPhase.LOADING))
    private val clock = mutableLongStateOf(1_100_000_000)
    private fun classification(label: String = "Speech", score: Float = .82f) =
        Classification(7, 1_000_000_000, 2_500_000_000, listOf(ClassScore(label, score)))
    private fun render() {
        compose.setContent {
            val state = collectAiDisplay(enabled, runtime, worker) { clock.longValue }
            UllimTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    AiResultContent(state, showOriginal = true)
                }
            }
        }
    }
    private fun recognize(label: String = "Speech", score: Float = .82f) {
        compose.runOnIdle { worker.value = AiSnapshot(sessionId = 7, phase = AiPhase.LISTENING, classification = classification(label, score)) }
    }

    @Test fun flowLoadingFreshReplacementUncertaintyExpiryAndOffNeverLeaveAnOldRecognition() {
        render()
        compose.onNodeWithText("AI를 준비하고 있어요").performScrollTo().assertIsDisplayed()
        recognize()
        compose.onNodeWithText("말소리").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("모델 점수 82% · 정답 확률이 아니에요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("모델 원문: Speech").performScrollTo().assertIsDisplayed()
        recognize("Knock", .9f)
        compose.onNodeWithText("말소리").assertDoesNotExist()
        compose.onNodeWithText("노크").performScrollTo().assertIsDisplayed()
        recognize("Knock", .2f)
        compose.onNodeWithText("노크").assertDoesNotExist()
        compose.onNodeWithText("판별 불확실").performScrollTo().assertIsDisplayed()
        recognize()
        compose.runOnIdle { clock.longValue = 2_500_000_000 }
        compose.onNodeWithText("말소리").assertDoesNotExist()
        compose.onNodeWithText("최근 결과가 만료됐어요").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("AI 보조 꺼짐").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("최근 결과가 만료됐어요").assertDoesNotExist()
    }

    @Test fun failedDelayedStoppedAndUnknownHideFreshResultsAndExplainFallback() {
        render(); recognize()
        compose.runOnIdle { worker.value = worker.value.copy(phase = AiPhase.DELAYED) }
        compose.onNodeWithText("말소리").assertDoesNotExist()
        compose.onNodeWithText("AI 응답이 지연되고 있어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기본 소리 변환은 계속 작동해요").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { worker.value = worker.value.copy(phase = AiPhase.FAILED, fault = AiFault.INFERENCE) }
        compose.onNodeWithText("말소리").assertDoesNotExist()
        recognize("Unknown", .95f)
        compose.onNodeWithText("지원하지 않는 분류예요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unknown").assertDoesNotExist()
        compose.runOnIdle { runtime.value = RuntimeState(ListeningState.Stopping(7)) }
        compose.onNodeWithText("지원하지 않는 분류예요").assertDoesNotExist()
    }

    @Test fun untranslatedLabelWrapsAndExpiredWorkerMetadataHasNoScore() {
        render(); recognize("French horn", .88f)
        compose.onNodeWithText("French horn (모델 원문)").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { worker.value = worker.value.copy(classification = null, resultExpired = true) }
        compose.onNodeWithText("French horn (모델 원문)").assertDoesNotExist()
        compose.onNodeWithText("모델 점수 88% · 정답 확률이 아니에요").assertDoesNotExist()
        compose.onNodeWithText("최근 결과가 만료됐어요").performScrollTo().assertIsDisplayed()
    }
}
