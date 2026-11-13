package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFrame
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class MotorCalibrationTest {
    private fun source(index: Int, amplitude: Int, hz: Int = 200) = AudioFrame(7, index * 20_000_000L,
        ShortArray(320) { (sin(2 * PI * hz * (it + index * 3) / 16000) * amplitude).roundToInt().toShort() })

    @Test fun quietAndTwoMotorWindowsProduceNumericBoundedProfile() {
        val calibration = MotorCalibration(7, 0)
        calibration.recordPhase(0, 500_000_000)
        calibration.recordPhase(1, 1_500_000_000)
        repeat(140) { index ->
            val motor = index in 25..54 || index in 75..104
            calibration.accept(source(index, if (motor) 12000 else 32))
        }
        val profile = calibration.finish()!!
        assertTrue(profile.hasCoupling)
        assertTrue(profile.flatCoupling)
        assertEquals(257, profile.highPowerCopy().size)
        assertTrue(profile.highPowerCopy().all { it.isFinite() && it >= 0 })
    }

    @Test fun missingMotorSamplesOrNoisyBaselineCannotReplaceAProfile() {
        assertNull(MotorCalibration(7, 0).finish())
        val calibration = MotorCalibration(7, 0)
        calibration.recordPhase(0, 500_000_000)
        calibration.recordPhase(1, 1_500_000_000)
        repeat(140) { calibration.accept(source(it, 12000)) }
        assertNull(calibration.finish())
    }

    @Test fun wrongSessionAndUnsettledMotorEdgesDoNotBecomeCalibrationSamples() {
        val calibration = MotorCalibration(8, 0)
        calibration.recordPhase(0, 500_000_000)
        calibration.recordPhase(1, 1_500_000_000)
        repeat(140) { calibration.accept(source(it, 32)) }
        assertNull(calibration.finish())
    }

    @Test fun ignoredMotorOutputCannotBeMistakenForSuccessfulCalibration() {
        val calibration = MotorCalibration(7, 0)
        calibration.recordPhase(0, 500_000_000)
        calibration.recordPhase(1, 1_500_000_000)
        repeat(140) { calibration.accept(source(it, 32)) }
        assertNull(calibration.finish())
    }
}
