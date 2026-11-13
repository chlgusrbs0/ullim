package com.ullim.watch.haptics

/**
 * Compares baseline-adjusted RMS changes during haptic and control windows.
 *
 * This is an observational comparison, not proof that haptics caused the
 * measured change. Environmental sounds and capture timing can also affect it.
 */
object HapticInterferenceComparator {
    fun compare(
        snapshots: List<HapticInterferenceSnapshot>,
    ): HapticInterferenceComparisonResult {
        val complete = snapshots.mapNotNull { snapshot ->
            val baseline = snapshot.baselineMeanRms ?: return@mapNotNull null
            val correlated = snapshot.correlatedMeanRms ?: return@mapNotNull null
            if (snapshot.baselineFrameCount == 0 || snapshot.correlatedFrameCount == 0) {
                return@mapNotNull null
            }
            (correlated - baseline).toDouble() to (snapshot.commandedIntensity > 0f)
        }

        val hapticChanges = complete.filter { it.second }.map { it.first }
        val controlChanges = complete.filterNot { it.second }.map { it.first }
        val hapticMean = hapticChanges.meanOrNull()
        val controlMean = controlChanges.meanOrNull()

        return HapticInterferenceComparisonResult(
            hapticSampleCount = hapticChanges.size,
            controlSampleCount = controlChanges.size,
            meanRmsIncreaseWithHaptics = hapticMean,
            meanRmsIncreaseWithoutHaptics = controlMean,
            additionalRmsDuringHaptics = if (hapticMean != null && controlMean != null) {
                hapticMean - controlMean
            } else {
                null
            },
        )
    }

    private fun List<Double>.meanOrNull(): Float? =
        takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
}

data class HapticInterferenceComparisonResult(
    val hapticSampleCount: Int,
    val controlSampleCount: Int,
    val meanRmsIncreaseWithHaptics: Float?,
    val meanRmsIncreaseWithoutHaptics: Float?,
    val additionalRmsDuringHaptics: Float?,
)
