package com.ullim.watch.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.MainActivity
import com.ullim.watch.ListeningService
import com.ullim.watch.AiControlsActivity
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-watch")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private var token = 0L
    private var owner: MicrophoneCapture? = null

    @Before fun allowMicrophoneAndRefreshActivity() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        while (shadowOf(application).nextStartedService != null) { /* discard setup work */ }
    }

    @After fun releaseFakeServiceOwnership() {
        compose.runOnIdle {
            owner?.let { CaptureRuntime.detach(it) }
            if (token > 0) CaptureRuntime.finished(token)
            CaptureRuntime.setAiEnabled(false)
        }
    }

    private fun requestStart() {
        compose.onNodeWithText("소리 감지 시작").performScrollTo().performClick()
        compose.runOnIdle {
            token = (CaptureRuntime.state.value.listening as ListeningState.Starting).sessionId
            val command = shadowOf(application).nextStartedService
            assertEquals(ListeningService.ACTION_START, command.action)
            assertEquals(token, command.getLongExtra(ListeningService.EXTRA_SESSION, 0))
        }
    }

    private fun confirmRecording(limited: Boolean = false) {
        compose.runOnIdle {
            owner = MicrophoneCapture({ error("fake service does not open AudioRecord") }, {}, {})
            assertTrue(CaptureRuntime.attach(token, owner!!))
            CaptureRuntime.listening(token, limited)
        }
    }

    @Test fun startShowsPendingWithoutOptimisticListeningAndCanBeCancelled() {
        requestStart()
        compose.onNodeWithText("시작 중").assertIsNotEnabled()
        compose.onNodeWithText("감지를 시작하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("시작 취소").performScrollTo().performClick()
        compose.runOnIdle {
            val command = shadowOf(application).nextStartedService
            assertEquals(ListeningService.ACTION_STOP, command.action)
            assertEquals(token, command.getLongExtra(ListeningService.EXTRA_SESSION, 0))
            assertEquals(ListeningState.Starting(token), CaptureRuntime.state.value.listening)
        }
    }

    @Test fun stopSendsCurrentTokenAndWaitsForServiceCompletion() {
        requestStart(); confirmRecording()
        compose.onNodeWithText("중지").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            val command = shadowOf(application).nextStartedService
            assertEquals(ListeningService.ACTION_STOP, command.action)
            assertEquals(token, command.getLongExtra(ListeningService.EXTRA_SESSION, 0))
            assertEquals(ListeningState.Listening(token), CaptureRuntime.state.value.listening)
            CaptureRuntime.stopping(token)
        }
        compose.onNodeWithText("중지 중").assertIsNotEnabled()
        compose.runOnIdle { CaptureRuntime.detach(owner!!); CaptureRuntime.finished(token) }
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsEnabled()
    }

    @Test fun retryBecomesEnabledAfterReleaseWithoutChangingTheErrorText() {
        requestStart(); confirmRecording()
        compose.runOnIdle { CaptureRuntime.fail(token, "마이크 정리 대기"); CaptureRuntime.finished(token) }
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsNotEnabled()
        val error = CaptureRuntime.state.value
        compose.runOnIdle { CaptureRuntime.detach(owner!!) }
        assertEquals(error, CaptureRuntime.state.value)
        compose.onNodeWithText("소리 감지 시작").assertIsEnabled()
    }

    @Test fun guideAndActivityRecreationPreserveActualListeningWithoutAnotherStart() {
        requestStart(); confirmRecording()
        compose.onNodeWithText("사용 안내").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("뒤로").performScrollTo().performClick()
        compose.onNodeWithText("소리 감지 중").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("중지").performScrollTo().assertIsEnabled()
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test fun deniedPermissionHasAnAppSettingsEntry() {
        shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("권한 설정").performScrollTo().performClick()
        val settings = shadowOf(compose.activity).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.action)
        assertEquals("package:com.ullim.watch", settings.data.toString())
    }

    @Test fun permissionsRefreshOnReturnFromAndroidSettingsWithoutStartingCapture() {
        shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("권한 설정").performScrollTo().performClick()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText("권한 설정").assertDoesNotExist()
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test fun listeningShowsCommandedLevelsAndLimitedOutputTruthfully() {
        requestStart(); confirmRecording(limited = true)
        compose.runOnIdle { CaptureRuntime.publish(token, .5f, .25f) }
        compose.onNodeWithText("입력 소리 50%\n요청한 진동 25%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.ullim.watch.R.string.amplitude_unsupported))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun comparisonLabelsDistinguishSourceFromCommandsAndStopWithoutUiFakeState() {
        compose.onNodeWithText("시각화 중지 · 진동 명령 없음").performScrollTo().assertIsDisplayed()
        requestStart(); confirmRecording()
        compose.onNodeWithText("입력 소리 · RMS / 엔벌로프").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("진동 명령 · 물리 측정값 아님").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { CaptureRuntime.stopping(token); CaptureRuntime.detach(owner!!); CaptureRuntime.finished(token) }
        compose.onNodeWithText("시각화 중지 · 진동 명령 없음").performScrollTo().assertIsDisplayed()
    }

    @Test fun liveNumericHistoryReachesBothCanvasRowsAndStopClearsTheirValues() {
        requestStart(); confirmRecording()
        compose.runOnIdle {
            val now = System.nanoTime()
            CaptureRuntime.visualization.offer(
                com.ullim.watch.core.AudioFeatures(token, now, .5f, .5f, .5f),
                com.ullim.watch.core.HapticFrame(token, now, 20, .25f),
            )
        }
        val input = compose.activity.getString(com.ullim.watch.R.string.graph_input_description, 50)
        val output = compose.activity.getString(com.ullim.watch.R.string.graph_command_description, 25)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(input).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(input).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(output).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { CaptureRuntime.stopping(token) }
        compose.onNodeWithContentDescription(compose.activity.getString(com.ullim.watch.R.string.graph_input_description, 0))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(com.ullim.watch.R.string.graph_command_description, 0))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun settingsExposeAiControlButCannotPlayDiagnosticsDuringCapture() {
        requestStart(); confirmRecording(limited = true)
        compose.onNodeWithText("설정").performScrollTo().performClick()
        compose.onNodeWithText("진동 진단").assertIsNotEnabled()
        compose.onNodeWithText("AI 상태와 결과").performScrollTo().performClick()
        assertEquals(AiControlsActivity::class.java.name, shadowOf(compose.activity).nextStartedActivity.component!!.className)
        assertEquals(ListeningState.Listening(token), CaptureRuntime.state.value.listening)
    }

    @Test fun stopBeforeServiceAttachCancelsOnlyTheMatchingPendingSession() {
        val service = Robolectric.buildService(ListeningService::class.java).create()
        try {
            compose.runOnIdle {
                val old = CaptureRuntime.requestStart()!!
                CaptureRuntime.finished(old)
                token = CaptureRuntime.requestStart()!!
                service.get().onStartCommand(Intent(application, ListeningService::class.java)
                    .setAction(ListeningService.ACTION_STOP).putExtra(ListeningService.EXTRA_SESSION, old), 0, 1)
                assertEquals(ListeningState.Starting(token), CaptureRuntime.state.value.listening)
                service.get().onStartCommand(Intent(application, ListeningService::class.java)
                    .setAction(ListeningService.ACTION_STOP).putExtra(ListeningService.EXTRA_SESSION, token), 0, 2)
                assertEquals(ListeningState.Idle, CaptureRuntime.state.value.listening)
            }
        } finally { service.destroy() }
    }

    @Test fun oldNotificationActionCannotAcquireANewerSessionsStopToken() {
        val service = Robolectric.buildService(ListeningService::class.java).create()
        try {
            val oldStop = service.get().notification(101).actions.single().actionIntent
            val newStop = service.get().notification(102).actions.single().actionIntent
            oldStop.send()
            assertEquals(101L, shadowOf(application).nextStartedService.getLongExtra(ListeningService.EXTRA_SESSION, 0))
            newStop.send()
            assertEquals(102L, shadowOf(application).nextStartedService.getLongExtra(ListeningService.EXTRA_SESSION, 0))
        } finally { service.destroy() }
    }
}
