package com.ullim.watch.haptics

/** Restricted to the exact model/builds where same-duration 1/255 failed on-device. */
object MotorVibrationPolicy {
    fun requiresPulseFallback(model: String, firmwareKey: String): Boolean = model == "SM-L300" &&
        (firmwareKey.contains("L300XXU2ZZI1") || firmwareKey.contains("L300XXU2ZZK1"))
}
