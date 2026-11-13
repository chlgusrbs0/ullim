package com.ullim.watch.probe

import com.ullim.watch.ai.AiWorker
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.core.Classification
import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.SoundClassifier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class NativeInferenceProgressTest {
    @Test fun completedInferencesCannotHideAWorkerThatLaterBecomesIdleWithoutFault() {
        val now = AtomicLong(2_000_000_000L)
        val lane = Executors.newSingleThreadExecutor()
        val worker = AiWorker({ object : SoundClassifier {
            override fun classify(frame: AudioFrame): Classification {
                val end = frame.timestampNs + frame.durationNs
                return Classification(frame.sessionId, end, end + 1_500_000_000L, listOf(ClassScore("Static", .9f)))
            }
            override fun close() {}
        } }, lane, now::get)
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 2_000_000_000L
            while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(condition())
        }
        try {
            assertTrue(worker.start(1, true))
            await { worker.state.value.modelLoaded }
            val progress = NativeInferenceProgress { worker.state.value.successfulInferences }
            assertTrue(worker.offer(AudioFrame(1, 1_000_000_000L, ShortArray(15_600))))
            await { worker.state.value.successfulInferences == 1L }
            progress.checkpoint()
            now.addAndGet(60_000_000_000L)
            worker.tick()
            assertNull(worker.state.value.fault)
            val failure = assertThrows(IllegalStateException::class.java) { progress.checkpoint() }
            assertEquals("INFERENCE_PROGRESS_STOPPED", failure.message)
        } finally {
            worker.stop(1)
            try { await { !worker.state.value.busy && !worker.state.value.modelLoaded } }
            finally {
                lane.shutdown()
                assertTrue(lane.awaitTermination(2, TimeUnit.SECONDS))
            }
        }
    }
}
