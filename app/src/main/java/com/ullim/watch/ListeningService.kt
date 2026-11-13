package com.ullim.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibratorManager
import android.util.Log
import com.ullim.watch.audio.AndroidPcmInput
import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.audio.CaptureStatus
import com.ullim.watch.audio.CaptureError
import com.ullim.watch.audio.CaptureException
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.audio.PcmInput
import com.ullim.watch.ai.AiPhase
import com.ullim.watch.ai.AiFault
import com.ullim.watch.ai.AiSnapshot
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.ai.AiWorker
import com.ullim.watch.ai.AiCaptureFeed
import com.ullim.watch.core.AiStatus
import com.ullim.watch.core.RenderResult
import com.ullim.watch.dsp.LiveDsp
import com.ullim.watch.haptics.AdaptiveHapticRenderer
import com.ullim.watch.haptics.AndroidEnvelopeDevice
import com.ullim.watch.haptics.HybridHapticMapper
import com.ullim.watch.haptics.HapticScheduler
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.ForegroundSession
import com.ullim.watch.runtime.ForegroundStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class ListeningService : Service() {
    protected open fun createPcmInput(): PcmInput = AndroidPcmInput.create(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lifecycle: ForegroundSession? = null
    private var currentSession = 0L
    private var currentStartId = 0
    private var stopping = false
    private var aiWorker: AiWorker? = null
    private var aiFeed: AiCaptureFeed? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var aiObservation: Job? = null
    private var lastAiLogNs = -5_000_000_000L
    private var lastAiLogPhase: AiPhase? = null
    private var lastAiLogFault: AiFault? = null
    private val aiHeartbeat = object : Runnable {
        override fun run() {
            val token = currentSession
            if (token <= 0 || stopping) return
            aiFeed?.refreshMode()
            aiWorker?.tick()
            mainHandler.postDelayed(this, 100)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentStartId = startId
        if (intent?.action == ACTION_STOP) {
            val token = intent.getLongExtra(EXTRA_SESSION, 0)
            if (token > 0 && token == currentSession) {
                stopCapture()
                if (lifecycle == null) stopSelf(startId)
            } else if (currentSession == 0L && CaptureRuntime.isStarting(token)) {
                // A user can cancel before the START command has attached an owner.
                CaptureRuntime.stopping(token)
                CaptureRuntime.finished(token)
                stopSelf(startId)
            } else if (currentSession == 0L && CaptureRuntime.canStart()) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopCapture()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val token = intent.getLongExtra(EXTRA_SESSION, 0)
        if (lifecycle != null) return START_NOT_STICKY
        if (!CaptureRuntime.isStarting(token)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            reportCaptureFailure(this, token, CaptureFailure.MIC_PERMISSION)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startCapture(token, startId)
        return START_NOT_STICKY
    }

    private fun startCapture(token: Long, startId: Int) {
        val device = try { AndroidEnvelopeDevice(getSystemService(VibratorManager::class.java).defaultVibrator) }
        catch (_: RuntimeException) {
            reportCaptureFailure(this, token, CaptureFailure.HAPTIC_OUTPUT)
            stopSelf(startId)
            return
        }
        if (!device.capabilities.available) {
            reportCaptureFailure(this, token, if (device.capabilities.probeFailed) CaptureFailure.HAPTIC_OUTPUT else CaptureFailure.HAPTIC_UNSUPPORTED)
            stopSelf(startId)
            return
        }
        val dsp = LiveDsp { CaptureRuntime.sensitivity.value }
        val mapper = HybridHapticMapper()
        val ai = AiRuntime.worker(this)
        aiWorker = ai
        val scheduler = HapticScheduler(AdaptiveHapticRenderer(device))
        var lastUiNs = -100_000_000L
        val fanout = AudioFanOut { frame ->
            val features = dsp.extract(frame)
            val command = mapper.map(features, ai.current(token, frame.timestampNs))
            scheduler.offer(command)
            when (scheduler.lastResult) {
                RenderResult.FAILED -> throw CaptureException(CaptureError.OUTPUT)
                RenderResult.UNSUPPORTED -> throw CaptureException(CaptureError.OUTPUT_UNSUPPORTED)
                else -> Unit
            }
            CaptureRuntime.visualization.offer(features, command)
            if (frame.timestampNs - lastUiNs >= 100_000_000L) {
                CaptureRuntime.publish(token, features.rms, command.commandedIntensity)
                lastUiNs = frame.timestampNs
            }
        }
        val feed = AiCaptureFeed(token, ai, fanout) { CaptureRuntime.aiEnabled.value }
        aiFeed = feed
        lateinit var capture: MicrophoneCapture
        capture = MicrophoneCapture(::createPcmInput, { feed.accept(it) }, { status ->
            mainHandler.post {
                when (status) {
                    CaptureStatus.Recording -> if (!stopping) CaptureRuntime.listening(token, !device.capabilities.amplitudeControl)
                    CaptureStatus.Stopped, is CaptureStatus.Failed -> {
                        val cleanupFailed = status is CaptureStatus.Failed && status.reason == CaptureError.CLEANUP
                        if (!cleanupFailed) CaptureRuntime.detach(capture)
                        if (status is CaptureStatus.Failed) reportCaptureFailure(this, token, CaptureFailure.from(status.reason))
                        if (currentSession == token) {
                            lifecycle?.stop()
                            lifecycle = null
                            CaptureRuntime.finished(token)
                            currentSession = 0
                            stopSelf(currentStartId)
                        }
                    }
                    CaptureStatus.Starting -> Unit
                }
            }
        })
        try {
            fanout.start(token)
            scheduler.begin(token)
        } catch (_: RuntimeException) {
            fanout.stop()
            runCatching { scheduler.stop() }
            reportCaptureFailure(this, token, CaptureFailure.HAPTIC_OUTPUT)
            stopSelf(startId)
            return
        }
        if (!CaptureRuntime.attach(token, capture)) {
            fanout.stop()
            runCatching { scheduler.stop() }
            reportCaptureFailure(this, token, CaptureFailure.CLEANUP)
            stopSelf(startId)
            return
        }
        currentSession = token
        stopping = false
        val session = ForegroundSession(
            capture,
            { startForeground(NOTIFICATION_ID, notification(token), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) },
            { stopForeground(STOP_FOREGROUND_REMOVE) },
            { fanout.stop(); scheduler.stop(); dsp.reset(); mapper.reset(); stopAi(token) },
        )
        lifecycle = session
        val result = session.start(token)
        if (result != ForegroundStart.STARTED) {
            if (result != ForegroundStart.STILL_STOPPING) CaptureRuntime.detach(capture)
            reportCaptureFailure(this, token, when (result) {
                ForegroundStart.DENIED -> CaptureFailure.MIC_PERMISSION
                ForegroundStart.STILL_STOPPING -> CaptureFailure.CLEANUP
                else -> CaptureFailure.FOREGROUND_START
            })
            CaptureRuntime.finished(token)
            currentSession = 0
            lifecycle = null
            stopSelf(startId)
        } else {
            feed.start()
            aiObservation = serviceScope.launch {
                ai.state.collect { snapshot ->
                    if (snapshot.sessionId == token) {
                        val status = when (snapshot.phase) {
                            AiPhase.DISABLED -> AiStatus.DISABLED
                            AiPhase.LOADING -> AiStatus.STARTING
                            AiPhase.LISTENING -> AiStatus.AVAILABLE
                            AiPhase.DELAYED, AiPhase.FAILED -> AiStatus.UNAVAILABLE
                        }
                        CaptureRuntime.aiStatus(token, status)
                        logAi(snapshot)
                    }
                }
            }
            mainHandler.post(aiHeartbeat)
        }
    }

    internal fun notification(token: Long): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.listening_channel), NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false)
            setSound(null, null)
        })
        val stop = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java)
            .setAction(ACTION_STOP).setData(Uri.parse("ullim://session/$token/stop"))
            .putExtra(EXTRA_SESSION, token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.listening_notification)).setContentText(getString(R.string.local_audio_notice))
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification), getString(R.string.stop), stop).build()).build()
    }

    private fun stopCapture() {
        stopping = true
        if (currentSession > 0) CaptureRuntime.stopping(currentSession)
        stopAi(currentSession)
        lifecycle?.stop()
    }

    private fun stopAi(token: Long) {
        mainHandler.removeCallbacks(aiHeartbeat)
        aiObservation?.cancel()
        aiObservation = null
        aiWorker?.stop(token)
        aiFeed = null
        CaptureRuntime.aiStatus(token, AiStatus.DISABLED)
    }

    private fun logAi(snapshot: AiSnapshot) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val now = System.nanoTime()
        if (snapshot.phase == lastAiLogPhase && snapshot.fault == lastAiLogFault && now - lastAiLogNs < 5_000_000_000L) return
        lastAiLogNs = now
        lastAiLogPhase = snapshot.phase
        lastAiLogFault = snapshot.fault
        Log.i("UllimAI", "session=${snapshot.sessionId} phase=${snapshot.phase} fault=${snapshot.fault} code=${snapshot.fault?.let { "AI_${it.name}" } ?: "NONE"} " +
            "loaded=${snapshot.modelLoaded} busy=${snapshot.busy} cleanupPending=${snapshot.cleanupPending} " +
            "loadNs=${snapshot.loadDurationNs} inferNs=${snapshot.lastInferenceNs} maxInferNs=${snapshot.maxInferenceNs} " +
            "attempts=${snapshot.inferenceCount} validResults=${snapshot.successfulInferences} " +
            "faults=${snapshot.faultCount} timeouts=${snapshot.timeoutCount} operationNs=${snapshot.operationElapsedNs} dropped=${snapshot.droppedWindows}")
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ullim.watch.START"
        const val ACTION_STOP = "com.ullim.watch.STOP"
        const val EXTRA_SESSION = "session"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
    }
}
