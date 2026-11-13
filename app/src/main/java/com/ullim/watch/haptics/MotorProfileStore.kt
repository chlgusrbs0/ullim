package com.ullim.watch.haptics

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import com.ullim.watch.dsp.FrameSpectrum
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Private numeric calibration, invalidated by firmware changes; never called from DSP. */
object MotorProfileStore {
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "motor-noise-v2.bin"))

    fun deviceKey(context: Context): String {
        val intensityKeys = listOf("haptic_feedback_intensity", "VIB_FEEDBACK_MAGNITUDE", "VIB_RECVCALL_MAGNITUDE", "SEM_VIBRATION_NOTIFICATION_INTENSITY")
        val values = intensityKeys.joinToString("|") { key ->
            runCatching { Settings.System.getString(context.contentResolver, key) }.getOrNull() ?: "-"
        }
        return "${Build.MODEL}|${Build.FINGERPRINT}|${Build.VERSION.INCREMENTAL}|$values"
    }

    fun load(context: Context, firmwareKey: String): MotorNoiseProfile? = try {
        DataInputStream(file(context).openRead()).use { input ->
            check(input.readUTF() == "ULLIM_MOTOR_2")
            check(input.readUTF() == firmwareKey)
            check(input.readInt() == 16000 && input.readInt() == FrameSpectrum.SIZE)
            val pulseFallback = input.readBoolean()
            val low = DoubleArray(FrameSpectrum.BINS) { input.readDouble() }
            val high = DoubleArray(FrameSpectrum.BINS) { input.readDouble() }
            check(input.read() == -1)
            MotorNoiseProfile(low, high, pulseFallback)
        }
    } catch (_: Exception) { null }

    fun save(context: Context, firmwareKey: String, profile: MotorNoiseProfile): Boolean {
        val target = file(context)
        val stream = try { target.startWrite() } catch (_: Exception) { return false }
        return try {
            val output = DataOutputStream(stream)
            output.writeUTF("ULLIM_MOTOR_2")
            output.writeUTF(firmwareKey)
            output.writeInt(16000)
            output.writeInt(FrameSpectrum.SIZE)
            output.writeBoolean(profile.pulseFallback)
            profile.lowPowerCopy().forEach(output::writeDouble)
            profile.highPowerCopy().forEach(output::writeDouble)
            output.flush()
            target.finishWrite(stream)
            true
        } catch (_: Exception) { target.failWrite(stream); false }
    }
}
