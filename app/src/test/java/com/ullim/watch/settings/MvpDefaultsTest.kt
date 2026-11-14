package com.ullim.watch.settings

import androidx.datastore.preferences.core.*
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.haptics.PulseFallback
import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

class MvpDefaultsTest {
    private val captured = Properties().apply {
        requireNotNull(MvpDefaultsTest::class.java.getResourceAsStream("/final-mvp-settings.properties")).use { load(it) }
    }

    @Test fun freshInstallMatchesEveryCapturedWatchSettingExactly() {
        val fresh = SettingsRepository.decode(emptyPreferences())
        assertEquals(captured.getProperty("sensitivity").toFloat(), fresh.sensitivity, 0f)
        assertEquals(captured.getProperty("aiEnabled").toBoolean(), fresh.aiEnabled)
        assertEquals(37, captured.size)
        for (p in TuningParameter.entries) {
            assertEquals(p.name, captured.getProperty(p.name).toFloat().toRawBits(), fresh.tuning[p].toRawBits())
        }
    }

    @Test fun existingSavedChoicesIncludingOldDefaultValuesAreNeverReplaced() {
        val stored = mutablePreferencesOf(floatPreferencesKey("sensitivity") to .75f,
            booleanPreferencesKey("ai_enabled") to false)
        TuningParameter.entries.forEach { stored[floatPreferencesKey(it.key)] = TuningSettings.DEFAULT[it] }
        assertEquals(UserSettings(.75f, false, TuningSettings.DEFAULT), SettingsRepository.decode(stored))
    }

    @Test fun missingMalformedAndInvalidPairsUseFinalDefaultsWhileKeepingValidChoices() {
        val partial = preferencesOf(floatPreferencesKey(TuningParameter.REFERENCE_RMS.key) to .8f,
            floatPreferencesKey(TuningParameter.MIN_RMS.key) to Float.NaN)
        val decoded = SettingsRepository.decode(partial)
        assertEquals(.8f, decoded.tuning[TuningParameter.REFERENCE_RMS], 0f)
        assertEquals(MvpDefaults.tuning[TuningParameter.MIN_RMS], decoded.tuning[TuningParameter.MIN_RMS], 0f)
        val invalidPair = preferencesOf(floatPreferencesKey(TuningParameter.CLOSE_OFFSET.key) to .03f,
            floatPreferencesKey("sensitivity") to .75f, booleanPreferencesKey("ai_enabled") to false)
        assertEquals(UserSettings(.75f, false), SettingsRepository.decode(invalidPair))
    }

    @Test fun shippedPulseDefaultsPreserveBothExistingMotorCalibrationWaveforms() {
        for (level in listOf(.2f, 1f)) {
            val frames = (0..2).map { HapticFrame(1, it * 20_000_000L, 20, level) }
            val old = PulseFallback.encodeBatch(frames, TuningSettings.DEFAULT)
            val shipped = PulseFallback.encodeBatch(frames, UserSettings().tuning)
            assertArrayEquals(old.timings, shipped.timings)
            assertArrayEquals(old.amplitudes, shipped.amplitudes)
        }
    }
}
