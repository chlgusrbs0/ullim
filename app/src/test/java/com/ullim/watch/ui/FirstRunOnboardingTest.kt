package com.ullim.watch.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ullim.watch.core.ListeningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class FirstRunOnboardingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitMicrophoneConsentRequestsPermissionButDoesNotStartListening() {
        var completed = false
        var permissionRequested = false
        var captureStarted = false
        compose.setContent {
            UllimApp(
                state = ListeningUiState(showOnboarding = true, microphoneGranted = false),
                onStart = { captureStarted = true },
                onCompleteOnboarding = { completed = true },
                onRequestMicrophone = { permissionRequested = true },
            )
        }

        compose.onNodeWithText("울림 시작 전 안내").assertIsDisplayed()
        compose.onNodeWithText("마이크 권한 허용").performScrollTo().performClick()

        assertEquals(true, completed)
        assertEquals(true, permissionRequested)
        assertFalse(captureStarted)
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsNotEnabled()
    }

    @Test fun choosingLaterCompletesOnboardingWithoutRequestingPermission() {
        var completed = false
        var permissionRequested = false
        compose.setContent {
            UllimApp(
                state = ListeningUiState(showOnboarding = true, microphoneGranted = false),
                onCompleteOnboarding = { completed = true },
                onRequestMicrophone = { permissionRequested = true },
            )
        }

        compose.onNodeWithText("나중에").performScrollTo().performClick()

        assertEquals(true, completed)
        assertFalse(permissionRequested)
        compose.onNodeWithText("울림").assertIsDisplayed()
        compose.onNodeWithText("마이크 권한 다시 요청")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
