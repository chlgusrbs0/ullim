package com.ullim.watch.runtime

import com.ullim.watch.core.AiStatus
import com.ullim.watch.core.ListeningState
import com.ullim.watch.core.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Logical session authority. Native resource operations never run under this monitor. */
class ListeningController {
    private val mutableState = MutableStateFlow(RuntimeState())
    val state = mutableState.asStateFlow()
    private val mutableStartAllowed = MutableStateFlow(true)
    val startAllowed = mutableStartAllowed.asStateFlow()
    private var sequence = 0L
    private var currentId = 0L
    private var owner: Any? = null
    private var acquired = false
    private var settled = true
    private var completionRequested = false

    @Synchronized fun requestStart(): Long? {
        if (!canStart()) return null
        currentId = ++sequence
        acquired = false
        settled = false
        completionRequested = false
        emit(RuntimeState(ListeningState.Starting(currentId)))
        return currentId
    }

    @Synchronized fun canStart(): Boolean {
        val phase = state.value.listening
        return owner == null && settled && (phase == ListeningState.Idle || phase is ListeningState.Error)
    }

    @Synchronized fun stopToken(): Long? = currentId.takeIf { it > 0 && (!settled || owner != null) }

    @Synchronized fun isStarting(id: Long): Boolean = id > 0 && currentId == id && state.value.listening == ListeningState.Starting(id)
    @Synchronized fun isListening(id: Long): Boolean = id > 0 && currentId == id && state.value.listening == ListeningState.Listening(id)
    @Synchronized fun attach(id: Long, resource: Any): Boolean {
        if (!isStarting(id) || owner != null || completionRequested) return false
        owner = resource
        acquired = true
        return true
    }

    @Synchronized fun detach(resource: Any): Boolean {
        if (owner !== resource) return false
        owner = null
        if (completionRequested) complete()
        else if (state.value.listening !is ListeningState.Error) emit(RuntimeState(ListeningState.Stopping(currentId)))
        return true
    }

    @Synchronized fun listening(id: Long): Boolean {
        if (!isStarting(id) || owner == null) return false
        emit(mutableState.value.copy(listening = ListeningState.Listening(id)))
        return true
    }

    @Synchronized fun stopping(id: Long): Boolean {
        val phase = state.value.listening
        if (id <= 0 || currentId != id || (phase !is ListeningState.Starting && phase !is ListeningState.Listening)) return false
        emit(RuntimeState(ListeningState.Stopping(id)))
        return true
    }

    @Synchronized fun finished(id: Long): Boolean {
        if (id <= 0 || currentId != id) return false
        completionRequested = true
        if (owner == null) complete()
        else if (state.value.listening !is ListeningState.Error) emit(RuntimeState(ListeningState.Stopping(id)))
        return true
    }

    @Synchronized fun fail(id: Long?, message: String, replaceActiveError: Boolean = false): Boolean {
        val phase = state.value.listening
        if (id == null) {
            if (!settled || owner != null || (phase != ListeningState.Idle && phase !is ListeningState.Error)) return false
        } else if (id <= 0 || currentId != id || phase == ListeningState.Idle ||
            (phase is ListeningState.Error && (!replaceActiveError || (settled && owner == null)))) return false
        // Failure before any resource acquisition needs no asynchronous release barrier.
        if (!acquired && owner == null) settled = true
        emit(RuntimeState(ListeningState.Error(message)))
        return true
    }

    @Synchronized fun aiStatus(id: Long, status: AiStatus): Boolean {
        if (!isStarting(id) && !isListening(id)) return false
        emit(mutableState.value.copy(ai = status))
        return true
    }

    private fun complete() {
        settled = true
        completionRequested = false
        emit(if (state.value.listening !is ListeningState.Error) RuntimeState() else state.value)
    }

    private fun emit(value: RuntimeState) {
        mutableState.value = value
        mutableStartAllowed.value = canStart()
    }
}
