package com.ullim.watch.ai

import com.ullim.watch.core.*
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.audio.AudioFanOut
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class AiWorkerTest {
    private class Engine(private val run: (AudioFrame) -> Unit = {}) : SoundClassifier {
        val calls = CopyOnWriteArrayList<Long>()
        val closed = AtomicInteger()
        private val active = AtomicBoolean()
        override fun classify(frame: AudioFrame): Classification {
            check(active.compareAndSet(false, true))
            try {
                calls.add(frame.timestampNs + frame.durationNs)
                run(frame)
                val end = frame.timestampNs + frame.durationNs
                return Classification(frame.sessionId, end, end + 1_500_000_000L, listOf(ClassScore("Speech", .9f)))
            } finally { active.set(false) }
        }
        override fun close() { check(!active.get()) { "close overlapped inference" }; closed.incrementAndGet() }
    }
    private fun frame(end: Long = 3_000_000_000L, session: Long = 1) =
        AudioFrame(session, end - 975_000_000L, ShortArray(15600))
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("condition did not complete", condition())
    }
    private fun fixture(factory: () -> SoundClassifier, body: (AiWorker, AtomicLong) -> Unit) {
        val clock = AtomicLong(3_000_000_000L)
        val lane = Executors.newSingleThreadExecutor { task -> Thread(task, "test-ai").apply { isDaemon = true } }
        val worker = AiWorker(factory, lane, clock::get)
        try { body(worker, clock) } finally {
            worker.stop(worker.state.value.sessionId)
            lane.shutdown()
            assertTrue(lane.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test fun blockedInferenceKeepsOnlyLatestPendingWindowAndDoesNotBlockDsp() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val engine = Engine { if (count.incrementAndGet() == 1) { entered.countDown(); check(release.await(2, TimeUnit.SECONDS)) } }
        fixture({ engine }) { worker, clock ->
            try {
                assertTrue(worker.start(1, true))
                await { worker.state.value.modelLoaded }
                assertTrue(worker.offer(frame(2_000_000_000)))
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                assertTrue(worker.offer(frame(2_500_000_000)))
                assertTrue(worker.offer(frame()))
                repeat(100) { assertFalse(worker.offer(frame())) }
                val dsp = DspProcessor()
                repeat(100) { index ->
                    val input = AudioFrame(1, index * 20_000_000L, ShortArray(320))
                    assertTrue(dsp.extract(input).isSilent)
                    assertNull(worker.current(1, clock.get()))
                }
                assertEquals(1, engine.calls.size)
                release.countDown()
                await { engine.calls.size == 2 && !worker.state.value.busy }
                assertEquals(listOf(2_000_000_000L, 3_000_000_000L), engine.calls.toList())
                assertEquals(2L, worker.state.value.successfulInferences)
                assertTrue(worker.state.value.droppedWindows >= 1)
                assertEquals("Speech", worker.current(1, clock.get())!!.scores.first().label)
            } finally { release.countDown() }
        }
    }

    @Test fun expiredResultMetadataSurvivesClearingAndResetsOnNextResultOffAndSession() {
        fixture({ Engine() }) { worker, clock ->
            worker.start(1, true)
            await { worker.state.value.modelLoaded && !worker.state.value.busy }
            assertTrue(worker.offer(frame()))
            await { worker.state.value.classification != null && !worker.state.value.busy }
            clock.set(4_500_000_000)
            worker.tick()
            assertNull(worker.state.value.classification)
            assertTrue(worker.state.value.resultExpired)
            clock.set(5_000_000_000)
            assertTrue(worker.offer(frame(5_000_000_000)))
            await { worker.state.value.classification != null && !worker.state.value.busy }
            assertFalse(worker.state.value.resultExpired)
            clock.set(6_500_000_000); worker.tick()
            assertTrue(worker.state.value.resultExpired)
            worker.setEnabled(1, false)
            assertFalse(worker.state.value.resultExpired)
            worker.stop(1); worker.start(2, false)
            assertFalse(worker.state.value.resultExpired)
        }
    }

    @Test fun offClearsContextImmediatelyButClosesOnlyAfterInferenceReturns() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine = Engine { entered.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
        fixture({ engine }) { worker, clock ->
            try {
                worker.start(1, true); await { worker.state.value.modelLoaded }
                worker.offer(frame()); assertTrue(entered.await(1, TimeUnit.SECONDS))
                worker.setEnabled(1, false)
                assertEquals(AiPhase.DISABLED, worker.state.value.phase)
                assertTrue(worker.state.value.cleanupPending)
                assertNull(worker.current(1, clock.get()))
                assertEquals(0, engine.closed.get())
                release.countDown()
                await { engine.closed.get() == 1 && !worker.state.value.cleanupPending }
                assertFalse(worker.state.value.modelLoaded)
                assertFalse(worker.offer(frame(3_500_000_000)))
            } finally { release.countDown() }
        }
    }

    @Test fun oldSessionFailureAndStopCannotOverwriteNewSessionAfterBlockedWork() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val old = Engine { entered.countDown(); check(release.await(2, TimeUnit.SECONDS)); error("old session") }
        val fresh = Engine()
        val loads = AtomicInteger()
        fixture({ if (loads.incrementAndGet() == 1) old else fresh }) { worker, clock ->
            try {
                worker.start(1, true); await { worker.state.value.modelLoaded }
                worker.offer(frame()); assertTrue(entered.await(1, TimeUnit.SECONDS))
                worker.setEnabled(1, false); worker.setEnabled(1, true)
                worker.stop(1)
                assertTrue(worker.start(2, true))
                worker.stop(1)
                clock.set(3_500_000_000)
                worker.offer(frame(clock.get(), 2))
                release.countDown()
                await { fresh.calls.size == 1 && !worker.state.value.busy }
                assertEquals(1, old.closed.get())
                assertEquals(2, loads.get())
                assertEquals(AiPhase.LISTENING, worker.state.value.phase)
                assertNull(worker.state.value.fault)
                assertEquals(2L, worker.current(2, clock.get())!!.sessionId)
                assertNull(worker.current(1, clock.get()))
            } finally { release.countDown() }
        }
    }

    @Test fun timeoutLatchesWithoutAutomaticRetryOrReplacementWorker() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked = Engine { entered.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
        val fresh = Engine()
        val loads = AtomicInteger()
        fixture({ if (loads.incrementAndGet() == 1) blocked else fresh }) { worker, clock ->
            try {
                worker.start(1, true); await { worker.state.value.modelLoaded }
                worker.offer(frame()); assertTrue(entered.await(1, TimeUnit.SECONDS))
                clock.set(3_600_000_000); worker.tick()
                assertEquals(AiPhase.DELAYED, worker.state.value.phase)
                clock.set(4_100_000_000); worker.tick()
                assertEquals(AiPhase.FAILED, worker.state.value.phase)
                assertEquals(AiFault.INFERENCE_TIMEOUT, worker.state.value.fault)
                repeat(10) { worker.setEnabled(1, true); worker.tick() }
                assertEquals(1, loads.get())
                assertTrue(worker.state.value.cleanupPending)
                release.countDown()
                await { blocked.closed.get() == 1 && !worker.state.value.busy }
                assertNull(worker.current(1, clock.get()))
                assertFalse(worker.offer(frame(clock.get())))
                worker.setEnabled(1, false); worker.setEnabled(1, true)
                await { loads.get() == 2 && worker.state.value.modelLoaded }
                assertEquals(AiPhase.LISTENING, worker.state.value.phase)
            } finally { release.countDown() }
        }
    }

    @Test fun expiredLabelsDisappearEvenWhenNoNewWindowArrives() {
        val engine = Engine()
        fixture({ engine }) { worker, clock ->
            worker.start(1, true); await { worker.state.value.modelLoaded }
            worker.offer(frame(2_000_000_000)); await { engine.calls.size == 1 && !worker.state.value.busy }
            worker.offer(frame(2_500_000_000)); await { engine.calls.size == 2 && !worker.state.value.busy }
            assertEquals("Speech", worker.current(1, clock.get())!!.scores.first().label)
            clock.set(4_000_000_000); worker.tick()
            assertNull(worker.current(1, clock.get()))
            assertNull(worker.state.value.classification)
            assertEquals(AiPhase.LISTENING, worker.state.value.phase)
        }
    }

    @Test fun disabledFactoryFailureAndInferenceFailureNeverAutoRetry() {
        val loads = AtomicInteger()
        fixture({ loads.incrementAndGet(); error("load failed") }) { worker, _ ->
            worker.start(1, false)
            assertFalse(worker.offer(frame()))
            assertEquals(0, loads.get())
            worker.setEnabled(1, true)
            await { worker.state.value.phase == AiPhase.FAILED }
            assertEquals(AiFault.LOAD, worker.state.value.fault)
            repeat(20) { worker.setEnabled(1, true); worker.tick() }
            assertEquals(1, loads.get())
        }
        val engine = Engine { error("inference failed") }
        fixture({ engine }) { worker, _ ->
            worker.start(1, true); await { worker.state.value.modelLoaded }
            worker.offer(frame())
            await { worker.state.value.phase == AiPhase.FAILED && engine.closed.get() == 1 }
            assertEquals(AiFault.INFERENCE, worker.state.value.fault)
            assertEquals(1L, worker.state.value.inferenceCount)
            assertEquals(0L, worker.state.value.successfulInferences)
            assertEquals(1L, worker.state.value.faultCount)
            assertFalse(worker.offer(frame(3_500_000_000)))
            assertEquals(1, engine.calls.size)
        }
    }

    @Test fun rejectedExecutionFailsAiWithoutRunningFactory() {
        val worker = AiWorker({ error("must not load") }, { throw RejectedExecutionException() }, { 0 })
        worker.start(1, true)
        assertEquals(AiPhase.FAILED, worker.state.value.phase)
        assertEquals(AiFault.EXECUTOR, worker.state.value.fault)
        assertFalse(worker.state.value.busy)
        assertNull(worker.current(1, 0))
    }

    @Test fun faultSurvivesMicrophoneRestartUntilExplicitAiModeChange() {
        val loads = AtomicInteger()
        fixture({ loads.incrementAndGet(); error("load failed") }) { worker, _ ->
            worker.start(1, true)
            await { worker.state.value.phase == AiPhase.FAILED }
            worker.stop(1)
            worker.start(2, true)
            assertEquals(AiPhase.FAILED, worker.state.value.phase)
            assertEquals(1, loads.get())
            worker.stop(2)
            // Explicit changes must also work while capture is idle.
            worker.setEnabled(2, false)
            worker.setEnabled(2, true)
            worker.start(3, true)
            await { loads.get() == 2 && worker.state.value.phase == AiPhase.FAILED }
        }
    }

    @Test fun disabledAiDoesNotRequireAnExecutorOrTurnOffIntoAnError() {
        val worker = AiWorker({ error("must not load") }, { throw RejectedExecutionException() }, { 0 })
        worker.start(1, false)
        assertEquals(AiPhase.DISABLED, worker.state.value.phase)
        assertNull(worker.state.value.fault)
        worker.setEnabled(1, true)
        assertEquals(AiPhase.FAILED, worker.state.value.phase)
        worker.setEnabled(1, false)
        assertEquals(AiPhase.DISABLED, worker.state.value.phase)
        assertNull(worker.state.value.fault)
    }

    @Test fun blockedLoadTimesOutAndCannotSpawnAnotherLoadBehindIt() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loads = AtomicInteger()
        val engine = Engine()
        fixture({ loads.incrementAndGet(); entered.countDown(); check(release.await(2, TimeUnit.SECONDS)); engine }) { worker, clock ->
            try {
                worker.start(1, true); assertTrue(entered.await(1, TimeUnit.SECONDS))
                clock.set(13_100_000_000); worker.tick()
                assertEquals(AiFault.LOAD_TIMEOUT, worker.state.value.fault)
                assertTrue(worker.state.value.cleanupPending)
                worker.setEnabled(1, false); worker.setEnabled(1, true)
                clock.set(23_200_000_000); worker.tick()
                assertEquals(AiFault.PREVIOUS_BUSY, worker.state.value.fault)
                assertTrue(worker.state.value.operationElapsedNs >= 20_000_000_000L)
                release.countDown()
                await { engine.closed.get() == 1 && !worker.state.value.busy }
                assertEquals(1, loads.get())
                assertFalse(worker.state.value.modelLoaded)
            } finally { release.countDown() }
        }
    }

    @Test fun blockedCloseKeepsCleanupPendingAndNeverLoadsConcurrently() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loads = AtomicInteger()
        val delegate = Engine()
        val engine = object : SoundClassifier {
            override fun classify(frame: AudioFrame) = delegate.classify(frame)
            override fun close() { entered.countDown(); check(release.await(2, TimeUnit.SECONDS)); delegate.close() }
        }
        fixture({ loads.incrementAndGet(); engine }) { worker, clock ->
            try {
                worker.start(1, true); await { worker.state.value.modelLoaded }
                worker.setEnabled(1, false); assertTrue(entered.await(1, TimeUnit.SECONDS))
                assertTrue(worker.state.value.cleanupPending)
                worker.setEnabled(1, true)
                clock.set(13_100_000_000); worker.tick()
                assertEquals(AiFault.PREVIOUS_BUSY, worker.state.value.fault)
                release.countDown()
                await { !worker.state.value.busy }
                assertEquals(1, loads.get())
                assertEquals(1, delegate.closed.get())
            } finally { release.countDown() }
        }
    }

    @Test fun closeFailurePoisonsFurtherModelLoadsAndDoesNotClaimFreedMemory() {
        val loads = AtomicInteger()
        val engine = object : SoundClassifier {
            override fun classify(frame: AudioFrame): Classification = error("unused")
            override fun close() { error("native close failed") }
        }
        fixture({ loads.incrementAndGet(); engine }) { worker, _ ->
            worker.start(1, true); await { worker.state.value.modelLoaded }
            worker.setEnabled(1, false)
            await { worker.state.value.fault == AiFault.CLEANUP && !worker.state.value.busy }
            assertTrue(worker.state.value.cleanupPending)
            assertTrue(worker.state.value.modelLoaded)
            worker.setEnabled(1, true); worker.stop(1); worker.start(2, true)
            assertEquals(AiPhase.FAILED, worker.state.value.phase)
            assertEquals(1, loads.get())
        }
    }

    @Test fun rejectedCleanupReportsRetainedModelWithoutRunningNativeCodeOnCaller() {
        val reject = AtomicBoolean()
        val engine = Engine()
        val lane = Executors.newSingleThreadExecutor { task -> Thread(task).apply { isDaemon = true } }
        val worker = AiWorker({ engine }, { task -> if (reject.get()) throw RejectedExecutionException() else lane.execute(task) }, { 0 })
        try {
            worker.start(1, true); await { worker.state.value.modelLoaded && !worker.state.value.busy }
            reject.set(true)
            worker.setEnabled(1, false)
            assertEquals(AiFault.EXECUTOR, worker.state.value.fault)
            assertTrue(worker.state.value.cleanupPending)
            assertTrue(worker.state.value.modelLoaded)
            assertFalse(worker.state.value.busy)
            assertEquals(0, engine.closed.get())
        } finally { lane.shutdown(); assertTrue(lane.awaitTermination(2, TimeUnit.SECONDS)) }
    }

    @Test fun inFlightAudioDeliveryCannotReenableAiAfterUserTurnsItOff() {
        val mode = AtomicBoolean(true)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = Engine()
        val loads = AtomicInteger()
        fixture({ if (loads.incrementAndGet() == 1) first else Engine() }) { worker, _ ->
            val fanout = AudioFanOut {
                entered.countDown()
                check(release.await(2, TimeUnit.SECONDS))
            }
            fanout.start(1)
            val feed = AiCaptureFeed(1, worker, fanout, mode::get)
            feed.start(); await { worker.state.value.modelLoaded }
            val audio = Executors.newSingleThreadExecutor { task -> Thread(task).apply { isDaemon = true } }
            try {
                val delivery = audio.submit { feed.accept(frame()) }
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                mode.set(false)
                feed.refreshMode()
                await { first.closed.get() == 1 }
                release.countDown()
                delivery.get(1, TimeUnit.SECONDS)
                assertEquals(AiPhase.DISABLED, worker.state.value.phase)
                assertEquals(1, loads.get())
                assertNull(worker.current(1, 3_000_000_000))
            } finally { release.countDown(); audio.shutdown(); assertTrue(audio.awaitTermination(2, TimeUnit.SECONDS)) }
        }
    }
}
