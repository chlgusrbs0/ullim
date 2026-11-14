package com.ullim.watch.ai

import com.ullim.watch.core.AudioFrame
import org.junit.Assert.*
import org.junit.Test

class YamnetClassifierTest {
    private class Engine : YamnetEngine {
        var calls = 0
        var closes = 0
        var input = floatArrayOf()
        var scores = FloatArray(521).apply { this[4] = .9f; this[2] = .7f }
        override fun infer(samples: FloatArray): FloatArray {
            calls++
            input = samples.copyOf()
            return scores
        }
        override fun close() { closes++ }
    }
    private val labels = List(521) { "class-$it" }
    private fun frame(size: Int = 15600) = AudioFrame(7, 100, ShortArray(size).apply {
        this[0] = Short.MIN_VALUE; this[1] = Short.MAX_VALUE
    })

    @Test fun normalizesPcmAndPreservesScoreIdentityAndWindowTime() {
        val engine = Engine()
        val result = YamnetClassifier(labels, engine).classify(frame())
        assertEquals(-1f, engine.input[0], 0f)
        assertEquals(32767f / 32768f, engine.input[1], 0f)
        assertEquals(15600, engine.input.size)
        assertEquals(7L, result.sessionId)
        assertEquals(975_000_100L, result.timestampNs)
        assertEquals(2_475_000_100L, result.validUntilNs)
        assertEquals("class-4", result.scores[0].label)
        assertEquals(.9f, result.scores[0].confidence, 0f)
        assertEquals("class-2", result.scores[1].label)
        assertEquals(5, result.scores.size)
        engine.scores.fill(0f)
        assertEquals(.9f, result.scores[0].confidence, 0f)
    }

    @Test fun invalidWindowNeverRunsModel() {
        val engine = Engine()
        val classifier = YamnetClassifier(labels, engine)
        assertThrows(IllegalArgumentException::class.java) { classifier.classify(frame(320)) }
        assertEquals(0, engine.calls)
    }

    @Test fun rejectsMalformedScoresBeforePublishingContext() {
        val engine = Engine()
        val classifier = YamnetClassifier(labels, engine)
        for (bad in listOf(FloatArray(520), FloatArray(521) { Float.NaN }, FloatArray(521) { 1.1f })) {
            engine.scores = bad
            assertThrows(IllegalArgumentException::class.java) { classifier.classify(frame()) }
        }
    }

    @Test fun labelsAreOwnedAndInvalidMetadataClosesEngine() {
        val owned = labels.toMutableList()
        val classifier = YamnetClassifier(owned, Engine())
        owned[4] = "wrong"
        assertEquals("class-4", classifier.classify(frame()).scores[0].label)
        for (bad in listOf(labels.dropLast(1), labels.toMutableList().apply { this[0] = "" })) {
            val engine = Engine()
            assertThrows(IllegalArgumentException::class.java) { YamnetClassifier(bad, engine) }
            assertEquals(1, engine.closes)
        }
    }

    @Test fun closeIsIdempotentAndCannotRunAgain() {
        val engine = Engine()
        val classifier = YamnetClassifier(labels, engine)
        classifier.close(); classifier.close()
        assertThrows(IllegalStateException::class.java) { classifier.classify(frame()) }
        assertEquals(0, engine.calls)
        assertEquals(1, engine.closes)
    }

    @Test fun inferenceInputIsErasedAfterSuccessAndEngineFailure() {
        var sharedInput: FloatArray? = null
        var fail = false
        val classifier = YamnetClassifier(labels, object : YamnetEngine {
            override fun infer(samples: FloatArray): FloatArray {
                sharedInput = samples
                assertEquals(-1f, samples[0], 0f)
                if (fail) error("injected model failure")
                return FloatArray(521)
            }
            override fun close() {}
        })
        classifier.classify(frame())
        assertTrue(sharedInput!!.all { it == 0f })
        fail = true
        assertThrows(IllegalStateException::class.java) { classifier.classify(frame()) }
        assertTrue(sharedInput!!.all { it == 0f })
        classifier.close()
    }
}
