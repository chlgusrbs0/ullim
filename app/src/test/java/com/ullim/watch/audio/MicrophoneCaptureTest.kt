package com.ullim.watch.audio

import com.ullim.watch.core.AudioFrame
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class MicrophoneCaptureTest {
    @Test fun typedInputAndConsumerFailuresPreserveCauseWhileAlwaysReleasingTheSource() {
        for (cause in listOf(CaptureError.SILENCED, CaptureError.OUTPUT, CaptureError.OUTPUT_UNSUPPORTED)) {
            val statuses = CopyOnWriteArrayList<CaptureStatus>()
            var releases = 0
            val capture = MicrophoneCapture({ object : PcmInput {
                override fun start() {}
                override fun read(buffer: ShortArray): Int {
                    if (cause == CaptureError.SILENCED) throw CaptureException(cause)
                    return buffer.size
                }
                override fun stop() {}
                override fun release() { releases++ }
            } }, { throw CaptureException(cause) }, statuses::add)
            capture.start(1)
            assertTrue(capture.awaitStopped(2000))
            assertEquals(1, releases)
            assertEquals(CaptureStatus.Failed(cause), statuses.last())
        }
    }

    @Test fun failedNativeReleasePreventsAnotherRecorderOnThatCapture() {
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val capture = MicrophoneCapture({ object : PcmInput {
            override fun start() {}
            override fun read(buffer: ShortArray) = -1
            override fun stop() {}
            override fun release() { error("release failed") }
        } }, {}, statuses::add)
        try {
            capture.start(1)
            assertTrue(capture.awaitStopped(2000))
            assertEquals(CaptureStatus.Failed(CaptureError.CLEANUP), statuses.last())
            assertFalse("release failed but a new recorder was admitted", capture.start(2))
        } finally { capture.stop(); assertTrue(capture.awaitStopped(2000)) }
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
