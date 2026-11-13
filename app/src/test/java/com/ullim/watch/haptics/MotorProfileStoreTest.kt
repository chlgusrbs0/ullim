package com.ullim.watch.haptics

import android.app.Application
import android.provider.Settings
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
