package com.ullim.watch.audio

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

class PcmInputFactoryTest {
    private object Input : PcmInput {
        override fun start() {}
        override fun read(buffer: ShortArray) = -1
        override fun stop() {}
        override fun release() {}
    }

    @Test fun unsupportedUnprocessedBuildFallsBackToRecognition() {
        val attempts = mutableListOf<PcmSource>()
        val input = openPcmInput(true, { true }) { source ->
            attempts += source
            if (source == PcmSource.UNPROCESSED) throw UnsupportedOperationException()
            Input
        }
        assertSame(Input, input)
        assertEquals(listOf(PcmSource.UNPROCESSED, PcmSource.VOICE_RECOGNITION), attempts)
    }

    @Test fun unavailableUnprocessedIsNeverAttempted() {
        val attempts = mutableListOf<PcmSource>()
        val input = openPcmInput(false, { true }) { source -> attempts += source; Input }
        assertSame(Input, input)
        assertEquals(listOf(PcmSource.VOICE_RECOGNITION), attempts)
    }

    @Test fun deniedPermissionDoesNotOpenInputAndReportsPermission() {
        val attempts = CopyOnWriteArrayList<PcmSource>()
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val capture = MicrophoneCapture({ openPcmInput(true, { false }) { source -> attempts += source; Input } }, {}, statuses::add)
        assertTrue(capture.start(1))
        assertTrue(capture.awaitStopped(2000))
        assertTrue(attempts.isEmpty())
        assertEquals(CaptureStatus.Failed(CaptureError.PERMISSION), statuses.last())
    }

    @Test fun permissionRevokedDuringBuildDoesNotRetryAndReportsPermission() {
        var permitted = true
        val attempts = CopyOnWriteArrayList<PcmSource>()
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val capture = MicrophoneCapture({ openPcmInput(true, { permitted }) { source ->
            attempts += source
            permitted = false
            throw UnsupportedOperationException()
        } }, {}, statuses::add)
        assertTrue(capture.start(1))
        assertTrue(capture.awaitStopped(2000))
        assertEquals(listOf(PcmSource.UNPROCESSED), attempts)
        assertEquals(CaptureStatus.Failed(CaptureError.PERMISSION), statuses.last())
    }

    @Test fun unsupportedSourcesWithPermissionReportInitialization() {
        val attempts = CopyOnWriteArrayList<PcmSource>()
        val statuses = CopyOnWriteArrayList<CaptureStatus>()
        val capture = MicrophoneCapture({ openPcmInput(true, { true }) { source ->
            attempts += source
            throw UnsupportedOperationException()
        } }, {}, statuses::add)
        assertTrue(capture.start(1))
        assertTrue(capture.awaitStopped(2000))
        assertEquals(listOf(PcmSource.UNPROCESSED, PcmSource.VOICE_RECOGNITION), attempts)
        assertEquals(CaptureStatus.Failed(CaptureError.INITIALIZATION), statuses.last())
    }

    @Test fun securityExceptionIsNotRetriedAsAnotherSource() {
        val attempts = CopyOnWriteArrayList<PcmSource>()
        assertThrows(SecurityException::class.java) {
            openPcmInput(true, { true }) { source -> attempts += source; throw SecurityException() }
        }
        assertEquals(listOf(PcmSource.UNPROCESSED), attempts)
    }
}
