package com.ullim.watch.runtime

import com.ullim.watch.audio.MicrophoneCapture

enum class ForegroundStart { STARTED, ALREADY_RUNNING, DENIED, FAILED, STILL_STOPPING }

class ForegroundSession(
    private val capture: MicrophoneCapture,
    private val promote: () -> Unit,
    private val demote: () -> Unit,
    private val cancelOutput: () -> Unit,
) {
    private var active = false

    @Synchronized fun start(sessionId: Long): ForegroundStart {
        if (active) return ForegroundStart.ALREADY_RUNNING
        return try {
            promote()
            if (capture.start(sessionId)) {
                active = true
                ForegroundStart.STARTED
            } else {
                stop()
                ForegroundStart.STILL_STOPPING
            }
        } catch (_: SecurityException) {
            stop()
            ForegroundStart.DENIED
        } catch (_: RuntimeException) {
            stop()
            ForegroundStart.FAILED
        }
    }

    @Synchronized fun stop() {
        active = false
        runCatching { capture.stop() }
        runCatching { cancelOutput() }
        runCatching { demote() }
    }
}
