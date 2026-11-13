package com.ullim.watch.dsp

import com.ullim.watch.core.AudioFrame
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class EnvelopeExtractorTest {
    private fun frame(level: Int, index: Int = 0, session: Long = 1) = AudioFrame(session, index * 20_000_000L, ShortArray(320) { level.toShort() })

    @Test fun silenceIsExactlyZero() {
        val features = EnvelopeExtractor().extract(frame(0))
        assertEquals(0f, features.rms, 0f)
        assertEquals(0f, features.peak, 0f)
        assertTrue(features.isSilent)
    }

    @Test fun constantInputIsOrderedAndClippingRemainsFinite() {
        val levels = listOf(100, 1000, 10000, 32767).map { EnvelopeExtractor().extract(frame(it)) }
        assertTrue(levels.zipWithNext().all { (a, b) -> a.rms < b.rms && a.envelope < b.envelope })
        val clipped = EnvelopeExtractor().extract(frame(-32768))
        assertEquals(1f, clipped.rms, 0f)
        assertEquals(1f, clipped.peak, 0f)
        assertTrue(clipped.envelope.isFinite())
    }

    @Test fun sineRmsMatchesAnalyticalReference() {
        val samples = ShortArray(320) { (16384 * sin(2 * PI * 1000 * it / 16000)).toInt().toShort() }
        val features = EnvelopeExtractor().extract(AudioFrame(1, 0, samples))
        assertEquals((0.5 / kotlin.math.sqrt(2.0)).toFloat(), features.rms, 0.0001f)
        assertEquals(0.5f, features.peak, 0.0001f)
    }

    @Test fun attackAndReleaseFollowTheirTimeConstants() {
        val extractor = EnvelopeExtractor(attackMs = 20f, releaseMs = 120f)
        val first = extractor.extract(frame(16384)).envelope
        assertEquals(0.5f * (1f - kotlin.math.exp(-1f)), first, 0.00001f)
        var released = first
        for (index in 1..6) released = extractor.extract(frame(0, index)).envelope
        assertEquals(first * kotlin.math.exp(-1f), released, 0.00001f)
        assertTrue(released > 0f)
    }

    @Test fun resetAndNewSessionRemoveResidualEnvelope() {
        val extractor = EnvelopeExtractor()
        extractor.extract(frame(32767))
        extractor.reset()
        assertEquals(0f, extractor.extract(frame(0)).envelope, 0f)
        extractor.extract(frame(32767, 1))
        assertEquals(0f, extractor.extract(frame(0, session = 2)).envelope, 0f)
    }
}
