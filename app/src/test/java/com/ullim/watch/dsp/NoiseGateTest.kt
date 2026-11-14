package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFeatures
import org.junit.Assert.*
import org.junit.Test

class NoiseGateTest {
    private fun feature(level: Float, index: Int = 0, session: Long = 1) = AudioFeatures(session, index * 20_000_000L, level, level, level)

    @Test fun sustainedSoundKeepsItsLevelForTwoMinutesThenSilenceCloses() {
        val gate = NoiseGate()
        assertEquals(0f, gate.process(feature(0f)).level, 0f)
        val first = gate.process(feature(.03f, 1))
        repeat(6000) { assertEquals(first.level, gate.process(feature(.03f, it + 2)).level, .000001f) }
        assertEquals(0f, gate.process(feature(0f, 6002)).level, 0f)
    }

    @Test fun motorContaminationCannotTeachTheNoiseFloorEvenBelowOpeningThreshold() {
        val gate = NoiseGate()
        repeat(1000) { gate.process(feature(.006f, it), learnNoise = false) }
        assertEquals(.003f, gate.process(feature(0f, 1000), learnNoise = false).noise, 0f)
        assertTrue(gate.process(feature(.02f, 1001)).level > 0f)
    }

    @Test fun quietSoundAfterLoudSoundRetainsTheSameStrength() {
        val gate = NoiseGate()
        val quiet = gate.process(feature(.03f))
        repeat(1000) { gate.process(feature(.7f, it + 1)) }
        assertEquals(quiet.level, gate.process(feature(.03f, 1001)).level, .000001f)
    }

    @Test fun transientAboveLearnedBackgroundRemainsVisible() {
        val gate = NoiseGate()
        repeat(1000) { gate.process(feature(0.03f, it)) }
        val transient = gate.process(feature(0.3f, 1000))
        assertTrue(transient.level > 0.5f)
        assertTrue(transient.noise < 0.04f)
    }

    @Test fun sensitivityIsBoundedAndOrderedAtSameInitialCondition() {
        val low = NoiseGate().process(feature(0.1f), 0.5f).level
        val high = NoiseGate().process(feature(0.1f), 2f).level
        assertTrue(high >= low)
        assertEquals(1f, NoiseGate().process(feature(1f), 2f).level, 0f)
        assertThrows(IllegalArgumentException::class.java) { NoiseGate().process(feature(0f), Float.NaN) }
    }

    @Test fun hysteresisAvoidsChatterAndSilenceClosesImmediately() {
        val gate = NoiseGate()
        assertTrue(gate.process(feature(0.02f)).level > 0f)
        assertTrue(gate.process(feature(0.009f, 1)).level > 0f)
        assertEquals(0f, gate.process(feature(0f, 2)).level, 0f)
        assertEquals(0f, gate.process(feature(0.009f, 3)).level, 0f)
    }

    @Test fun newSessionDoesNotInheritNoiseFloor() {
        val gate = NoiseGate()
        repeat(3000) { gate.process(feature(0.2f, it)) }
        assertTrue(gate.process(feature(0.05f, session = 2)).level > 0f)
    }
}
