package com.ullim.watch.probe

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.ui.theme.UllimTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Synthetic-only native verification; never opens the microphone or vibrator. */
class NativeFanOutProbeActivity : ComponentActivity() {
    private val cancelled = AtomicBoolean()
    private var started = false
    private var worker: Thread? = null
    private var message by mutableStateOf("오디오 분배 자동 시험 준비")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UllimTheme {
                Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
                    Text(message, textAlign = TextAlign.Center)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { cancel(); return }
        if (started || intent.action != "com.ullim.watch.PROBE_FANOUT") return
        started = true
        cancelled.set(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        message = "오디오 분배 자동 시험 중"
        worker = Thread({
            try {
                verify()
                Log.i(TAG, "result=PASS")
                runOnUiThread { message = "오디오 분배 시험 통과" }
            } catch (_: InterruptedException) {
                Log.i(TAG, "result=CANCELLED")
            } catch (failure: Exception) {
                Log.i(TAG, "result=FAIL code=${failure.message}")
                runOnUiThread { message = "오디오 분배 시험 실패" }
            } finally {
                runOnUiThread { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
        }, "ullim-fanout-probe").apply { start() }
    }

    override fun onPause() { cancel(); super.onPause() }
    private fun cancel() { cancelled.set(true); worker?.interrupt() }
    private fun checkActive() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun usedHeap(): Long {
        System.gc()
        System.runFinalization()
        Thread.sleep(100)
        System.gc()
        return Runtime.getRuntime().run { totalMemory() - freeMemory() }
    }

    private fun verify() {
        val count = AtomicInteger()
        val fanout = AudioFanOut { count.incrementAndGet() }
        val pcm = ShortArray(320)
        val heaps = mutableListOf<Long>()
        fanout.start(1)
        // First burst warms class/JIT state; the following three measure retained Java heap.
        repeat(4) { run ->
            checkActive()
            val before = System.nanoTime()
            repeat(30_000) { index ->
                checkActive()
                val globalIndex = run * 30_000L + index
                check(fanout.accept(AudioFrame(1, globalIndex * 20_000_000L, pcm))) { "FRAME_REJECTED" }
            }
            check(count.get() == (run + 1) * 30_000) { "DSP_COUNT" }
            // Measure the still-active session without draining its AI slot or clearing history.
            val heap = usedHeap()
            heaps.add(heap)
            Log.i(TAG, "burst=$run frames=${count.get()} elapsedMs=${(System.nanoTime() - before) / 1_000_000} usedJavaHeap=$heap")
        }
        check(fanout.takeLatestAi()?.sampleCount == 15_600) { "AI_WINDOW" }
        check(fanout.takeLatestAi() == null) { "AI_QUEUE" }
        fanout.stop()
        val retainedGrowth = heaps.last() - heaps.first()
        check(retainedGrowth < 4 * 1024 * 1024) { "JAVA_HEAP_GROWTH" }
        Log.i(TAG, "javaHeapGrowth=$retainedGrowth limitBytes=4194304 simulatedMinutes=40")

        count.set(0)
        fanout.start(5)
        repeat(50) { fanout.accept(AudioFrame(5, it * 20_000_000L, ShortArray(320) { 7 })) }
        val taken = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val childFailure = AtomicReference<Throwable?>()
        var heldNs = 0L
        val ai = Thread({
            try {
                val snapshot = fanout.takeLatestAi() ?: error("AI_SNAPSHOT_MISSING")
                val began = System.nanoTime()
                taken.countDown()
                check(finish.await(5, TimeUnit.SECONDS)) { "AI_WAIT_TIMEOUT" }
                // The first snapshot is emitted at 15,600 samples; later input has not reached the next cadence.
                check(snapshot.sessionId == 5L && snapshot.timestampNs == 0L) { "AI_IDENTITY" }
                check((0 until snapshot.sampleCount).all { snapshot.sampleAt(it) == 7.toShort() }) { "AI_SNAPSHOT_CHANGED" }
                heldNs = System.nanoTime() - began
            } catch (failure: Throwable) { childFailure.set(failure) }
        }, "ullim-held-ai").apply { isDaemon = true; start() }
        var submitted = 0
        try {
            check(taken.await(2, TimeUnit.SECONDS)) { "AI_NOT_STARTED" }
            val began = System.nanoTime()
            do {
                checkActive()
                check(fanout.accept(AudioFrame(5, 1_000_000_000L + submitted * 20_000_000L, pcm))) { "STALL_REJECTED" }
                submitted++
                Thread.sleep(20)
            } while (System.nanoTime() - began < 2_000_000_000L)
            check(submitted >= 20 && count.get() == 50 + submitted) { "STALL_DSP_COUNT" }
        } finally {
            finish.countDown()
            ai.join(2_000)
            fanout.stop()
        }
        check(!ai.isAlive && heldNs >= 2_000_000_000L) { "AI_STALL_DURATION" }
        check(childFailure.get() == null) { "AI_SNAPSHOT_FAILED" }
        Log.i(TAG, "aiHeldMs=${heldNs / 1_000_000} dspFramesDuringStall=$submitted")

        var fresh = true
        val restarted = AudioFanOut { frame ->
            for (index in 0 until frame.sampleCount) fresh = fresh && frame.sampleAt(index) == 2.toShort()
        }
        restarted.start(1)
        restarted.accept(AudioFrame(1, 0, ShortArray(100) { 1 }))
        restarted.stop()
        restarted.start(2)
        check(!restarted.accept(AudioFrame(1, 6_250_000, pcm))) { "OLD_SESSION" }
        check(restarted.accept(AudioFrame(2, 0, ShortArray(15_600) { 2 }))) { "NEW_SESSION" }
        check(fresh) { "OLD_PARTIAL" }
        val snapshot = restarted.takeLatestAi() ?: error("NO_NEW_WINDOW")
        check((0 until snapshot.sampleCount).all { snapshot.sampleAt(it) == 2.toShort() }) { "OLD_AI_AUDIO" }
        restarted.stop()
        check(restarted.takeLatestAi() == null) { "STOPPED_AI_AUDIO" }
        Log.i(TAG, "restartFresh=true stoppedWindowCleared=true")
    }

    companion object { private const val TAG = "UllimFanOutProbe" }
}
