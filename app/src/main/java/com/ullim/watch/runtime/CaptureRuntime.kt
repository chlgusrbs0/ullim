package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.core.AiStatus
import com.ullim.watch.settings.UserSettings
import com.ullim.watch.settings.MvpDefaults
import com.ullim.watch.haptics.AiTactileStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveLevels(
    val sound: Float = 0f, val commanded: Float = 0f,
    val rawSound: Float = sound, val baselineCommanded: Float = commanded,
    val motorFiltered: Boolean = false, val aiAdjusted: Boolean = false,
    val aiPolicy: AiTactileStatus = AiTactileStatus.NO_CONTEXT,
    val normalizedLevel: Float = 0f, val detected: Boolean = false,
)

object CaptureRuntime {
    private val controller = ListeningController()
    private val mutableLevels = MutableStateFlow(LiveLevels())
    private val mutableLimited = MutableStateFlow(false)
    private val mutableAiEnabled = MutableStateFlow(false)
    private val mutableSensitivity = MutableStateFlow(MvpDefaults.SENSITIVITY)
    private val mutableTuning = MutableStateFlow(MvpDefaults.tuning)
    private val mutableFailure = MutableStateFlow<CaptureFailure?>(null)
    val state = controller.state
    val startAllowed = controller.startAllowed
    val levels = mutableLevels.asStateFlow()
    val strengthLimited = mutableLimited.asStateFlow()
    val aiEnabled = mutableAiEnabled.asStateFlow()
    val sensitivity = mutableSensitivity.asStateFlow()
    val tuning = mutableTuning.asStateFlow()
    val failure = mutableFailure.asStateFlow()
    val visualization = VisualizationHistory()

    fun setAiEnabled(enabled: Boolean) { mutableAiEnabled.value = enabled }
    fun applySettings(values: UserSettings) {
        mutableSensitivity.value = values.sensitivity
        mutableAiEnabled.value = values.aiEnabled
        mutableTuning.value = values.tuning
    }
    @Synchronized fun canStart(): Boolean = controller.canStart()
    @Synchronized fun stopToken(): Long? = controller.stopToken()
    @Synchronized fun aiStatus(id: Long, status: AiStatus) { controller.aiStatus(id, status) }

    @Synchronized fun requestStart(): Long? {
        val id = controller.requestStart() ?: return null
        mutableFailure.value = null
        mutableLevels.value = LiveLevels()
        mutableLimited.value = false
        visualization.begin(id)
        return id
    }

    @Synchronized fun isStarting(id: Long): Boolean = controller.isStarting(id)
    @Synchronized fun attach(id: Long, capture: MicrophoneCapture): Boolean = controller.attach(id, capture)
    @Synchronized fun detach(capture: MicrophoneCapture) { controller.detach(capture) }
    @Synchronized fun listening(id: Long, limited: Boolean) {
        if (!controller.listening(id)) return
        mutableLimited.value = limited
    }
    @Synchronized fun stopping(id: Long) {
        if (controller.stopping(id)) { mutableLevels.value = LiveLevels(); visualization.end(id) }
    }
    @Synchronized fun finished(id: Long) {
        if (controller.finished(id)) { mutableLevels.value = LiveLevels(); mutableLimited.value = false; visualization.end(id) }
    }
    @Synchronized fun fail(id: Long?, message: String, cause: CaptureFailure = CaptureFailure.UNKNOWN): Boolean {
        if (!controller.fail(id, message, replaceActiveError = cause == CaptureFailure.CLEANUP || cause == CaptureFailure.SERVICE_COMMAND)) return false
        mutableFailure.value = cause
        mutableLevels.value = LiveLevels()
        id?.let(visualization::end)
        return true
    }
    @Synchronized fun publish(id: Long, sound: Float, commanded: Float, rawSound: Float = sound,
        baselineCommanded: Float = commanded, motorFiltered: Boolean = false, aiAdjusted: Boolean = false,
        aiPolicy: AiTactileStatus = AiTactileStatus.NO_CONTEXT,
        normalizedLevel: Float = 0f, detected: Boolean = false) {
        if (controller.isListening(id)) mutableLevels.value = LiveLevels(sound, commanded, rawSound,
            baselineCommanded, motorFiltered, aiAdjusted, aiPolicy, normalizedLevel, detected)
    }
}
