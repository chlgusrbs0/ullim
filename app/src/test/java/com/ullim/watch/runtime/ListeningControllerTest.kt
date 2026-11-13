package com.ullim.watch.runtime

import com.ullim.watch.core.AiStatus
import com.ullim.watch.core.ListeningState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ListeningControllerTest {
    @Test fun concurrentStartsAdmitExactlyOneSession() {
        val controller = ListeningController()
        val gate = CountDownLatch(1)
        val callers = Executors.newFixedThreadPool(4)
        try {
            val results = (1..32).map { callers.submit<Long?> { gate.await(); controller.requestStart() } }
            gate.countDown()
            val admitted = results.mapNotNull { it.get(2, TimeUnit.SECONDS) }
            assertEquals(1, admitted.size)
            assertEquals(ListeningState.Starting(admitted.single()), controller.state.value.listening)
        } finally { callers.shutdownNow() }
    }

    @Test fun finishedCannotBecomeIdleUntilExactOwnerReleases() {
        val controller = ListeningController()
        val id = controller.requestStart()!!
        val owner = Any()
        assertTrue(controller.attach(id, owner))
        assertTrue(controller.listening(id))
        controller.aiStatus(id, AiStatus.AVAILABLE)
        controller.stopping(id)
        controller.finished(id)
        assertEquals(ListeningState.Stopping(id), controller.state.value.listening)
        assertEquals(AiStatus.DISABLED, controller.state.value.ai)
        assertNull(controller.requestStart())
        assertFalse(controller.detach(Any()))
        assertNull(controller.requestStart())
        assertTrue(controller.detach(owner))
        assertEquals(ListeningState.Idle, controller.state.value.listening)
        assertTrue(controller.requestStart()!! > id)
    }

    @Test fun stoppingBeforeAttachRejectsLateInitializationAndTerminalCallbacks() {
        val controller = ListeningController()
        val id = controller.requestStart()!!
        assertTrue(controller.stopping(id))
        assertFalse(controller.stopping(id))
        assertFalse(controller.attach(id, Any()))
        assertFalse(controller.listening(id))
        controller.finished(id)
        assertFalse(controller.stopping(id))
        assertFalse(controller.aiStatus(id, AiStatus.AVAILABLE))
        assertFalse(controller.fail(id, "late failure"))
        assertEquals(ListeningState.Idle, controller.state.value.listening)
    }

    @Test fun initializationErrorIsRetryableOnlyAfterReleaseAndOldCallbacksAreIgnored() {
        val controller = ListeningController()
        val first = controller.requestStart()!!
        val owner = Any()
        assertTrue(controller.attach(first, owner))
        assertTrue(controller.fail(first, "initialization"))
        controller.finished(first)
        assertEquals(ListeningState.Error("initialization"), controller.state.value.listening)
        assertNull(controller.requestStart())
        controller.detach(owner)
        val second = controller.requestStart()!!
        assertFalse(controller.attach(first, Any()))
        assertFalse(controller.listening(first))
        assertFalse(controller.aiStatus(first, AiStatus.UNAVAILABLE))
        assertFalse(controller.stopping(first))
        assertFalse(controller.finished(first))
        assertFalse(controller.fail(first, "old error"))
        assertEquals(ListeningState.Starting(second), controller.state.value.listening)
    }

    @Test fun unattributedUiFailureCannotInterruptAnActiveSession() {
        val controller = ListeningController()
        assertTrue(controller.fail(null, "permission"))
        val id = controller.requestStart()!!
        assertFalse(controller.fail(null, "old permission result"))
        assertTrue(controller.attach(id, Any()))
        controller.aiStatus(id, AiStatus.STARTING)
        controller.listening(id)
        assertTrue(controller.isListening(id))
        assertEquals(AiStatus.STARTING, controller.state.value.ai)
        assertFalse(controller.fail(null, "old UI failure"))
        assertEquals(ListeningState.Listening(id), controller.state.value.listening)
    }
}
