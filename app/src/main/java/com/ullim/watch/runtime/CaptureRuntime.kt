package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.core.AiStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveLevels(val sound: Float = 0f, val commanded: Float = 0f)

object CaptureRuntime {
    private val controller = ListeningController()
    private val mutableLevels = MutableStateFlow(LiveLevels())
    private val mutableLimited = MutableStateFlow(false)
    private val mutableAiEnabled = MutableStateFlow(false)
    val state = controller.state
    val levels = mutableLevels.asStateFlow()
    val strengthLimited = mutableLimited.asStateFlow()
    val aiEnabled = mutableAiEnabled.asStateFlow()

    fun setAiEnabled(enabled: Boolean) { mutableAiEnabled.value = enabled }
    @Synchronized fun canStart(): Boolean = controller.canStart()
    @Synchronized fun aiStatus(id: Long, status: AiStatus) { controller.aiStatus(id, status) }

    @Synchronized fun requestStart(): Long? {
        val id = controller.requestStart() ?: return null
        mutableLevels.value = LiveLevels()
        mutableLimited.value = false
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
        if (controller.stopping(id)) mutableLevels.value = LiveLevels()
    }
    @Synchronized fun finished(id: Long) {
        if (controller.finished(id)) { mutableLevels.value = LiveLevels(); mutableLimited.value = false }
    }
    @Synchronized fun fail(id: Long?, message: String) {
        if (controller.fail(id, message)) mutableLevels.value = LiveLevels()
    }
    @Synchronized fun publish(id: Long, sound: Float, commanded: Float) {
        if (controller.isListening(id)) mutableLevels.value = LiveLevels(sound, commanded)
    }
}
