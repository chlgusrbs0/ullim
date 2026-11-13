package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.core.ListeningState
import com.ullim.watch.core.AiStatus
import org.junit.Assert.*
import org.junit.Test

class CaptureRuntimeTest {
    @Test fun aiStatusFollowsOnlyActiveSessionAndCannotResurrectAfterStop() {
        val first = CaptureRuntime.requestStart()!!
        try {
            CaptureRuntime.setAiEnabled(true)
            assertTrue(CaptureRuntime.aiEnabled.value)
            CaptureRuntime.aiStatus(first, AiStatus.STARTING)
            CaptureRuntime.listening(first, false)
            assertEquals(AiStatus.STARTING, CaptureRuntime.state.value.ai)
            CaptureRuntime.aiStatus(first, AiStatus.AVAILABLE)
            CaptureRuntime.stopping(first)
            assertEquals(AiStatus.DISABLED, CaptureRuntime.state.value.ai)
            CaptureRuntime.finished(first)
            CaptureRuntime.aiStatus(first, AiStatus.AVAILABLE)
            assertEquals(AiStatus.DISABLED, CaptureRuntime.state.value.ai)
            val second = CaptureRuntime.requestStart()!!
            CaptureRuntime.aiStatus(first, AiStatus.UNAVAILABLE)
            assertEquals(AiStatus.DISABLED, CaptureRuntime.state.value.ai)
            CaptureRuntime.finished(second)
        } finally { CaptureRuntime.setAiEnabled(false); CaptureRuntime.finished(first) }
    }

    @Test fun serviceDestructionAfterCompletedStopCannotResurrectStoppingState() {
        val first = CaptureRuntime.requestStart()!!
        try {
            CaptureRuntime.listening(first, false)
            CaptureRuntime.stopping(first)
            CaptureRuntime.finished(first)
            // Service.stopSelf() then invokes onDestroy() -> stopCapture() again.
            CaptureRuntime.stopping(first)
            assertEquals(ListeningState.Idle, CaptureRuntime.state.value.listening)
            val second = CaptureRuntime.requestStart()
            assertNotNull(second)
            if (second != null) CaptureRuntime.finished(second)
        } finally { CaptureRuntime.finished(first) }
    }

    @Test fun oldServiceCallbacksCannotOverwriteNewSessionOrReleaseDifferentMicrophone() {
        val first = CaptureRuntime.requestStart()!!
        assertNull(CaptureRuntime.requestStart())
        val owner = MicrophoneCapture({ error("not started") }, {}, {})
        val other = MicrophoneCapture({ error("not started") }, {}, {})
        assertTrue(CaptureRuntime.attach(owner))
        assertFalse(CaptureRuntime.attach(other))
        CaptureRuntime.listening(first, true)
        CaptureRuntime.publish(first, 0.2f, 0.5f)
        assertEquals(0.5f, CaptureRuntime.levels.value.commanded, 0f)
        CaptureRuntime.stopping(first)
        CaptureRuntime.finished(first)
        assertNull(CaptureRuntime.requestStart())
        CaptureRuntime.detach(other)
        assertNull(CaptureRuntime.requestStart())
        CaptureRuntime.detach(owner)
        val second = CaptureRuntime.requestStart()!!
        assertTrue(second > first)
        CaptureRuntime.finished(first)
        CaptureRuntime.fail(first, "old failure")
        CaptureRuntime.publish(first, 1f, 1f)
        assertEquals(ListeningState.Starting(second), CaptureRuntime.state.value.listening)
        assertEquals(0f, CaptureRuntime.levels.value.commanded, 0f)
        CaptureRuntime.finished(second)
    }
}
