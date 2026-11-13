package com.ullim.watch.probe

import com.ullim.watch.ai.AiWorker
import com.ullim.watch.core.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class NativeAiOffCheckTest {
    @Test fun inferenceAlreadyInFlightMayCompleteDuringOffCleanup() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lane = Executors.newSingleThreadExecutor()
        val worker = AiWorker({ object : SoundClassifier {
            override fun classify(frame: AudioFrame): Classification {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                val end = frame.timestampNs + frame.durationNs
                return Classification(frame.sessionId, end, end + 1_500_000_000, listOf(ClassScore("Speech", .9f)))
            }
            override fun close() {}
        } }, lane, { 3_000_000_000L })
        try {
            assertTrue(worker.start(1, true))
            withTimeout(3_000) { while (!worker.state.value.modelLoaded) delay(2) }
            assertTrue(worker.offer(AudioFrame(1, 2_025_000_000L, ShortArray(15600))))
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val before = worker.state.value.successfulInferences
            val sequence = observeAiOff(worker, { worker.setEnabled(1, false) }, {
                release.countDown()
                withTimeout(3_000) { while (worker.state.value.let { it.modelLoaded || it.busy || it.cleanupPending }) delay(2) }
            }, { 99L })
            assertEquals(99L, sequence)
            assertEquals(before + 1, worker.state.value.successfulInferences)
            assertNull(worker.current(1, 3_000_000_000L))
        } finally { release.countDown(); worker.stop(1); lane.shutdownNow(); lane.awaitTermination(3, TimeUnit.SECONDS) }
    }
}
