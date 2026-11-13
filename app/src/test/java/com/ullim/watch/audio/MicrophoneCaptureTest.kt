package com.ullim.watch.audio

import com.ullim.watch.core.AudioFrame
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class MicrophoneCaptureTest {
    @Test fun nextSessionWaitsUntilPreviousTerminalStatusHasBeenDelivered() {
        val terminalEntered = CountDownLatch(1)
        val releaseTerminal = CountDownLatch(1)
        val terminalCount = AtomicInteger()
        val capture = MicrophoneCapture({ object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray) = -1
            override fun stop() {}
            override fun release() {}
        } }, {}, { status ->
            if (status is CaptureStatus.Failed && terminalCount.incrementAndGet() == 1) {
                terminalEntered.countDown()
                check(releaseTerminal.await(3, TimeUnit.SECONDS))
            }
        })
        try {
            assertTrue(capture.start(1))
            assertTrue(terminalEntered.await(2, TimeUnit.SECONDS))
            assertFalse("next session overtook previous terminal status", capture.start(2))
        } finally {
            releaseTerminal.countDown()
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
        assertTrue(capture.start(3))
        assertTrue(capture.awaitStopped(2000))
    }

    @Test fun failedNativeReleaseRetainsRecorderOwnership() {
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val opens = AtomicInteger()
        val capture = MicrophoneCapture({
            opens.incrementAndGet()
            object : PcmInput {
                override fun start() {}
                override fun read(buffer: ShortArray) = -1
                override fun stop() {}
                override fun release() { error("native release failed") }
            }
        }, {}, statuses::add)
        capture.start(1)
        assertTrue(capture.awaitStopped(2000))
        assertEquals(CaptureStatus.Failed(CaptureError.CLEANUP), statuses.last())
        try {
            assertFalse("failed release admitted another recorder", capture.start(2))
            assertEquals(1, opens.get())
        } finally {
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
    }

    @Test fun stopReturnsWhileNativeStartIsBlocked() {
        val starting = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val reads = AtomicInteger()
        val releases = AtomicInteger()
        val capture = MicrophoneCapture({ object : PcmInput {
            override fun start() { starting.countDown(); check(releaseStart.await(3, TimeUnit.SECONDS)) }
            override fun read(buffer: ShortArray): Int { reads.incrementAndGet(); return -1 }
            override fun stop() {}
            override fun release() { releases.incrementAndGet() }
        } }, {}, {})
        val caller = Thread { capture.stop(); returned.countDown() }.apply { isDaemon = true }
        try {
            assertTrue(capture.start(1))
            assertTrue(starting.await(2, TimeUnit.SECONDS))
            caller.start()
            assertTrue("stop waited for native start", returned.await(500, TimeUnit.MILLISECONDS))
            assertFalse(capture.awaitStopped(0))
            assertFalse(capture.start(2))
        } finally {
            releaseStart.countDown()
            caller.join(2000)
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
        assertEquals(0, reads.get())
        assertEquals(1, releases.get())
    }

    @Test fun stopReturnsWhileNativeStopIsBlockedAndReleaseStillOwnsRecorder() {
        val reading = CountDownLatch(1)
        val stopping = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val releases = AtomicInteger()
        val capture = MicrophoneCapture({ object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray): Int {
                reading.countDown()
                check(releaseStop.await(3, TimeUnit.SECONDS))
                return -1
            }
            override fun stop() { stopping.countDown(); check(releaseStop.await(3, TimeUnit.SECONDS)) }
            override fun release() { releases.incrementAndGet() }
        } }, {}, {})
        val caller = Thread { capture.stop(); returned.countDown() }.apply { isDaemon = true }
        try {
            assertTrue(capture.start(1))
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            caller.start()
            assertTrue(stopping.await(2, TimeUnit.SECONDS))
            assertTrue("stop waited for native stop", returned.await(500, TimeUnit.MILLISECONDS))
            assertFalse(capture.awaitStopped(0))
            assertFalse(capture.start(2))
        } finally {
            releaseStop.countDown()
            caller.join(2000)
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
        assertEquals(1, releases.get())
    }

    private class BlockingInput : PcmInput {
        val reading = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        var starts = 0
        var releases = 0
        override fun start() { starts++ }
        override fun read(buffer: ShortArray): Int {
            reading.countDown()
            stopped.await(2, TimeUnit.SECONDS)
            return -1
        }
        override fun stop() { stopped.countDown() }
        override fun release() { releases++ }
    }

    @Test fun duplicateStartCannotOpenSecondMicrophoneAndStopReleasesOnce() {
        val input = BlockingInput()
        val opens = AtomicInteger()
        val capture = MicrophoneCapture({ opens.incrementAndGet(); input }, {}, {})
        assertTrue(capture.start(1))
        assertTrue(input.reading.await(2, TimeUnit.SECONDS))
        assertFalse(capture.start(2))
        capture.stop()
        capture.stop()
        assertTrue(capture.awaitStopped(2000))
        assertEquals(1, opens.get())
        assertEquals(1, input.releases)
    }

    @Test fun permissionFailureIsReportedWithoutLeakingSource() {
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val capture = MicrophoneCapture({ throw SecurityException() }, {}, statuses::add)
        capture.start(1)
        assertTrue(capture.awaitStopped(2000))
        assertEquals(CaptureStatus.Failed(CaptureError.PERMISSION), statuses.last())
    }

    @Test fun initializationFailureReleasesPartiallyOpenedInput() {
        var releases = 0
        val input = object : PcmInput {
            override fun start() { throw IllegalStateException() }
            override fun read(buffer: ShortArray) = 0
            override fun stop() {}
            override fun release() { releases++ }
        }
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val capture = MicrophoneCapture({ input }, {}, statuses::add)
        capture.start(1)
        assertTrue(capture.awaitStopped(2000))
        assertEquals(1, releases)
        assertEquals(CaptureStatus.Failed(CaptureError.INITIALIZATION), statuses.last())
    }

    @Test fun partialReadsKeepOwnedSamplesAndSampleBasedTime() {
        var reads = 0
        var released = false
        val frames = CopyOnWriteArrayList<AudioFrame>()
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val input = object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray): Int {
                if (reads++ == 2) return -6
                buffer[0] = reads.toShort()
                buffer[1] = reads.toShort()
                return 2
            }
            override fun stop() {}
            override fun release() { released = true }
        }
        val capture = MicrophoneCapture({ input }, frames::add, statuses::add, { 1_000_000L })
        capture.start(3)
        assertTrue(capture.awaitStopped(2000))
        assertEquals(2, frames.size)
        assertEquals(1.toShort(), frames[0].sampleAt(0))
        assertEquals(2, frames[0].sampleCount)
        assertEquals(1_125_000L, frames[1].timestampNs)
        assertTrue(released)
        assertEquals(CaptureStatus.Failed(CaptureError.READ), statuses.last())
    }

    @Test fun stopDuringFactoryNeverStartsRecorder() {
        val factoryEntered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val input = BlockingInput()
        val capture = MicrophoneCapture({ factoryEntered.countDown(); proceed.await(); input }, {}, {})
        capture.start(1)
        assertTrue(factoryEntered.await(2, TimeUnit.SECONDS))
        capture.stop()
        proceed.countDown()
        assertTrue(capture.awaitStopped(2000))
        assertEquals(0, input.starts)
        assertEquals(1, input.releases)
    }

    @Test fun consumerFailureStillReleasesMicrophone() {
        var released = false
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val input = object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray): Int = 1
            override fun stop() {}
            override fun release() { released = true }
        }
        val capture = MicrophoneCapture({ input }, { throw IllegalStateException() }, statuses::add)
        capture.start(1)
        assertTrue(capture.awaitStopped(2000))
        assertTrue(released)
        assertEquals(CaptureStatus.Failed(CaptureError.CONSUMER), statuses.last())
    }

    @Test fun repeatedSessionsReleaseBeforeNextStart() {
        val inputs = CopyOnWriteArrayList<BlockingInput>()
        val capture = MicrophoneCapture({ BlockingInput().also(inputs::add) }, {}, {})
        repeat(10) {
            assertTrue(capture.start(it + 1L))
            while (inputs.size <= it) Thread.yield()
            assertTrue(inputs[it].reading.await(2, TimeUnit.SECONDS))
            capture.stop()
            assertTrue(capture.awaitStopped(2000))
        }
        assertEquals(10, inputs.sumOf { it.releases })
    }
}
