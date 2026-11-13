package com.ullim.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibratorManager
import com.ullim.watch.audio.AndroidPcmInput
import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.audio.CaptureStatus
import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.core.RenderResult
import com.ullim.watch.dsp.DspProcessor
import com.ullim.watch.haptics.AdaptiveHapticRenderer
import com.ullim.watch.haptics.AndroidEnvelopeDevice
import com.ullim.watch.haptics.EnvelopeHapticMapper
import com.ullim.watch.haptics.HapticScheduler
import com.ullim.watch.runtime.CaptureRuntime
import com.ullim.watch.runtime.ForegroundSession
import com.ullim.watch.runtime.ForegroundStart

class ListeningService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lifecycle: ForegroundSession? = null
    private var currentSession = 0L
    private var currentStartId = 0
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentStartId = startId
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            if (lifecycle == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopCapture()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val token = intent.getLongExtra(EXTRA_SESSION, 0)
        if (lifecycle != null) return START_NOT_STICKY
        if (!CaptureRuntime.isStarting(token) || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (token > 0) CaptureRuntime.fail(token, getString(R.string.microphone_permission_needed))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startCapture(token, startId)
        return START_NOT_STICKY
    }

    private fun startCapture(token: Long, startId: Int) {
        val device = AndroidEnvelopeDevice(getSystemService(VibratorManager::class.java).defaultVibrator)
        if (!device.capabilities.available) {
            CaptureRuntime.fail(token, getString(R.string.motor_unavailable))
            stopSelf(startId)
            return
        }
        val dsp = DspProcessor()
        val mapper = EnvelopeHapticMapper()
        val scheduler = HapticScheduler(AdaptiveHapticRenderer(device))
        var lastUiNs = -100_000_000L
        val fanout = AudioFanOut { frame ->
            val features = dsp.extract(frame)
            val command = mapper.map(features)
            scheduler.offer(command)
            check(scheduler.lastResult != RenderResult.FAILED && scheduler.lastResult != RenderResult.UNSUPPORTED)
            if (frame.timestampNs - lastUiNs >= 100_000_000L) {
                CaptureRuntime.publish(token, features.rms, command.commandedIntensity)
                lastUiNs = frame.timestampNs
            }
        }
        lateinit var capture: MicrophoneCapture
        capture = MicrophoneCapture({ AndroidPcmInput.create(this) }, { fanout.accept(it) }, { status ->
            mainHandler.post {
                when (status) {
                    CaptureStatus.Recording -> if (!stopping) CaptureRuntime.listening(token, !device.capabilities.amplitudeControl)
                    CaptureStatus.Stopped, is CaptureStatus.Failed -> {
                        CaptureRuntime.detach(capture)
                        if (status is CaptureStatus.Failed) CaptureRuntime.fail(token, getString(R.string.capture_failed))
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
        if (!CaptureRuntime.attach(capture)) {
            CaptureRuntime.fail(token, getString(R.string.capture_still_stopping))
            stopSelf(startId)
            return
        }
        currentSession = token
        stopping = false
        fanout.start(token)
        scheduler.begin(token)
        val session = ForegroundSession(
            capture,
            { startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) },
            { stopForeground(STOP_FOREGROUND_REMOVE) },
            { fanout.stop(); scheduler.stop(); dsp.reset(); mapper.reset() },
        )
        lifecycle = session
        val result = session.start(token)
        if (result != ForegroundStart.STARTED) {
            CaptureRuntime.detach(capture)
            CaptureRuntime.fail(token, getString(if (result == ForegroundStart.DENIED) R.string.microphone_permission_needed else R.string.service_start_failed))
            lifecycle = null
            stopSelf(startId)
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.listening_channel), NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false)
            setSound(null, null)
        })
        val stop = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
        lifecycle?.stop()
    }

    override fun onDestroy() {
        stopCapture()
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
