package com.ullim.watch

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.core.ListeningState
import com.ullim.watch.haptics.MotorNoiseProfile
import com.ullim.watch.haptics.MotorProfileStore
import com.ullim.watch.haptics.MotorSettingsObservation
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotorSettingsServiceTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    class TestService : ListeningService() {
        var observationAllowed = true
        var observationCloses = 0
        var invalidation: (() -> Unit)? = null
        val inputCreations = AtomicInteger()
        val reads = AtomicInteger()
        val releases = AtomicInteger()
        override fun createMotorSettingsObservation(expectedKey: String, onInvalidated: () -> Unit): MotorSettingsObservation {
            invalidation = onInvalidated
            return object : MotorSettingsObservation {
                override fun start() = observationAllowed
                override fun close() { observationCloses++ }
            }
        }
        override fun createPcmInput(): PcmInput {
            inputCreations.incrementAndGet()
            return object : PcmInput {
                private val stopped = AtomicBoolean()
                override fun start() {}
                override fun read(buffer: ShortArray): Int {
                    Thread.sleep(20)
                    if (stopped.get()) return 0
                    reads.incrementAndGet()
                    buffer.fill(5000)
                    return buffer.size
                }
                override fun stop() { stopped.set(true) }
                override fun release() { releases.incrementAndGet() }
            }
        }
    }
    private fun prepare() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val vibrator = application.getSystemService(VibratorManager::class.java).defaultVibrator
        shadowOf(vibrator).setHasVibrator(true)
        shadowOf(vibrator).setHasAmplitudeControl(true)
        CaptureRuntime.setAiEnabled(false)
        assertTrue(MotorProfileStore.save(application, MotorProfileStore.deviceKey(application), MotorNoiseProfile(DoubleArray(257), DoubleArray(257))))
    }
    private fun start(service: TestService, token: Long) = service.onStartCommand(
        Intent(application, ListeningService::class.java).setAction(ListeningService.ACTION_START)
            .putExtra(ListeningService.EXTRA_SESSION, token), 0, 1)
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2) }
        assertTrue(condition())
    }

    @Test fun settingInvalidationCancelsOutputReleasesMicrophoneAndAllowsManualRestart() {
        prepare()
        val controller = Robolectric.buildService(TestService::class.java).create()
        val token = CaptureRuntime.requestStart()!!
        try {
            start(controller.get(), token)
            val vibrator = application.getSystemService(VibratorManager::class.java).defaultVibrator
            await { CaptureRuntime.state.value.listening is ListeningState.Listening && shadowOf(vibrator).isVibrating }
            controller.get().invalidation!!()
            await { CaptureRuntime.canStart() && controller.get().releases.get() == 1 }
            assertEquals(CaptureFailure.MOTOR_CALIBRATION, CaptureRuntime.failure.value)
            assertTrue(CaptureRuntime.state.value.listening is ListeningState.Error)
            assertFalse(shadowOf(vibrator).isVibrating)
            assertEquals(1, controller.get().observationCloses)
            assertNull("same identity must not revive an explicitly invalidated calibration",
                MotorProfileStore.load(application, MotorProfileStore.deviceKey(application)))
            val retry = CaptureRuntime.requestStart()!!
            CaptureRuntime.finished(retry)
        } finally { controller.destroy(); CaptureRuntime.finished(token) }
    }

    @Test fun failedObservationPreflightNeverCreatesAMicrophone() {
        prepare()
        val controller = Robolectric.buildService(TestService::class.java).create()
        controller.get().observationAllowed = false
        val token = CaptureRuntime.requestStart()!!
        try {
            start(controller.get(), token)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(CaptureFailure.MOTOR_CALIBRATION, CaptureRuntime.failure.value)
            assertEquals(0, controller.get().inputCreations.get())
            assertEquals(1, controller.get().observationCloses)
            assertTrue(CaptureRuntime.canStart())
        } finally { controller.destroy(); CaptureRuntime.finished(token) }
    }

    @Test fun lateOldCallbackAfterUserStopCannotFailANewSession() {
        prepare()
        val controller = Robolectric.buildService(TestService::class.java).create()
        val token = CaptureRuntime.requestStart()!!
        var next: Long? = null
        try {
            start(controller.get(), token)
            await { controller.get().reads.get() >= 2 }
            val oldCallback = controller.get().invalidation!!
            controller.get().onStartCommand(Intent(application, ListeningService::class.java)
                .setAction(ListeningService.ACTION_STOP).putExtra(ListeningService.EXTRA_SESSION, token), 0, 2)
            await { CaptureRuntime.canStart() && controller.get().releases.get() == 1 }
            next = CaptureRuntime.requestStart()!!
            oldCallback()
            assertEquals(ListeningState.Starting(next), CaptureRuntime.state.value.listening)
            assertNull(CaptureRuntime.failure.value)
            assertEquals(1, controller.get().observationCloses)
        } finally { next?.let(CaptureRuntime::finished); controller.destroy(); CaptureRuntime.finished(token) }
    }
}
