package com.ullim.watch.probe

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.Classification
import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.HapticMapper
import com.ullim.watch.haptics.EnvelopeHapticMapper
import com.ullim.watch.haptics.HybridHapticMapper
import org.junit.Assert.*
import org.junit.Test

class HybridPolicyVerificationTest {
    private fun context() = Classification(1, 1_500_000_000, 3_000_000_000,
        listOf(ClassScore("Static", .66796875f), ClassScore("Noise", .5859375f), ClassScore("White noise", .5f)))

    @Test fun expiryProbeRejectsAMapperThatKeepsAdjustingNonzeroExpiredInput() {
        val broken = object : HapticMapper {
            private val baseline = EnvelopeHapticMapper()
            override fun map(features: AudioFeatures, context: Classification?) =
                baseline.map(features).let { if (context != null) it.copy(commandedIntensity = it.commandedIntensity * .94f) else it }
            override fun reset() = baseline.reset()
        }
        val failure = assertThrows(IllegalStateException::class.java) {
            HybridPolicyVerification.verifyNonzeroExpiry(context(), broken, {}, {})
        }
        assertEquals("EXPIRED_NOT_BASELINE", failure.message)
    }

    @Test fun expiryProbeAcceptsRealPolicyAndObservesBothNonzeroPhases() {
        val reports = mutableListOf<String>()
        HybridPolicyVerification.verifyNonzeroExpiry(context(), HybridHapticMapper(), {}, { reports.add(it) })
        assertTrue(reports.single().contains("adjustedBeforeExpiry=75 expiredNonzeroExact=25"))
    }
}
