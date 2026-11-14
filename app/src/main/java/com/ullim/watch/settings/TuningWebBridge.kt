package com.ullim.watch.settings

import com.ullim.watch.core.ListeningState
import com.ullim.watch.runtime.CaptureRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class TuningWebBridge(private val settings: SettingsController, private val stop: () -> Unit) {
    fun snapshot(): String {
        val state = settings.state.value
        val levels = CaptureRuntime.levels.value
        val parameters = JSONArray()
        TuningParameter.entries.forEach { p -> parameters.put(JSONObject().apply {
            put("name", p.name); put("label", p.label); put("help", p.help); put("group", p.group.label)
            put("min", p.minimum.toDouble()); put("max", p.maximum.toDouble()); put("step", p.step.toDouble())
            put("value", state.values.tuning[p].toDouble()); put("default", p.default.toDouble()); put("unit", p.unit.name)
        }) }
        val phase = CaptureRuntime.state.value.listening
        return JSONObject().apply {
            put("ready", state.ready); put("saving", state.saving); put("error", state.error?.name ?: JSONObject.NULL)
            put("parameters", parameters)
            put("listening", phase is ListeningState.Listening)
            put("phase", when(phase) { is ListeningState.Listening -> "소리 감지 중"; is ListeningState.Starting -> "시작 중"; is ListeningState.Stopping -> "중지 중"; is ListeningState.Error -> phase.reason; else -> "감지 중지 · 워치에서 시작해 주세요" })
            put("detected", levels.detected); put("raw", levels.rawSound.toDouble()); put("filtered", levels.sound.toDouble())
            put("level", levels.normalizedLevel.toDouble()); put("command", levels.commanded.toDouble())
            put("ai", levels.aiPolicy.name)
        }.toString()
    }

    /** Server thread marshals writes to the same control dispatcher as the watch UI. */
    fun command(body: String): String {
        val json = try { JSONObject(body) } catch (_: Exception) { throw IllegalArgumentException("Invalid JSON") }
        runBlocking(Dispatchers.Main) {
            when (json.optString("action")) {
                "set" -> {
                    val parameter = TuningParameter.entries.firstOrNull { it.name == json.optString("parameter") }
                        ?: throw IllegalArgumentException("Unknown parameter")
                    val value = json.optDouble("value", Double.NaN).toFloat()
                    require(value.isFinite())
                    settings.setTuning(parameter, value)
                }
                "reset" -> settings.resetTuning()
                "retry" -> settings.retrySave()
                "stop" -> stop()
                else -> throw IllegalArgumentException("Unknown action")
            }
        }
        return snapshot()
    }
}
