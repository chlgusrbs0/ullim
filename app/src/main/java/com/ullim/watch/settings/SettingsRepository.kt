package com.ullim.watch.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.io.File

data class UserSettings(val sensitivity: Float = MvpDefaults.SENSITIVITY, val aiEnabled: Boolean = MvpDefaults.AI_ENABLED,
    val tuning: TuningSettings = MvpDefaults.tuning) {
    init { require(sensitivity.isFinite() && sensitivity in .75f..1.25f) }
}

interface SettingsStore {
    suspend fun load(): UserSettings
    suspend fun save(settings: UserSettings)
}

class SettingsRepository(private val store: DataStore<Preferences>) : SettingsStore {
    override suspend fun load(): UserSettings = decode(store.data.first())
    override suspend fun save(settings: UserSettings) {
        store.edit { values ->
            values[intPreferencesKey("schema")] = 1
            values[floatPreferencesKey("sensitivity")] = settings.sensitivity
            values[booleanPreferencesKey("ai_enabled")] = settings.aiEnabled
            TuningParameter.entries.forEach { values[floatPreferencesKey(it.key)] = settings.tuning[it] }
        }
    }

    companion object {
        fun createStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            )

        fun decode(preferences: Preferences): UserSettings {
            // Reading raw values by name also tolerates incorrectly typed old keys.
            val values = preferences.asMap().entries.associate { it.key.name to it.value }
            val schema = values["schema"]
            if (schema != null && schema != 1) return UserSettings()
            val sensitivity = (values["sensitivity"] as? Float)
                ?.takeIf { it.isFinite() && it in .75f..1.25f } ?: MvpDefaults.SENSITIVITY
            return UserSettings(sensitivity, values["ai_enabled"] as? Boolean ?: MvpDefaults.AI_ENABLED,
                TuningSettings.decode(fallback = MvpDefaults.tuning) { parameter ->
                    (values[parameter.key] as? Float)?.takeIf { it.isFinite() && it in parameter.minimum..parameter.maximum }
                        ?: MvpDefaults.tuning[parameter]
                })
        }
    }
}
