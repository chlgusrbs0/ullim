package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.audio.CaptureStatus
import com.ullim.watch.core.ListeningState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class ForegroundSessionTest {
    @Test fun stopReturnsAndCancelsOutputWhileNativeStartIsBlocked() {
        val starting = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val releases = AtomicInteger()
        val capture = MicrophoneCapture({ object : PcmInput {
            override fun start() { starting.countDown(); check(releaseStart.await(3, TimeUnit.SECONDS)) }
            override fun read(buffer: ShortArray): Int = error("must not read after stop")
            override fun stop() {}
            override fun release() { releases.incrementAndGet() }
        } }, {}, {})
        val session = ForegroundSession(capture, {}, {}, { cancelled.countDown() })
        val caller = Executors.newSingleThreadExecutor()
        try {
            session.start(1)
            assertTrue(starting.await(1, TimeUnit.SECONDS))
            caller.submit { session.stop(); stopped.countDown() }
            assertTrue("stop waited for native initialization", stopped.await(500, TimeUnit.MILLISECONDS))
            assertEquals(0L, cancelled.count)
        } finally {
            releaseStart.countDown()
            caller.shutdown()
            assertTrue(caller.awaitTermination(2, TimeUnit.SECONDS))
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
        assertEquals(1, releases.get())
    }

    @Test fun foregroundRestrictionCannotOpenMicrophoneAndRollsBackOutput() {
        val opens = AtomicInteger()
        var foregroundClosed = 0
        var outputCancelled = 0
        val capture = MicrophoneCapture({ opens.incrementAndGet(); error("must not open") }, {}, {})
        val session = ForegroundSession(capture, { throw IllegalStateException("foreground start blocked") }, { foregroundClosed++ }, { outputCancelled++ })
        assertEquals(ForegroundStart.FAILED, session.start(1))
        assertEquals(0, opens.get())
        assertEquals(1, foregroundClosed)
        assertEquals(1, outputCancelled)
        session.stop(); session.stop()
        assertEquals(1, foregroundClosed)
        assertEquals(1, outputCancelled)
    }

    @Test fun blockedNativeStopCancelsOutputOnceAndKeepsOwnershipUntilRelease() {
        val controller = ListeningController()
        val id = controller.requestStart()!!
        val reading = CountDownLatch(1)
        val stopping = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val cancelled = AtomicInteger()
        val removed = AtomicInteger()
        val releases = AtomicInteger()
        lateinit var capture: MicrophoneCapture
        capture = MicrophoneCapture({ object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray): Int { reading.countDown(); check(releaseStop.await(3, TimeUnit.SECONDS)); return -1 }
            override fun stop() { stopping.countDown(); check(releaseStop.await(3, TimeUnit.SECONDS)) }
            override fun release() { releases.incrementAndGet() }
        } }, {}, { status ->
            when (status) {
                CaptureStatus.Recording -> controller.listening(id)
                CaptureStatus.Stopped, is CaptureStatus.Failed -> { controller.detach(capture); controller.finished(id) }
                else -> Unit
            }
        })
        assertTrue(controller.attach(id, capture))
        val session = ForegroundSession(capture, {}, { removed.incrementAndGet() }, { cancelled.incrementAndGet() })
        val caller = Executors.newSingleThreadExecutor()
        try {
            session.start(id)
            assertTrue(reading.await(1, TimeUnit.SECONDS))
            caller.submit { controller.stopping(id); session.stop(); returned.countDown() }
            assertTrue(returned.await(500, TimeUnit.MILLISECONDS))
            assertTrue(stopping.await(1, TimeUnit.SECONDS))
            assertEquals(1, cancelled.get())
            assertEquals(1, removed.get())
            assertFalse(capture.awaitStopped(20))
            assertNull(controller.requestStart())
            assertEquals(ListeningState.Stopping(id), controller.state.value.listening)
            session.stop(); session.stop()
            assertEquals(1, cancelled.get())
            assertEquals(1, removed.get())
        } finally {
            releaseStop.countDown()
            caller.shutdown()
            assertTrue(caller.awaitTermination(2, TimeUnit.SECONDS))
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
        assertEquals(1, releases.get())
        assertEquals(ListeningState.Idle, controller.state.value.listening)
        assertNotNull(controller.requestStart())
    }

    @Test fun securityExceptionIsDistinctAndNeverStartsCapture() {
        var opened = false
        val capture = MicrophoneCapture({ opened = true; error("must not open") }, {}, {})
        val session = ForegroundSession(capture, { throw SecurityException() }, {}, {})
        assertEquals(ForegroundStart.DENIED, session.start(1))
        assertFalse(opened)
    }

    @Test fun notificationPrecedesRecordingAndRepeatedStartDoesNotRemoveIt() {
        var promoted = false
        var demoted = false
        val reading = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        var released = false
        val capture = MicrophoneCapture({
            check(promoted)
            object : PcmInput {
                override fun start() {}
                override fun read(buffer: ShortArray): Int { reading.countDown(); stopped.await(2, TimeUnit.SECONDS); return -1 }
                override fun stop() { stopped.countDown() }
                override fun release() { released = true }
            }
        }, {}, {})
        val session = ForegroundSession(capture, { promoted = true }, { demoted = true }, {})
        assertEquals(ForegroundStart.STARTED, session.start(1))
        assertTrue(reading.await(2, TimeUnit.SECONDS))
        assertEquals(ForegroundStart.ALREADY_RUNNING, session.start(2))
        assertFalse(demoted)
        session.stop()
        assertTrue(capture.awaitStopped(2000))
        assertTrue(released)
        assertTrue(demoted)
    }
}
