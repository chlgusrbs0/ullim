package com.ullim.watch.ai

import com.ullim.watch.core.ClassScore
import com.ullim.watch.core.Classification
import org.junit.Assert.*
import org.junit.Test

class ClassificationSmootherTest {
    private fun result(t: Long, label: String = "Speech", score: Float = .9f, session: Long = 1) =
        Classification(session, t, t + 1_500_000_000L, listOf(ClassScore(label, score)))

    @Test fun confirmsOnlyDistinctWindowsAndNeverExtendsTheirExpiry() {
        val smoother = ClassificationSmoother()
        val first = result(1_000_000_000)
        assertTrue(smoother.accept(first, 1, first.timestampNs)!!.scores.isEmpty())
        val early = result(1_100_000_000)
        assertTrue(smoother.accept(early, 1, early.timestampNs)!!.scores.isEmpty())
        repeat(20) { smoother.accept(first, 1, 1_499_000_000) }
        assertTrue(smoother.current(1, 1_499_000_000)!!.scores.isEmpty())
        val second = result(1_500_000_000)
        val stable = smoother.accept(second, 1, second.timestampNs)!!
        assertEquals("Speech", stable.scores.first().label)
        assertEquals(1_500_000_000L, stable.timestampNs)
        assertEquals(3_000_000_000L, stable.validUntilNs)
        assertNull(smoother.current(1, 3_000_000_000))
    }

    @Test fun alternatingClassesStayUncertainUntilAConsistentCandidateArrives() {
        val smoother = ClassificationSmoother()
        for ((index, label) in listOf("Speech", "Knock", "Speech", "Knock").withIndex()) {
            val input = result(1_000_000_000L + index * 500_000_000L, label)
            assertTrue(smoother.accept(input, 1, input.timestampNs)!!.scores.isEmpty())
        }
        val stable = smoother.accept(result(3_000_000_000L, "Knock"), 1, 3_000_000_000L)!!
        assertEquals("Knock", stable.scores.first().label)
    }

    @Test fun lowAmbiguousAndNoSignalInputsDoNotInheritAConfidentLabel() {
        val smoother = ClassificationSmoother()
        smoother.accept(result(1_000_000_000), 1, 1_000_000_000)
        smoother.accept(result(1_500_000_000), 1, 1_500_000_000)
        val ambiguous = Classification(1, 2_000_000_000L, 3_500_000_000L,
            listOf(ClassScore("Speech", .4f), ClassScore("Knock", .39f)))
        assertTrue(smoother.accept(ambiguous, 1, 2_000_000_000L)!!.scores.isEmpty())
        val empty = Classification(1, 2_500_000_000L, 4_000_000_000L, emptyList())
        assertTrue(smoother.accept(empty, 1, empty.timestampNs)!!.scores.isEmpty())
        val low = result(3_000_000_000L, score = .1f)
        assertTrue(smoother.accept(low, 1, low.timestampNs)!!.scores.isEmpty())
        val uncertain = ClassificationSmoother()
        assertTrue(uncertain.accept(ambiguous, 1, ambiguous.timestampNs)!!.scores.isEmpty())
    }

    @Test fun staleFutureOtherSessionAndReorderedResultsCannotReplaceContext() {
        val smoother = ClassificationSmoother()
        assertNull(smoother.accept(result(1_000_000_000), 1, 3_000_000_000))
        assertNull(smoother.accept(result(4_000_000_000), 1, 3_000_000_000))
        assertNull(smoother.accept(result(3_000_000_000, session = 2), 1, 3_000_000_000))
        smoother.accept(result(3_000_000_000), 1, 3_000_000_000)
        smoother.accept(result(3_500_000_000), 1, 3_500_000_000)
        smoother.accept(result(3_200_000_000, "Knock"), 1, 3_600_000_000)
        smoother.accept(result(3_500_000_000, "Knock"), 1, 3_600_000_000)
        assertEquals("Speech", smoother.current(1, 3_600_000_000)!!.scores.first().label)
        assertNull(smoother.current(2, 3_600_000_000))
    }

    @Test fun longGapsAndSessionRestartsRequireNewConfirmation() {
        val smoother = ClassificationSmoother()
        smoother.accept(result(1_000_000_000), 1, 1_000_000_000)
        smoother.accept(result(1_500_000_000), 1, 1_500_000_000)
        assertTrue(smoother.accept(result(4_000_000_000, "Knock"), 1, 4_000_000_000)!!.scores.isEmpty())
        assertEquals("Knock", smoother.accept(result(4_500_000_000, "Knock"), 1, 4_500_000_000)!!.scores.first().label)
        assertTrue(smoother.accept(result(5_000_000_000, session = 2), 2, 5_000_000_000)!!.scores.isEmpty())
        smoother.reset()
        assertNull(smoother.current(2, 5_000_000_000))
    }

    @Test fun displayNamesUseExactOntologyLabelsAndKeepUntranslatedOriginals() {
        assertEquals("차량 경적", SoundLabels.display("Vehicle horn, car horn, honking"))
        assertEquals("노크", SoundLabels.display("Knock"))
        assertEquals("French horn", SoundLabels.display("French horn"))
        assertEquals("Unmapped event", SoundLabels.display("Unmapped event"))
    }

    @Test fun rawAndSmoothedWinnersMustAgreeBeforeShowingALabel() {
        val smoother = ClassificationSmoother()
        smoother.accept(result(1_000_000_000, score = .99f), 1, 1_000_000_000)
        smoother.accept(result(1_500_000_000, score = .99f), 1, 1_500_000_000)
        val changed = Classification(1, 2_000_000_000L, 3_500_000_000L,
            listOf(ClassScore("Knock", .6f), ClassScore("Speech", .45f)))
        assertTrue(smoother.accept(changed, 1, changed.timestampNs)!!.scores.isEmpty())
    }

    @Test fun keepsOnlyFiveSmoothedCandidatesAcrossChangingInputLists() {
        val smoother = ClassificationSmoother()
        val first = Classification(1, 1_000_000_000L, 2_500_000_000L,
            listOf(ClassScore("Speech", .9f)) + (1..6).map { ClassScore("other-$it", it / 10f) })
        smoother.accept(first, 1, first.timestampNs)
        val second = Classification(1, 1_500_000_000L, 3_000_000_000L,
            listOf(ClassScore("Speech", .7f), ClassScore("new", .2f)))
        val stable = smoother.accept(second, 1, second.timestampNs)!!
        assertEquals(5, stable.scores.size)
        assertEquals("Speech", stable.scores.first().label)
        // Hand-calculated EMA: 0.6*0.7 + 0.4*0.9 = 0.78; disappeared 0.6 -> 0.24.
        assertEquals(.78f, stable.scores.first().confidence, .000001f)
        assertEquals(.24f, stable.scores.first { it.label == "other-6" }.confidence, .000001f)
    }
}
