package com.ullim.watch

import android.content.Context
import android.util.Log
import com.ullim.watch.runtime.CaptureFailure
import com.ullim.watch.runtime.CaptureRuntime

internal fun reportCaptureFailure(context: Context, session: Long?, failure: CaptureFailure) {
    val message = when (failure) {
        CaptureFailure.MIC_PERMISSION -> R.string.microphone_permission_needed
        CaptureFailure.MIC_SILENCED -> R.string.microphone_silenced
        CaptureFailure.MIC_INITIALIZATION -> R.string.microphone_initialization_failed
        CaptureFailure.MIC_READ -> R.string.microphone_read_failed
        CaptureFailure.PROCESSING -> R.string.processing_failed
        CaptureFailure.HAPTIC_UNSUPPORTED -> R.string.motor_unavailable
        CaptureFailure.HAPTIC_OUTPUT -> R.string.haptic_output_failed
        CaptureFailure.FOREGROUND_START -> R.string.service_start_failed
        CaptureFailure.SERVICE_COMMAND -> R.string.service_stop_failed
        CaptureFailure.CLEANUP -> R.string.capture_cleanup_failed
        CaptureFailure.UNKNOWN -> R.string.capture_failed
    }
    if (CaptureRuntime.fail(session, context.getString(message), failure)) {
        Log.w("UllimError", "session=${session ?: 0} code=${failure.code}")
    }
}
