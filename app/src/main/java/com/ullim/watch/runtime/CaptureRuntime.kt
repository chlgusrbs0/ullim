package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture
import com.ullim.watch.core.ListeningState
import com.ullim.watch.core.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveLevels(val sound: Float = 0f, val commanded: Float = 0f)

object CaptureRuntime {
    private val mutableState = MutableStateFlow(RuntimeState())
    private val mutableLevels = MutableStateFlow(LiveLevels())
    private val mutableLimited = MutableStateFlow(false)
    val state = mutableState.asStateFlow()
    val levels = mutableLevels.asStateFlow()
    val strengthLimited = mutableLimited.asStateFlow()
    private var counter = 0L
    private var currentId = 0L
    private var owner: MicrophoneCapture? = null

    @Synchronized fun requestStart(): Long? {
        if (owner != null || (state.value.listening != ListeningState.Idle && state.value.listening !is ListeningState.Error)) return null
        currentId = ++counter
        mutableState.value = RuntimeState(ListeningState.Starting(currentId))
        mutableLevels.value = LiveLevels()
        return currentId
    }

    @Synchronized fun isStarting(id: Long): Boolean = currentId == id && state.value.listening == ListeningState.Starting(id)
    @Synchronized fun attach(capture: MicrophoneCapture): Boolean {
        if (owner != null) return false
        owner = capture
        return true
    }
    @Synchronized fun detach(capture: MicrophoneCapture) { if (owner === capture) owner = null }
    @Synchronized fun listening(id: Long, limited: Boolean) {
        if (!isStarting(id)) return
        mutableLimited.value = limited
        mutableState.value = RuntimeState(ListeningState.Listening(id))
    }
    @Synchronized fun stopping(id: Long) {
        if (currentId == id && state.value.listening !is ListeningState.Error) mutableState.value = RuntimeState(ListeningState.Stopping(id))
    }
    @Synchronized fun finished(id: Long) {
        if (currentId != id) return
        mutableLevels.value = LiveLevels()
        if (state.value.listening !is ListeningState.Error) mutableState.value = RuntimeState()
    }
    @Synchronized fun fail(id: Long?, message: String) {
        if (id != null && currentId != id) return
        if (id == null && owner != null) return
        mutableState.value = RuntimeState(ListeningState.Error(message))
        mutableLevels.value = LiveLevels()
    }
    @Synchronized fun publish(id: Long, sound: Float, commanded: Float) {
        if (currentId == id && state.value.listening is ListeningState.Listening) mutableLevels.value = LiveLevels(sound, commanded)
    }
}
