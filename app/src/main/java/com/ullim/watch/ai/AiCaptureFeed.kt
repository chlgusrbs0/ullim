package com.ullim.watch.ai

import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.core.AudioFrame

/** Main/control-thread mode changes are separate from audio-thread frame delivery. */
class AiCaptureFeed(
    private val sessionId: Long,
    private val worker: AiWorker,
    private val fanout: AudioFanOut,
    private val readEnabled: () -> Boolean,
) {
    /** Service/UI control thread only; audio delivery never observes or changes the mode. */
    fun start(): Boolean = worker.start(sessionId, readEnabled())
    fun refreshMode() { worker.setEnabled(sessionId, readEnabled()) }
    fun accept(frame: AudioFrame): Boolean {
        val accepted = fanout.accept(frame)
        fanout.takeLatestAi()?.let { worker.offer(it) }
        return accepted
    }
}
