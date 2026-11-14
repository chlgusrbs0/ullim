package com.ullim.watch.settings

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @Test fun newInstallEnablesAiAndStoredOffChoiceIsRespected() {
        assertTrue(SettingsRepository.decode(emptyPreferences()).aiEnabled)
        assertFalse(SettingsRepository.decode(preferencesOf(booleanPreferencesKey("ai_enabled") to false)).aiEnabled)
    }

    @get:Rule val temporary = TemporaryFolder()
    private suspend fun <T> withStore(file: File, body: suspend (SettingsRepository) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try { return body(SettingsRepository(SettingsRepository.createStore(file, scope))) }
        finally { job.cancelAndJoin() }
    }

    @Test fun actualDataStoreSurvivesCloseReopenAndReset() = runBlocking {
        val file = File(temporary.root, "settings.preferences_pb")
        withStore(file) { repository ->
            assertEquals(UserSettings(), repository.load())
            repository.save(UserSettings(1.25f, true))
        }
        withStore(file) { repository ->
            assertEquals(UserSettings(1.25f, true), repository.load())
            repository.save(UserSettings())
        }
        withStore(file) { assertEquals(UserSettings(), it.load()) }
    }

    @Test fun detailedTuningSurvivesActualStoreReopenIncludingCoupledHighThresholds() = runBlocking {
        val file = File(temporary.root, "tuning.preferences_pb")
        val tuning = TuningSettings.DEFAULT.with(TuningParameter.LONG_END, .99f)
            .with(TuningParameter.LONG_START, .9f).with(TuningParameter.MIDDLE_OUTPUT, .32f)
            .with(TuningParameter.REFERENCE_RMS, .12f).with(TuningParameter.GAP_HOLD_MS, 60f)
        val expected = UserSettings(.75f, false, tuning)
        withStore(file) { it.save(expected) }
        withStore(file) {
            assertEquals(expected, it.load())
            it.save(expected.copy(tuning = TuningSettings.DEFAULT))
        }
        withStore(file) { assertEquals(UserSettings(.75f, false), it.load()) }
    }

    @Test fun corruptedActualPreferenceBytesRecoverToSafeDefaultsAndCanBeSaved() = runBlocking {
        val file = File(temporary.root, "bad.preferences_pb")
        file.writeBytes(byteArrayOf(-1, -1, -1, 0))
        withStore(file) { repository ->
            assertEquals(UserSettings(), repository.load())
            repository.save(UserSettings(.75f, false))
        }
        withStore(file) { assertEquals(UserSettings(.75f, false), it.load()) }
    }

    @Test fun wrongTypesInvalidNumbersAndFutureSchemaHaveSafeDefaults() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            assertEquals(1f, SettingsRepository.decode(preferencesOf(floatPreferencesKey("sensitivity") to value)).sensitivity)
        }
        val wrong = preferencesOf(stringPreferencesKey("sensitivity") to "loud", stringPreferencesKey("ai_enabled") to "yes")
        assertEquals(UserSettings(), SettingsRepository.decode(wrong))
        val future = preferencesOf(intPreferencesKey("schema") to 99, floatPreferencesKey("sensitivity") to 1.25f, booleanPreferencesKey("ai_enabled") to true)
        assertEquals(UserSettings(), SettingsRepository.decode(future))
        val oldMissingVersion = preferencesOf(floatPreferencesKey("sensitivity") to .75f, booleanPreferencesKey("ai_enabled") to true,
            stringPreferencesKey("unrelated") to "retained")
        assertEquals(UserSettings(.75f, true), SettingsRepository.decode(oldMissingVersion))
        assertThrows(IllegalArgumentException::class.java) { UserSettings(Float.NaN) }
    }
}
