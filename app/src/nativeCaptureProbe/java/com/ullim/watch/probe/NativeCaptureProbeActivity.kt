package com.ullim.watch.probe

import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.ullim.watch.audio.AndroidPcmInput
import com.ullim.watch.audio.CaptureError
import com.ullim.watch.audio.CaptureStatus
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.ui.theme.UllimTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/** Opt-in local verification UI. No files, network, haptic output, or PCM logging. */
class NativeCaptureProbeActivity : ComponentActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private val timers = Handler(Looper.getMainLooper())
    private val stats = ScalarStats()
    private val opens = AtomicInteger()
    private val releases = AtomicInteger()
    private var capture: MicrophoneCapture? = null
    private var began = false
    private var resumed = false
    private var startMs = 0L
    private var statusText by mutableStateOf("시험 준비 중")
    private val durationMs by lazy { intent.getLongExtra("durationMs", 60_000L).coerceIn(1_000L, 60_000L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UllimTheme {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("마이크 수집 시험", textAlign = TextAlign.Center)
                    Text(statusText, textAlign = TextAlign.Center)
                    Button(onClick = { requestStop("button") }, modifier = Modifier.fillMaxWidth()) {
                        Text("시험 중지")
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); resumed = true; maybeStart() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) maybeStart()
    }
    override fun onPause() {
        resumed = false
        requestStop("pause")
        super.onPause()
    }
    override fun onDestroy() { timers.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun maybeStart() {
        if (!resumed || !hasWindowFocus() || began) return
        began = true
        if (intent.action != ACTION || !lease.compareAndSet(false, true)) {
            statusText = "시험 요청 없음 또는 이미 사용 중"
            Log.i(TAG, "rejected actionOrLease")
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val owner = MicrophoneCapture(
            sourceFactory = {
                opens.incrementAndGet()
                val input = AndroidPcmInput.create(this)
                object : PcmInput by input {
                    override fun release() { input.release(); releases.incrementAndGet() }
                }
            },
            onFrame = stats::accept,
            onStatus = ::onCaptureStatus,
        )
        capture = owner
        Log.i(TAG, "begin durationMs=$durationMs")
        check(owner.start(1))
    }

    private fun onCaptureStatus(status: CaptureStatus) {
        if (status == CaptureStatus.Recording) {
            // Exercise the real owner's duplicate admission while it is definitely active.
            Log.i(TAG, "duplicateAccepted=${capture!!.start(2)}")
        }
        Log.i(TAG, "status=$status")
        ui.post {
            if (isDestroyed) return@post
            statusText = when (status) {
                CaptureStatus.Starting -> "마이크 준비 중"
                CaptureStatus.Recording -> "수집 중 · ${durationMs / 1000}초 후 자동 중지"
                CaptureStatus.Stopped -> "시험 종료 · 마이크 해제 확인 중"
                is CaptureStatus.Failed -> "시험 결과: ${status.reason}"
            }
            if (status == CaptureStatus.Recording && resumed) {
                startMs = SystemClock.elapsedRealtime()
                timers.postDelayed({ requestStop("timer") }, durationMs)
                timers.postDelayed(::reportProgress, 200)
            }
        }
        if (status == CaptureStatus.Stopped || status is CaptureStatus.Failed) {
            timers.removeCallbacksAndMessages(null)
            // Terminal callback precedes owner admission reset. Wait off the UI/capture thread.
            Thread({
                val done = capture!!.awaitStopped(2_000)
                val safe = done && !(status is CaptureStatus.Failed && status.reason == CaptureError.CLEANUP)
                if (safe) lease.set(false)
                Log.i(TAG, "terminal done=$done leaseReleased=$safe opens=${opens.get()} releases=${releases.get()} ${stats.snapshot()}")
                ui.post {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (!isDestroyed && status == CaptureStatus.Stopped) statusText = "시험 종료 · 마이크 해제 $safe"
                }
            }, "ullim-probe-terminal").apply { isDaemon = true }.start()
        }
    }

    private fun reportProgress() {
        if (!resumed) return
        val configurations = getSystemService(AudioManager::class.java).activeRecordingConfigurations
        val formats = configurations.joinToString(";") { config ->
            val format = config.clientFormat
            "clientHz=${format.sampleRate},channels=${format.channelCount},encoding=${format.encoding},source=${config.clientAudioSource},silenced=${config.isClientSilenced}"
        }
        Log.i(TAG, "progress elapsedMs=${SystemClock.elapsedRealtime() - startMs} $formats ${stats.snapshot()}")
        timers.postDelayed(::reportProgress, 10_000)
    }

    private fun requestStop(reason: String) {
        timers.removeCallbacksAndMessages(null)
        if (capture != null) Log.i(TAG, "stop reason=$reason elapsedMs=${if (startMs == 0L) 0 else SystemClock.elapsedRealtime() - startMs}")
        capture?.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private class ScalarStats {
        private var frames = 0L
        private var samples = 0L
        private var expectedNs: Long? = null
        private var gaps = 0L
        private var minRms = Double.POSITIVE_INFINITY
        private var maxRms = 0.0

        @Synchronized fun accept(frame: AudioFrame) {
            if (expectedNs != null && frame.timestampNs != expectedNs) gaps++
            expectedNs = frame.timestampNs + frame.durationNs
            var power = 0.0
            for (i in 0 until frame.sampleCount) {
                val value = frame.normalizedAt(i).toDouble()
                power += value * value
            }
            val rms = sqrt(power / frame.sampleCount)
            minRms = minOf(minRms, rms)
            maxRms = maxOf(maxRms, rms)
            frames++
            samples += frame.sampleCount
        }
        @Synchronized fun snapshot(): String = String.format(Locale.US,
            "frames=%d samples=%d timestampGaps=%d rmsMin=%.7f rmsMax=%.7f",
            frames, samples, gaps, if (frames == 0L) 0.0 else minRms, maxRms)
    }

    companion object {
        const val ACTION = "com.ullim.watch.PROBE_CAPTURE"
        private const val TAG = "UllimCaptureProbe"
        private val lease = AtomicBoolean()
    }
}
