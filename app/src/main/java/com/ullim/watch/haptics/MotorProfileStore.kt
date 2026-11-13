package com.ullim.watch.haptics

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import com.ullim.watch.dsp.FrameSpectrum
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Private numeric calibration, invalidated by firmware changes; never called from DSP. */
object MotorProfileStore {
    private val intensityKeys = listOf("haptic_feedback_intensity", "VIB_FEEDBACK_MAGNITUDE", "VIB_RECVCALL_MAGNITUDE", "SEM_VIBRATION_NOTIFICATION_INTENSITY")
    private const val GLOBAL_INTENSITY = "setting_vibration_intensity"
    private val invalidatedPaths = mutableSetOf<String>()
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "motor-noise-v2.bin"))
    private fun invalidationFile(context: Context) = File(context.noBackupFilesDir, "motor-noise-v2.invalid")

    internal fun settingUris(): List<Uri> = intensityKeys.map(Settings.System::getUriFor) + Settings.Global.getUriFor(GLOBAL_INTENSITY)

    /** Null means a parent/unrelated URI; false means a specific value cannot be verified. */
    internal fun settingValueKnown(resolver: ContentResolver, uri: Uri?): Boolean? {
        if (uri == Settings.Global.getUriFor(GLOBAL_INTENSITY)) {
            return runCatching { Settings.Global.getString(resolver, GLOBAL_INTENSITY) != null }.getOrDefault(false)
        }
        val key = intensityKeys.firstOrNull { Settings.System.getUriFor(it) == uri } ?: return null
        return runCatching { Settings.System.getString(resolver, key) != null }.getOrDefault(false)
    }

    fun deviceKey(context: Context): String = formatDeviceKey(
        { key -> runCatching { Settings.System.getString(context.contentResolver, key) }.getOrNull() },
        { key -> runCatching { Settings.Global.getString(context.contentResolver, key) }.getOrNull() },
    )

    internal fun checkedDeviceKey(
        context: Context,
        readSystem: (String) -> String? = { Settings.System.getString(context.contentResolver, it) },
        readGlobal: (String) -> String? = { Settings.Global.getString(context.contentResolver, it) },
    ): String = formatDeviceKey(readSystem, readGlobal)

    private fun formatDeviceKey(readSystem: (String) -> String?, readGlobal: (String) -> String?): String {
        val values = intensityKeys.joinToString("|") { readSystem(it) ?: "-" }
        val globalIntensity = readGlobal(GLOBAL_INTENSITY) ?: "-"
        return "${Build.MODEL}|${Build.FINGERPRINT}|${Build.VERSION.INCREMENTAL}|$values|globalIntensity=$globalIntensity"
    }

    @Synchronized fun load(context: Context, firmwareKey: String): MotorNoiseProfile? = try {
        if (context.noBackupFilesDir.absolutePath in invalidatedPaths || invalidationFile(context).exists()) null
        else DataInputStream(file(context).openRead()).use { input ->
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

    /** Keeps numeric calibration bytes; an empty/partial marker also fails closed after restart. */
    @Synchronized fun invalidate(context: Context): Boolean {
        val path = try { context.noBackupFilesDir.absolutePath } catch (_: Exception) { return false }
        invalidatedPaths.add(path)
        return try {
            invalidationFile(context).outputStream().use { stream -> stream.write(1); stream.fd.sync() }
            true
        } catch (_: Exception) { false } // In-process latch remains even if persistence fails.
    }

    @Synchronized fun save(context: Context, firmwareKey: String, profile: MotorNoiseProfile): Boolean {
        val target = file(context)
        val stream = try { target.startWrite() } catch (_: Exception) { return false }
        val saved = try {
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
        if (!saved) return false
        val cleared = runCatching {
            val invalid = invalidationFile(context)
            !invalid.exists() || invalid.delete()
        }.getOrDefault(false)
        if (cleared) invalidatedPaths.remove(context.noBackupFilesDir.absolutePath)
        return cleared
    }
}
