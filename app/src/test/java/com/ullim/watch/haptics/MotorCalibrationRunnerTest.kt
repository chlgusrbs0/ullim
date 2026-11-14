package com.ullim.watch.haptics

import android.Manifest
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.runtime.CaptureRuntime
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MotorCalibrationRunnerTest {
    @Test fun motorWithoutAmplitudeControlCalibratesTheSameDutyWaveformAsLiveOutput() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val vibrator = application.getSystemService(VibratorManager::class.java).defaultVibrator
        val reading = CountDownLatch(1)
        val stop = CountDownLatch(1)
        val calls = mutableListOf<DiagnosticWaveform>()
        val device = object : WaveformDevice {
            override val capabilities = HapticCapabilities(true, false, null)
            override fun play(timings: LongArray, amplitudes: IntArray) { calls += DiagnosticWaveform(timings, amplitudes) }
            override fun cancel() {}
        }
        val runner = MotorCalibrationRunner(application, vibrator, {}, { object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray): Int { reading.countDown(); check(stop.await(3, TimeUnit.SECONDS)); return -1 }
            override fun stop() { stop.countDown() }
            override fun release() {}
        } }, { SystemClock.uptimeMillis() * 1_000_000 }, device)
        try {
            runner.start()
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            val waveform = calls.first()
            assertArrayEquals(longArrayOf(0, 12, 48), waveform.timings)
            assertArrayEquals(intArrayOf(0, -1, 0), waveform.amplitudes)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
            assertArrayEquals(longArrayOf(0, 60), calls.last().timings)
            assertArrayEquals(intArrayOf(0, -1), calls.last().amplitudes)
        } finally {
            runner.cancel(); stop.countDown()
            val deadline = System.nanoTime() + 2_000_000_000
            while (!CaptureRuntime.canStart() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.yield() }
            assertTrue(CaptureRuntime.canStart())
        }
    }

    @Test fun cancelBeforeQueuedRecordingCannotRestartCalibrationOrVibration() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val vibrator = application.getSystemService(VibratorManager::class.java).defaultVibrator
        shadowOf(vibrator).setHasVibrator(true)
        val enteredClock = CountDownLatch(1)
        val releaseClock = CountDownLatch(1)
        val releaseNative = CountDownLatch(1)
        val firstClock = AtomicBoolean(true)
        val states = mutableListOf<MotorCalibrationState>()
        val runner = MotorCalibrationRunner(application, vibrator, states::add, { object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray) = -1
            override fun stop() {}
            override fun release() { check(releaseNative.await(3, TimeUnit.SECONDS)) }
        } }, {
            if (firstClock.compareAndSet(true, false)) {
                enteredClock.countDown()
                check(releaseClock.await(3, TimeUnit.SECONDS))
            }
            0L
        })
        try {
            runner.start()
            assertTrue(enteredClock.await(2, TimeUnit.SECONDS))
            runner.cancel()
            val afterCancel = states.size
            releaseClock.countDown()
            val deadline = System.nanoTime() + 1_000_000_000
            while (states.size == afterCancel && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.yield() }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            assertFalse("canceled run became RUNNING again", states.drop(afterCancel).contains(MotorCalibrationState.RUNNING))
            assertFalse(shadowOf(vibrator).isVibrating)
        } finally {
            releaseClock.countDown(); releaseNative.countDown(); runner.cancel()
            val deadline = System.nanoTime() + 2_000_000_000
            while (!CaptureRuntime.canStart() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.yield() }
            assertTrue(CaptureRuntime.canStart())
        }
    }
}
