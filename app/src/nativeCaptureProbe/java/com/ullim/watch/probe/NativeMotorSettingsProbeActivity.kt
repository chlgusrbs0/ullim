package com.ullim.watch.probe

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ullim.watch.haptics.MotorProfileStore
import com.ullim.watch.haptics.MotorSettingsWatcher
import com.ullim.watch.haptics.MotorNoiseProfile
import com.ullim.watch.dsp.FrameSpectrum
import java.io.File
import java.security.MessageDigest

/** Public read-only settings registration; no microphone, motor or setting writes. */
class NativeMotorSettingsProbeActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var started = false
    private var finished = false
    private var observation: MotorSettingsWatcher? = null
    private var expectedKey = ""
    private var began = 0L
    private var cacheVerified = false
    private lateinit var label: TextView
    private val complete = Runnable {
        if (!resumed || !hasWindowFocus()) finish("CANCELLED")
        else try {
            check(MotorProfileStore.deviceKey(this) == expectedKey)
            verifySyntheticCache()
            check(MotorProfileStore.deviceKey(this) == expectedKey)
            finish("PASS")
        } catch (_: Exception) { finish("FAIL", "KEY_OR_CACHE") }
    }
    private fun verifySyntheticCache() {
        val profile = MotorNoiseProfile(DoubleArray(FrameSpectrum.BINS) { .0001 }, DoubleArray(FrameSpectrum.BINS) { .0002 }, pulseFallback = true)
        check(MotorProfileStore.save(this, expectedKey, profile))
        val file = File(noBackupFilesDir, "motor-noise-v2.bin")
        val before = file.readBytes()
        check(MotorProfileStore.invalidate(this))
        check(MotorProfileStore.load(this, expectedKey) == null)
        check(before.contentEquals(file.readBytes()))
        check(MotorProfileStore.save(this, expectedKey, profile))
        check(MotorProfileStore.load(this, expectedKey) != null)
        cacheVerified = true
        Log.i(TAG, "syntheticCache=true invalidationBlockedLoad=true numericBytesPreserved=true recalibrationCleared=true bytes=${before.size}")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this).apply {
            text = "보정 설정 확인 준비"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(label)
    }
    override fun onResume() { super.onResume(); resumed = true; maybeStart() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) maybeStart() else if (started) finish("CANCELLED")
    }
    override fun onPause() {
        resumed = false
        if (started) finish("CANCELLED")
        super.onPause()
    }
    override fun onDestroy() {
        if (started) finish("CANCELLED")
        super.onDestroy()
    }
    private fun maybeStart() {
        if (started || !resumed || !hasWindowFocus() || intent.action != "com.ullim.watch.PROBE_MOTOR_SETTINGS") return
        started = true
        began = System.nanoTime()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        label.text = "진동 설정 감시 확인 중"
        try {
            expectedKey = MotorProfileStore.deviceKey(this)
            val hash = MessageDigest.getInstance("SHA-256").digest(expectedKey.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val globalKnown = Settings.Global.getString(contentResolver, "setting_vibration_intensity") != null
            Log.i(TAG, "keySha256=$hash globalKnown=$globalKnown uriCount=${MotorProfileStore.settingUris().size} microphone=false motor=false settingsWritten=false")
            observation = MotorSettingsWatcher(this, expectedKey) { finish("FAIL", "SETTING_CHANGED") }
            if (observation!!.start()) {
                Log.i(TAG, "registered=true initialKeyMatched=true")
                handler.postDelayed(complete, 5_000)
            } else finish("FAIL", "REGISTRATION_OR_RECHECK")
        } catch (_: Exception) { finish("FAIL", "SETTINGS_ACCESS") }
    }
    private fun finish(result: String, code: String = "NONE") {
        if (finished) return
        finished = true
        handler.removeCallbacks(complete)
        val cleanup = runCatching { observation?.close() }.isSuccess
        observation = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val finalResult = if (!cleanup && result == "PASS") "FAIL" else result
        label.text = if (finalResult == "PASS") "설정 감시 확인 완료" else "설정 감시 확인 종료"
        Log.i(TAG, "result=$finalResult code=${if (cleanup) code else "OBSERVER_CLEANUP"} unregisterReturned=$cleanup elapsedMs=${(System.nanoTime() - began) / 1_000_000} syntheticCache=$cacheVerified settingsWritten=false")
    }
    companion object { private const val TAG = "UllimMotorSettingsProbe" }
}
