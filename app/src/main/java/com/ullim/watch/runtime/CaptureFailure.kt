package com.ullim.watch.runtime

import com.ullim.watch.audio.CaptureError

enum class CaptureFailure(
    val code: String,
    val openSettings: Boolean = false,
    val openDiagnostics: Boolean = false,
    val restartRequired: Boolean = false,
) {
    MIC_PERMISSION("MIC_PERMISSION", openSettings = true),
    MIC_SILENCED("MIC_SILENCED", openSettings = true),
    MIC_INITIALIZATION("MIC_INITIALIZATION"),
    MIC_READ("MIC_READ"),
    PROCESSING("PROCESSING"),
    HAPTIC_UNSUPPORTED("HAPTIC_UNSUPPORTED", openDiagnostics = true),
    HAPTIC_OUTPUT("HAPTIC_OUTPUT", openDiagnostics = true),
    FOREGROUND_START("FOREGROUND_START"),
    SERVICE_COMMAND("SERVICE_COMMAND", openSettings = true, restartRequired = true),
    CLEANUP("CLEANUP", openSettings = true, restartRequired = true),
    UNKNOWN("UNKNOWN");

    companion object {
        fun from(cause: CaptureError): CaptureFailure = when (cause) {
            CaptureError.PERMISSION -> MIC_PERMISSION
            CaptureError.SILENCED -> MIC_SILENCED
            CaptureError.INITIALIZATION -> MIC_INITIALIZATION
            CaptureError.READ -> MIC_READ
            CaptureError.CONSUMER -> PROCESSING
            CaptureError.OUTPUT -> HAPTIC_OUTPUT
            CaptureError.OUTPUT_UNSUPPORTED -> HAPTIC_UNSUPPORTED
            CaptureError.CLEANUP -> CLEANUP
        }
    }
}
