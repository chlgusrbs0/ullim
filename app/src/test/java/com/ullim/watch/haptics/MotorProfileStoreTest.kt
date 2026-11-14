package com.ullim.watch.haptics

import android.app.Application
import android.provider.Settings
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotorProfileStoreTest {
    @Test fun groupedOutputRequiresNewCalibrationWithoutDeletingTheOldNumericProfile() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val key = MotorProfileStore.deviceKey(context)
        val oldKey = key.removeSuffix("|pulse=grouped60-v1")
        val profile = MotorNoiseProfile(DoubleArray(257) { .0001 }, DoubleArray(257) { .0002 }, true)
        assertTrue(MotorProfileStore.save(context, oldKey, profile))
        assertNull(MotorProfileStore.load(context, key))
        assertNotNull(MotorProfileStore.load(context, oldKey))
        assertTrue(MotorProfileStore.save(context, key, profile))
        assertNotNull(MotorProfileStore.load(context, key))
    }

    @Test fun persistedInvalidationKeepsNumericProfileButRequiresANewSuccessfulCalibration() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val profile = MotorNoiseProfile(DoubleArray(257) { .0001 }, DoubleArray(257) { .0002 })
        assertTrue(MotorProfileStore.save(context, "firmware-a", profile))
        val numeric = File(context.noBackupFilesDir, "motor-noise-v2.bin")
        val before = numeric.readBytes()
        // Simulate a previous process invalidating this calibration, including a partial marker.
        val invalid = File(context.noBackupFilesDir, "motor-noise-v2.invalid")
        invalid.writeBytes(byteArrayOf())
        assertNull(MotorProfileStore.load(context, "firmware-a"))
        assertArrayEquals(before, numeric.readBytes())
        assertTrue(MotorProfileStore.save(context, "firmware-a", profile))
        assertFalse(invalid.exists())
        assertNotNull(MotorProfileStore.load(context, "firmware-a"))
    }

    @Test fun observedKeyNamesPreserveTheExistingStoredKeyRepresentation() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf("haptic_feedback_intensity", "VIB_FEEDBACK_MAGNITUDE", "VIB_RECVCALL_MAGNITUDE", "SEM_VIBRATION_NOTIFICATION_INTENSITY")
            .forEachIndexed { index, key -> Settings.System.putInt(context.contentResolver, key, index + 1) }
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 3)
        assertEquals("${Build.MODEL}|${Build.FINGERPRINT}|${Build.VERSION.INCREMENTAL}|1|2|3|4|globalIntensity=3|output=100|pulse=grouped60-v1", MotorProfileStore.deviceKey(context))
        assertEquals(5, MotorProfileStore.settingUris().distinct().size)
        assertTrue(MotorProfileStore.settingUris().contains(Settings.Global.getUriFor("setting_vibration_intensity")))
    }
    @Test fun changingTheSamsungGlobalIntensityInvalidatesAStoredMotorProfile() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 0)
        val key = MotorProfileStore.deviceKey(context)
        val profile = MotorNoiseProfile(DoubleArray(257) { .0001 }, DoubleArray(257) { .0002 }, pulseFallback = true)
        assertTrue(MotorProfileStore.save(context, key, profile))
        assertNotNull(MotorProfileStore.load(context, key))
        Settings.Global.putInt(context.contentResolver, "setting_vibration_intensity", 1)
        val changedKey = MotorProfileStore.deviceKey(context)
        assertNotEquals(key, changedKey)
        assertNull(MotorProfileStore.load(context, changedKey))
    }

    @Test fun privateNumericProfileRoundTripsAndFirmwareChangeInvalidatesIt() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val profile = MotorNoiseProfile(DoubleArray(257) { .0001 }, DoubleArray(257) { .0002 }, pulseFallback = true)
        assertTrue(MotorProfileStore.save(context, "firmware-a", profile))
        val restored = MotorProfileStore.load(context, "firmware-a")!!
        assertTrue(restored.pulseFallback)
        assertArrayEquals(profile.lowPowerCopy(), restored.lowPowerCopy(), 0.0)
        assertArrayEquals(profile.highPowerCopy(), restored.highPowerCopy(), 0.0)
        assertNull(MotorProfileStore.load(context, "firmware-b"))
    }

    @Test fun truncatedProfileIsUnavailableInsteadOfApplyingPartialNoiseBounds() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        File(context.noBackupFilesDir, "motor-noise-v2.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(MotorProfileStore.load(context, "firmware-a"))
    }
}
