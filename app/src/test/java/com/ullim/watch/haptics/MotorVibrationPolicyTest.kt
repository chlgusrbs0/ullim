package com.ullim.watch.haptics

import org.junit.Assert.*
import org.junit.Test

class MotorVibrationPolicyTest {
    @Test fun fallbackIsRestrictedToTheActuallyTestedSamsungBetaBuilds() {
        assertTrue(MotorVibrationPolicy.requiresPulseFallback("SM-L300", "samsung/L300XXU2ZZK1:user"))
        assertTrue(MotorVibrationPolicy.requiresPulseFallback("SM-L300", "samsung/L300XXU2ZZI1:user"))
        assertFalse(MotorVibrationPolicy.requiresPulseFallback("SM-L300", "new-untested-firmware"))
        assertFalse(MotorVibrationPolicy.requiresPulseFallback("other-model", "L300XXU2ZZK1"))
    }
}
