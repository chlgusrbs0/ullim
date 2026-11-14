package com.ullim.watch.ui

import com.ullim.watch.ai.*
import com.ullim.watch.core.*
import org.junit.Assert.*
import org.junit.Test

class AiDisplayTest {
    private val runtime = RuntimeState(ListeningState.Listening(7))
    private fun result(vararg scores: ClassScore, session: Long = 7, time: Long = 1_000_000_000L) =
        Classification(session, time, time + 1_500_000_000, scores.toList())
    private fun snapshot(result: Classification? = result(ClassScore("Speech", .82f)), phase: AiPhase = AiPhase.LISTENING) =
        AiSnapshot(sessionId = 7, phase = phase, classification = result)
    private fun display(snapshot: AiSnapshot = snapshot(), now: Long = 1_100_000_000L) = AiDisplay.project(true, runtime, snapshot, now)

    @Test fun offInactiveAndNonRecognizingExecutionPhasesHideAnyStoredClassification() {
        assertEquals(AiDisplayStatus.OFF, AiDisplay.project(false, runtime, snapshot(), 1_100_000_000).status)
        assertNull(AiDisplay.project(false, runtime, snapshot(), 1_100_000_000).label)
        for (phase in listOf(ListeningState.Idle, ListeningState.Stopping(7), ListeningState.Error("mic"))) {
            assertNull(AiDisplay.project(true, RuntimeState(phase), snapshot(), 1_100_000_000).label)
        }
        for ((phase, expected) in listOf(AiPhase.DISABLED to AiDisplayStatus.LOADING,
            AiPhase.LOADING to AiDisplayStatus.LOADING, AiPhase.DELAYED to AiDisplayStatus.DELAYED, AiPhase.FAILED to AiDisplayStatus.FAILED)) {
            val state = display(snapshot(phase = phase))
            assertEquals(expected, state.status)
            assertNull(state.label); assertNull(state.modelScorePercent)
        }
    }

    @Test fun freshnessIsCheckedAtDisplayTimeAndWrongSessionOrFutureCannotLookRecognized() {
        assertEquals(AiResultKind.RECOGNIZED, display(now = 2_499_999_999).result)
        assertEquals(AiResultKind.EXPIRED, display(now = 2_500_000_000).result)
        assertNull(display(now = 2_500_000_000).label)
        assertNull(display(now = 999_999_999).label)
        assertNull(display(snapshot(result(ClassScore("Speech", .9f), session = 6))).label)
        val staleWorker = snapshot().copy(sessionId = 6)
        assertEquals(AiDisplayStatus.LOADING, display(staleWorker).status)
        assertNull(display(staleWorker).label)
    }

    @Test fun emptyLowTiedAndDuplicateScoresCannotManufactureConfidentRecognition() {
        for (scores in listOf(emptyList(), listOf(ClassScore("Speech", .2f)),
            listOf(ClassScore("Speech", .8f), ClassScore("Noise", .78f)))) {
            val state = display(snapshot(result(*scores.toTypedArray())))
            assertEquals(AiResultKind.UNCERTAIN, state.result)
            assertNull(state.label); assertNull(state.modelScorePercent)
        }
        val duplicate = display(snapshot(result(ClassScore("Speech", .4f), ClassScore("Speech", .8f), ClassScore("Noise", .1f))))
        assertEquals("말소리", duplicate.label)
        assertEquals(80, duplicate.modelScorePercent)
    }

    @Test fun koreanExactMappingPreservesOriginalAndUntranslatedIsNotInventedWarning() {
        assertEquals("말소리", display().label)
        assertEquals("Speech", display().originalLabel)
        assertEquals(82, display().modelScorePercent)
        val untranslated = display(snapshot(result(ClassScore("French horn", .9f))))
        assertEquals("French horn", untranslated.label)
        assertNull(untranslated.originalLabel)
        assertEquals(AiResultKind.UNSUPPORTED, display(snapshot(result(ClassScore("Unknown", .9f)))).result)
    }

    @Test fun expiryMetadataDistinguishesClearedOldResultFromFirstResultAndFlagsStayHonest() {
        assertEquals(AiResultKind.WAITING, display(snapshot(null)).result)
        assertEquals(AiResultKind.EXPIRED, display(snapshot(null).copy(resultExpired = true)).result)
        val failed = snapshot().copy(phase = AiPhase.FAILED, fault = AiFault.CLEANUP, cleanupPending = true)
        assertTrue(display(failed).restartRequired)
        assertTrue(display(failed).cleanupPending)
        val off = AiDisplay.project(false, runtime, failed, 1_100_000_000)
        assertEquals(AiDisplayStatus.OFF, off.status)
        assertNull(off.label)
        assertTrue(off.cleanupPending)
    }
}
