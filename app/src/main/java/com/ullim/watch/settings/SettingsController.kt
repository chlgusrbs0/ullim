package com.ullim.watch.settings

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class SettingsError { READ, WRITE }
data class SettingsState(
    val values: UserSettings = UserSettings(),
    val ready: Boolean = false,
    val saving: Boolean = false,
    val error: SettingsError? = null,
)

/** Scope and callers share one main/control dispatcher; disk work never runs on it. */
class SettingsController(
    private val store: SettingsStore,
    scope: CoroutineScope,
    private val apply: (UserSettings) -> Unit,
) {
    private data class Save(val revision: Long, val values: UserSettings)
    private val mutableState = MutableStateFlow(SettingsState())
    val state = mutableState.asStateFlow()
    private val pending = Channel<Save>(Channel.CONFLATED)
    private var revision = 0L

    init {
        scope.launch {
            val loaded = try { SettingsState(withContext(Dispatchers.IO) { store.load() }, ready = true) }
            catch (_: IOException) { SettingsState(ready = true, error = SettingsError.READ) }
            apply(loaded.values)
            mutableState.value = loaded
        }
        scope.launch {
            for (request in pending) {
                val error = try {
                    withContext(Dispatchers.IO) { store.save(request.values) }
                    null
                } catch (_: IOException) { SettingsError.WRITE }
                if (request.revision == revision) mutableState.value = mutableState.value.copy(saving = false, error = error)
            }
        }
    }

    @MainThread fun setSensitivity(value: Float): Boolean = update(state.value.values.copy(sensitivity = value))
    @MainThread fun setAiEnabled(enabled: Boolean): Boolean = update(state.value.values.copy(aiEnabled = enabled))
    @MainThread fun setTuning(parameter: TuningParameter, value: Float): Boolean =
        update(state.value.values.copy(tuning = state.value.values.tuning.with(parameter, value)))
    @MainThread fun resetTuning(): Boolean = update(state.value.values.copy(tuning = MvpDefaults.tuning), force = true)
    @MainThread fun retrySave(): Boolean = update(state.value.values, force = true)
    @MainThread fun reset(): Boolean = update(UserSettings(), force = true)

    private fun update(values: UserSettings, force: Boolean = false): Boolean {
        if (!state.value.ready) return false
        if (!force && values == state.value.values) return true
        apply(values)
        mutableState.value = mutableState.value.copy(values = values, saving = true, error = null)
        pending.trySend(Save(++revision, values))
        return true
    }
}
