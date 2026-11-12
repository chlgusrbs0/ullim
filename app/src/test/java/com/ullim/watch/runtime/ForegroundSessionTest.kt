package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.audio.PcmInput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ForegroundSessionTest {
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
