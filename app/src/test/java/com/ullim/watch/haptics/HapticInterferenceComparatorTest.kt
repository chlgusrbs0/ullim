package com.ullim.watch.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HapticInterferenceComparatorTest {
    private fun snapshot(
        intensity: Float,
        baseline: Float?,
        correlated: Float?,
        baselineCount: Int = if (baseline == null) 0 else 2,
        correlatedCount: Int = if (correlated == null) 0 else 2,
    ) = HapticInterferenceSnapshot(
        sessionId = 1,
        commandTimestampNs = 1_000,
        commandDurationMs = 100,
        commandedIntensity = intensity,
        baselineFrameCount = baselineCount,
        baselineMeanRms = baseline,
        correlatedFrameCount = correlatedCount,
        correlatedMeanRms = correlated,
        correlatedPeak = correlated,
        correlatedOnset = correlated,
    )

    @Test
    fun comparesBaselineAdjustedRmsIncreaseBetweenHapticAndControlWindows() {
        val result = HapticInterferenceComparator.compare(
            listOf(
                snapshot(intensity = 0.5f, baseline = 0.1f, correlated = 0.3f),
                snapshot(intensity = 1f, baseline = 0.2f, correlated = 0.5f),
                snapshot(intensity = 0f, baseline = 0.1f, correlated = 0.15f),
                snapshot(intensity = 0f, baseline = 0.2f, correlated = 0.3f),
            ),
        )

        assertEquals(2, result.hapticSampleCount)
        assertEquals(2, result.controlSampleCount)
        assertEquals(0.25f, result.meanRmsIncreaseWithHaptics!!, 0.0001f)
        assertEquals(0.075f, result.meanRmsIncreaseWithoutHaptics!!, 0.0001f)
        assertEquals(0.175f, result.additionalRmsDuringHaptics!!, 0.0001f)
    }

    @Test
    fun zeroIntensityIsIncludedInControlGroup() {
        val result = HapticInterferenceComparator.compare(
            listOf(snapshot(intensity = 0f, baseline = 0.1f, correlated = 0.2f)),
        )

        assertEquals(0, result.hapticSampleCount)
        assertEquals(1, result.controlSampleCount)
        assertNull(result.meanRmsIncreaseWithHaptics)
        assertEquals(0.1f, result.meanRmsIncreaseWithoutHaptics!!, 0.0001f)
        assertNull(result.additionalRmsDuringHaptics)
    }

    @Test
    fun incompleteWindowsAreExcludedFromComparison() {
        val result = HapticInterferenceComparator.compare(
            listOf(
                snapshot(intensity = 1f, baseline = 0.1f, correlated = null),
                snapshot(intensity = 0f, baseline = null, correlated = 0.2f),
                snapshot(
                    intensity = 1f,
                    baseline = 0.1f,
                    correlated = 0.2f,
                    baselineCount = 0,
                ),
                snapshot(
                    intensity = 0f,
                    baseline = 0.1f,
                    correlated = 0.2f,
                    correlatedCount = 0,
                ),
            ),
        )

        assertEquals(0, result.hapticSampleCount)
        assertEquals(0, result.controlSampleCount)
        assertNull(result.meanRmsIncreaseWithHaptics)
        assertNull(result.meanRmsIncreaseWithoutHaptics)
        assertNull(result.additionalRmsDuringHaptics)
    }

    @Test
    fun missingEitherGroupProducesNoAdditionalRmsEstimate() {
        val result = HapticInterferenceComparator.compare(
            listOf(snapshot(intensity = 0.7f, baseline = 0.1f, correlated = 0.4f)),
        )

        assertEquals(1, result.hapticSampleCount)
        assertEquals(0, result.controlSampleCount)
        assertEquals(0.3f, result.meanRmsIncreaseWithHaptics!!, 0.0001f)
        assertNull(result.meanRmsIncreaseWithoutHaptics)
        assertNull(result.additionalRmsDuringHaptics)
    }
}
