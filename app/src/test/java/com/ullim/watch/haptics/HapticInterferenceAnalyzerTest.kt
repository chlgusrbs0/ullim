package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.HapticFrame
import org.junit.Assert.*
import org.junit.Test

class HapticInterferenceAnalyzerTest {
    private fun features(
        sessionId: Long = 1,
        timestampNs: Long,
        rms: Float = 0.1f,
        peak: Float = 0.2f,
        onset: Float = 0.3f,
    ) = AudioFeatures(
        sessionId = sessionId,
        timestampNs = timestampNs,
        rms = rms,
        peak = peak,
        envelope = rms,
        onset = onset,
    )

    @Test
    fun commandWindowCollectsBaselineAndCorrelatedAudioMetrics() {
        val analyzer = HapticInterferenceAnalyzer(
            baselineWindowNs = 200,
            postCommandTailNs = 120,
        )
        analyzer.observe(features(timestampNs = 999_999_850, rms = 0.1f))
        analyzer.observe(features(timestampNs = 999_999_950, rms = 0.2f))
        analyzer.recordCommand(HapticFrame(1, 1_000_000_000, 100, 0.7f))

        assertEquals(1, analyzer.observe(features(timestampNs = 1_000_000_000, rms = 0.4f)).size)
        assertEquals(1, analyzer.observe(features(timestampNs = 1_100_000_120, rms = 0.6f, peak = 0.9f, onset = 0.8f)).size)
        assertTrue(analyzer.observe(features(timestampNs = 1_100_000_121, rms = 0.8f)).isEmpty())

        val result = analyzer.snapshots().single()
        assertEquals(2, result.baselineFrameCount)
        assertEquals(0.15f, result.baselineMeanRms!!, 0.0001f)
        assertEquals(2, result.correlatedFrameCount)
        assertEquals(0.5f, result.correlatedMeanRms!!, 0.0001f)
        assertEquals(0.9f, result.correlatedPeak!!, 0f)
        assertEquals(0.8f, result.correlatedOnset!!, 0f)
    }

    @Test
    fun correlationNeverCrossesAudioSessions() {
        val analyzer = HapticInterferenceAnalyzer(postCommandTailNs = 100)
        analyzer.recordCommand(HapticFrame(7, 1_000, 100, 1f))

        assertTrue(analyzer.observe(features(sessionId = 8, timestampNs = 1_050)).isEmpty())
        assertEquals(1, analyzer.observe(features(sessionId = 7, timestampNs = 1_050)).size)
    }

    @Test
    fun silentCommandCanBeRecordedAsAnOffControlWindow() {
        val analyzer = HapticInterferenceAnalyzer()
        analyzer.recordCommand(HapticFrame(1, 10, 50, 0f))

        analyzer.observe(features(timestampNs = 20))
        val result = analyzer.snapshots().single()
        assertEquals(0f, result.commandedIntensity, 0f)
        assertEquals(1, result.correlatedFrameCount)
    }

    @Test
    fun commandAndAudioHistoryAreBoundedAndCanBeCleared() {
        val analyzer = HapticInterferenceAnalyzer(
            maxRecentAudioFrames = 2,
            maxCommandHistory = 2,
        )
        repeat(3) { index ->
            analyzer.observe(features(timestampNs = index.toLong()))
            analyzer.recordCommand(HapticFrame(1, 10L + index, 1, 0.5f))
        }

        assertEquals(2, analyzer.snapshots().size)
        assertEquals(2, analyzer.snapshots().first().baselineFrameCount)

        analyzer.clear()
        assertTrue(analyzer.snapshots().isEmpty())
        assertTrue(analyzer.observe(features(timestampNs = 100)).isEmpty())
    }

    @Test
    fun invalidWindowConfigurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            HapticInterferenceAnalyzer(baselineWindowNs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HapticInterferenceAnalyzer(postCommandTailNs = -1)
        }
    }
}
