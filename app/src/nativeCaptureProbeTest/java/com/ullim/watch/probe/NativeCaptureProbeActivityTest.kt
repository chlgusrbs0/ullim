package com.ullim.watch.probe

import com.ullim.watch.audio.CaptureStatus
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.audio.PcmInput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeCaptureProbeActivityTest {
    @Test fun lossOfFocusStopsAndReleasesCaptureEvenWithoutPause() {
        val ready = CountDownLatch(1)
        val stopped = AtomicBoolean()
        val released = AtomicBoolean()
        val capture = MicrophoneCapture({ object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray): Int {
                Thread.sleep(5)
                return if (stopped.get()) 0 else buffer.size
            }
            override fun stop() { stopped.set(true) }
            override fun release() { released.set(true) }
        } }, {}, { if (it == CaptureStatus.Recording) ready.countDown() })
        val controller = Robolectric.buildActivity(NativeCaptureProbeActivity::class.java).create()
        try {
            NativeCaptureProbeActivity::class.java.getDeclaredField("capture").apply {
                isAccessible = true
                set(controller.get(), capture)
            }
            assertTrue(capture.start(1))
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            // No onPause: an overlay can remove focus while leaving the Activity resumed.
            controller.get().onWindowFocusChanged(false)
            assertTrue("focus loss kept the microphone active", capture.awaitStopped(500))
            assertTrue(released.get())
        } finally {
            capture.stop()
            assertTrue(capture.awaitStopped(2_000))
            controller.destroy()
        }
    }
}
