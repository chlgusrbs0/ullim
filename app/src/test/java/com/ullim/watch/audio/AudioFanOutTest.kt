package com.ullim.watch.audio

import com.ullim.watch.core.AudioFrame
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AudioFanOutTest {
    @Test fun partialReadsAssembleWithoutDroppingSamples() {
        val frames = mutableListOf<AudioFrame>()
        val fanout = AudioFanOut(frames::add)
        fanout.start(1)
        fanout.accept(AudioFrame(1, 0, ShortArray(100) { 1 }))
        fanout.accept(AudioFrame(1, 6_250_000, ShortArray(540) { 2 }))
        assertEquals(2, frames.size)
        assertEquals(0L, frames[0].timestampNs)
        assertEquals(20_000_000L, frames[1].timestampNs)
        assertEquals(1.toShort(), frames[0].sampleAt(99))
        assertEquals(2.toShort(), frames[0].sampleAt(100))
    }

    @Test fun stalledAiCannotBlockDspAndOnlyNewestWindowRemains() {
        var dspCount = 0
        val fanout = AudioFanOut { dspCount++ }
        fanout.start(1)
        repeat(150) { fanout.accept(AudioFrame(1, it * 20_000_000L, ShortArray(320) { it.toShort() })) }
        assertEquals(150, dspCount)
        val latest = fanout.takeLatestAi()!!
        assertEquals(15_600, latest.sampleCount)
        assertTrue(latest.timestampNs > 1_500_000_000L)
        assertNull(fanout.takeLatestAi())
    }

    @Test fun oldSessionStoppedDataAndDuplicateTimeAreRejected() {
        val frames = mutableListOf<AudioFrame>()
        val fanout = AudioFanOut(frames::add)
        fanout.start(1)
        fanout.accept(AudioFrame(1, 0, ShortArray(320)))
        assertFalse(fanout.accept(AudioFrame(1, 0, ShortArray(320))))
        fanout.start(2)
        assertFalse(fanout.accept(AudioFrame(1, 20_000_000, ShortArray(320))))
        assertNull(fanout.takeLatestAi())
        fanout.stop()
        assertFalse(fanout.accept(AudioFrame(2, 0, ShortArray(320))))
        assertEquals(1, frames.size)
    }

    @Test fun restartAndTimeGapCannotMixPartialOrAiAudio() {
        val frames = mutableListOf<AudioFrame>()
        val fanout = AudioFanOut(frames::add)
        fanout.start(1)
        fanout.accept(AudioFrame(1, 0, ShortArray(100) { 1 }))
        fanout.accept(AudioFrame(1, 1_000_000_000, ShortArray(320) { 2 }))
        assertTrue(frames.single().copySamples().all { it == 2.toShort() })
        fanout.start(2)
        fanout.accept(AudioFrame(2, 0, ShortArray(15_600) { 3 }))
        assertTrue(fanout.takeLatestAi()!!.copySamples().all { it == 3.toShort() })
    }

    @Test fun tenMinuteStreamKeepsEveryDspFrameWhileAiConsumerSleeps() {
        var count = 0
        val fanout = AudioFanOut { count++ }
        fanout.start(1)
        val input = ShortArray(320)
        val before = System.nanoTime()
        repeat(30_000) { fanout.accept(AudioFrame(1, it * 20_000_000L, input)) }
        val elapsedMs = (System.nanoTime() - before) / 1_000_000
        assertEquals(30_000, count)
        assertEquals(15_600, fanout.takeLatestAi()!!.sampleCount)
        assertTrue(elapsedMs < 10_000)
        println("10-minute synthetic PCM: DSP frames=$count, desktop processing=${elapsedMs}ms; fixed ring=15600 samples; pending AI windows<=1")
    }

    @Test fun aiConsumerDoesNotHoldDspLockWhileUsingSnapshot() {
        val observed = CopyOnWriteArrayList<AudioFrame>()
        val fanout = AudioFanOut(observed::add)
        fanout.start(1)
        fanout.accept(AudioFrame(1, 0, ShortArray(15_600)))
        val snapshotTaken = CountDownLatch(1)
        val finishAi = CountDownLatch(1)
        val ai = Thread {
            assertNotNull(fanout.takeLatestAi())
            snapshotTaken.countDown()
            finishAi.await(2, TimeUnit.SECONDS)
        }
        ai.start()
        assertTrue(snapshotTaken.await(2, TimeUnit.SECONDS))
        val before = observed.size
        fanout.accept(AudioFrame(1, 975_000_000, ShortArray(640)))
        assertEquals(before + 2, observed.size)
        finishAi.countDown()
        ai.join(2000)
        assertFalse(ai.isAlive)
    }
}
