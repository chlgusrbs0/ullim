package com.ullim.watch

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.ai.AiWorker
import com.ullim.watch.ai.AiFault
import com.ullim.watch.ai.observeAiFailures
import com.ullim.watch.core.*
import com.ullim.watch.ui.AiDisplay
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceFailureTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    class ReadFailureService : ListeningService() {
        var releases = 0
        var outputWasActive = false
        override fun createPcmInput(): PcmInput = object : PcmInput {
            private var frames = 0
            override fun start() {}
            override fun read(buffer: ShortArray): Int {
                Thread.sleep(20)
                if (frames++ < 3) {
                    buffer.fill(5000)
                    return buffer.size
                }
                outputWasActive = shadowOf(getSystemService(VibratorManager::class.java).defaultVibrator).isVibrating
                return -6
            }
            override fun stop() {}
            override fun release() { releases++ }
        }
    }

    @Test fun actualServiceReadFailureReportsMatchingCodeCancelsMotorAndAllowsManualRestart() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val vibrator = application.getSystemService(VibratorManager::class.java).defaultVibrator
        shadowOf(vibrator).setHasVibrator(true)
        shadowOf(vibrator).setHasAmplitudeControl(true)
        val service = Robolectric.buildService(ReadFailureService::class.java).create()
        val token = CaptureRuntime.requestStart()!!
        try {
            service.get().onStartCommand(Intent(application, ListeningService::class.java)
                .setAction(ListeningService.ACTION_START).putExtra(ListeningService.EXTRA_SESSION, token), 0, 1)
            val deadline = System.nanoTime() + 3_000_000_000L
            while ((CaptureRuntime.failure.value == null || !CaptureRuntime.canStart()) && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(2)
            }
            assertEquals(CaptureFailure.MIC_READ, CaptureRuntime.failure.value)
            assertTrue(CaptureRuntime.state.value.listening is ListeningState.Error)
            assertTrue(CaptureRuntime.canStart())
            assertEquals(1, service.get().releases)
            assertTrue("test must have played a command before the injected read failure", service.get().outputWasActive)
            assertFalse(shadowOf(vibrator).isVibrating)
            assertTrue(shadowOf(vibrator).isCancelled)
            assertTrue(ShadowLog.getLogsForTag("UllimError").any { it.msg == "session=$token code=MIC_READ" })
        } finally {
            service.destroy()
            CaptureRuntime.finished(token)
        }
        val retry = CaptureRuntime.requestStart()!!
        assertNull(CaptureRuntime.failure.value)
        CaptureRuntime.finished(retry)
    }

    @Test fun processReporterObservesFailedCloseAfterCaptureStopAndMatchesDisplayedCodeOnce() {
        val lane = Executors.newSingleThreadExecutor()
        val worker = AiWorker({ object : SoundClassifier {
            override fun classify(frame: AudioFrame): Classification = error("not requested")
            override fun close() { error("injected native close failure") }
        } }, lane)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        observeAiFailures(worker, scope)
        try {
            worker.start(91, true)
            val loadDeadline = System.nanoTime() + 3_000_000_000L
            while ((!worker.state.value.modelLoaded || worker.state.value.busy) && System.nanoTime() < loadDeadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2)
            }
            assertTrue(worker.state.value.modelLoaded)
            worker.stop(91) // service-level collectors are no longer required
            val closeDeadline = System.nanoTime() + 3_000_000_000L
            while ((worker.state.value.fault != AiFault.CLEANUP || ShadowLog.getLogsForTag("UllimError").none { it.msg == "session=91 code=AI_CLEANUP" }) && System.nanoTime() < closeDeadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2)
            }
            val display = AiDisplay.project(true, RuntimeState(), worker.state.value, System.nanoTime())
            assertEquals("AI_CLEANUP", display.faultCode)
            repeat(3) { worker.tick(); shadowOf(Looper.getMainLooper()).idle() }
            assertEquals(1, ShadowLog.getLogsForTag("UllimError").count { it.msg == "session=91 code=${display.faultCode}" })
        } finally {
            scope.cancel()
            lane.shutdown()
            assertTrue(lane.awaitTermination(3, TimeUnit.SECONDS))
        }
    }
}
