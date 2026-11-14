package com.ullim.watch.runtime

import com.ullim.watch.audio.CaptureError
import com.ullim.watch.audio.MicrophoneCapture
import org.junit.Assert.*
import org.junit.Test

class CaptureFailureTest {
    @Test fun everyCaptureCauseHasADistinctStableCodeAndRecoveryPolicy() {
        assertEquals(CaptureFailure.MIC_PERMISSION, CaptureFailure.from(CaptureError.PERMISSION))
        assertEquals(CaptureFailure.MIC_SILENCED, CaptureFailure.from(CaptureError.SILENCED))
        assertEquals(CaptureFailure.HAPTIC_OUTPUT, CaptureFailure.from(CaptureError.OUTPUT))
        assertEquals(CaptureFailure.HAPTIC_UNSUPPORTED, CaptureFailure.from(CaptureError.OUTPUT_UNSUPPORTED))
        assertEquals(CaptureFailure.CLEANUP, CaptureFailure.from(CaptureError.CLEANUP))
        assertEquals(CaptureFailure.entries.size, CaptureFailure.entries.map { it.code }.distinct().size)
        assertTrue(CaptureFailure.MIC_PERMISSION.openSettings)
        assertTrue(CaptureFailure.CLEANUP.restartRequired)
        assertTrue(CaptureFailure.HAPTIC_UNSUPPORTED.openDiagnostics)
    }

    @Test fun staleFailuresCannotReplaceNewSessionCodeAndCleanupKeepsItsStopToken() {
        val old = CaptureRuntime.requestStart()!!
        CaptureRuntime.finished(old)
        val token = CaptureRuntime.requestStart()!!
        val owner = MicrophoneCapture({ error("not started") }, {}, {})
        try {
            assertTrue(CaptureRuntime.attach(token, owner))
            CaptureRuntime.listening(token, false)
            assertFalse(CaptureRuntime.fail(old, "old", CaptureFailure.MIC_READ))
            assertNull(CaptureRuntime.failure.value)
            assertTrue(CaptureRuntime.fail(token, "read failed", CaptureFailure.MIC_READ))
            assertTrue(CaptureRuntime.fail(token, "unsafe release", CaptureFailure.CLEANUP))
            CaptureRuntime.finished(token)
            assertEquals(CaptureFailure.CLEANUP, CaptureRuntime.failure.value)
            assertEquals(token, CaptureRuntime.stopToken())
            assertFalse(CaptureRuntime.canStart())
            assertFalse(CaptureRuntime.fail(null, "unrelated", CaptureFailure.MIC_PERMISSION))
            assertEquals(CaptureFailure.CLEANUP, CaptureRuntime.failure.value)
        } finally { CaptureRuntime.detach(owner); CaptureRuntime.finished(token) }
        assertNull(CaptureRuntime.stopToken())
        val retry = CaptureRuntime.requestStart()!!
        assertNull(CaptureRuntime.failure.value)
        CaptureRuntime.finished(retry)
    }
}
