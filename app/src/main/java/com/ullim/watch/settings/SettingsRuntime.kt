package com.ullim.watch.settings

import android.content.Context
import androidx.annotation.MainThread
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ullim.watch.ai.AiRuntime
import com.ullim.watch.runtime.CaptureRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One application-owned preference authority; no disk reads on the audio/UI caller. */
object SettingsRuntime {
    private var instance: SettingsController? = null

    @MainThread fun get(context: Context): SettingsController {
        instance?.let { return it }
        val application = context.applicationContext
        val store = SettingsRepository.createStore(application.preferencesDataStoreFile("ullim_settings"),
            CoroutineScope(SupervisorJob() + Dispatchers.IO))
        return SettingsController(SettingsRepository(store), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) { values ->
            CaptureRuntime.applySettings(values)
            AiRuntime.refreshEnabled(values.aiEnabled)
        }.also { instance = it }
    }
}
