package com.ullim.watch.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.DiagnosticsActivity
import com.ullim.watch.MainActivity
import com.ullim.watch.runtime.CaptureRuntime
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
class FirstRunIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val firstRun get() = app.getSharedPreferences("ullim_first_run", 0)

    @Before fun freshInstallState() {
        firstRun.edit().clear().commit()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun laterPersistsCompletionAndRecreationDoesNotForceTheNoticeAgain() {
        compose.onNodeWithText("울림 시작 전 안내").assertIsDisplayed()
        compose.onNodeWithText("나중에").performScrollTo().performClick()
        compose.onNodeWithText("울림").assertIsDisplayed()
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsNotEnabled()
        assertTrue(firstRun.getBoolean("onboarding_complete", false))
        assertNull(shadowOf(app).nextStartedService)
        assertTrue(CaptureRuntime.canStart())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("울림 시작 전 안내").assertDoesNotExist()
        compose.onNodeWithText("마이크 권한 다시 요청").performScrollTo().assertIsDisplayed()
    }

    @Suppress("DEPRECATION") // Drive the OS callback through ActivityResultRegistry.
    @Test fun microphonePermissionResultDoesNotStartAService() {
        compose.onNodeWithText("마이크 권한 허용").performScrollTo().performClick()
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.RECORD_AUDIO), request.requestedPermissions)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_GRANTED))
        }
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsEnabled()
        assertNull(shadowOf(app).nextStartedService)
        assertTrue(CaptureRuntime.canStart())
    }

    @Test fun existingPermissionContinuesWithoutARequestAndKeepsCalibrationMandatory() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("메인으로").performScrollTo().performClick()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertNull(shadowOf(app).nextStartedService)
        compose.onNodeWithText("소리 감지 시작").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(DiagnosticsActivity::class.java.name, shadowOf(app).nextStartedActivity.component!!.className)
            assertNull(shadowOf(app).nextStartedService)
            assertTrue(CaptureRuntime.canStart())
        }
    }

    @Suppress("DEPRECATION")
    @Test fun firstDenialOffersAnotherMicrophoneOnlyRequestWithoutCapture() {
        compose.onNodeWithText("마이크 권한 허용").performScrollTo().performClick()
        val firstRequest = shadowOf(compose.activity).lastRequestedPermission
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO, true)
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(firstRequest.requestCode, firstRequest.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED))
        }
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("마이크 권한 다시 요청").performScrollTo().performClick()
        val retry = shadowOf(compose.activity).lastRequestedPermission
        assertNotSame(firstRequest, retry)
        assertArrayEquals(arrayOf(Manifest.permission.RECORD_AUDIO), retry.requestedPermissions)
        assertNull(shadowOf(app).nextStartedService)
        assertTrue(CaptureRuntime.canStart())
    }

    @Test fun backCompletesTheNoticeWithoutRequestingPermissionOrStartingCapture() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("울림").assertIsDisplayed()
        assertTrue(firstRun.getBoolean("onboarding_complete", false))
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertNull(shadowOf(app).nextStartedService)
        assertTrue(CaptureRuntime.canStart())
    }

    @Suppress("DEPRECATION")
    @Test fun notificationsAreRequestedOnlyByTheLaterExplicitStart() {
        compose.onNodeWithText("마이크 권한 허용").performScrollTo().performClick()
        val microphone = shadowOf(compose.activity).lastRequestedPermission
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(microphone.requestCode, microphone.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_GRANTED))
        }
        assertSame(microphone, shadowOf(compose.activity).lastRequestedPermission)
        assertNull(shadowOf(app).nextStartedService)
        compose.onNodeWithText("소리 감지 시작").performScrollTo().performClick()
        val notification = shadowOf(compose.activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notification.requestedPermissions)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun deniedPermissionHasASettingsPathAndReturnNeverStartsCapture() {
        firstRun.edit().putBoolean("onboarding_complete", true)
            .putBoolean("microphone_permission_requested", true).commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("권한 설정").performScrollTo().performClick()
        compose.runOnIdle {
            val settingsIntent = shadowOf(app).nextStartedActivity
            assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.action)
            assertEquals("package:${app.packageName}", settingsIntent.dataString)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("소리 감지 시작").performScrollTo().assertIsEnabled()
        assertNull(shadowOf(app).nextStartedService)
        assertTrue(CaptureRuntime.canStart())
    }
}
