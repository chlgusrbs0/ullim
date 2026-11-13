package com.ullim.watch.core

import org.junit.Assert.*
import org.junit.Test

class ContractsTest {
    @Test fun pcmOwnershipSurvivesProducerAndConsumerMutation() {
        val source = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE)
        val frame = AudioFrame(1, 100, source)
        source[0] = 0
        frame.copySamples()[2] = 0
        assertEquals(Short.MIN_VALUE, frame.sampleAt(0))
        assertEquals(Short.MAX_VALUE, frame.sampleAt(2))
        assertEquals(-1f, frame.normalizedAt(0), 0f)
        assertTrue(frame.normalizedAt(2) < 1f)
    }

    @Test fun framesRejectInvalidSessionsAndEmptyAudio() {
        assertThrows(IllegalArgumentException::class.java) { AudioFrame(0, 0, shortArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { AudioFrame(1, -1, shortArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { AudioFrame(1, 0, shortArrayOf()) }
    }

    @Test fun normalizedFeaturesAndCommandsRejectNonFiniteOrUnsafeValues() {
        for (bad in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                AudioFeatures(1, 0, rms = bad, peak = 0f, envelope = 0f)
            }
            assertThrows(IllegalArgumentException::class.java) { HapticFrame(1, 0, 20, bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { HapticFrame(1, 0, 0, 0f) }
        assertThrows(IllegalArgumentException::class.java) { HapticFrame(1, 0, 1001, 1f) }
        assertTrue(HapticFrame(1, 0, 20, 0f).isSilent)
    }

    @Test fun classificationExpiresAndCannotCrossSessionsOrComeFromTheFuture() {
        val latest = LatestClassification()
        val scores = mutableListOf(ClassScore("Knock", 0.9f))
        latest.publish(Classification(1, 100, 200, scores))
        scores.clear()
        assertEquals("Knock", latest.current(1, 150)?.scores?.single()?.label)
        assertNull(latest.current(2, 150))
        assertNull(latest.current(1, 99))
        assertNull(latest.current(1, 200))
        latest.clear()
        assertNull(latest.current(1, 150))
    }

    @Test fun olderClassificationCannotReplaceNewerContext() {
        val latest = LatestClassification()
        latest.publish(Classification(1, 200, 400, listOf(ClassScore("Speech", 0.8f))))
        latest.publish(Classification(1, 100, 400, listOf(ClassScore("Noise", 0.9f))))
        assertEquals("Speech", latest.current(1, 250)?.scores?.single()?.label)
    }

    @Test fun listeningAndAiAvailabilityRemainIndependent() {
        val state = RuntimeState(ListeningState.Listening(7), AiStatus.UNAVAILABLE)
        assertEquals(7L, (state.listening as ListeningState.Listening).sessionId)
        assertEquals(AiStatus.UNAVAILABLE, state.ai)
    }
}
