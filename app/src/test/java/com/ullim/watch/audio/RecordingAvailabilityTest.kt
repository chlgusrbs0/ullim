package com.ullim.watch.audio

import org.junit.Assert.*
import org.junit.Test

class RecordingAvailabilityTest {
    @Test fun metadataSilencingStopsInputAndAnOlderInitialQueryCannotUndoItsCallback() {
        val availability = RecordingAvailability()
        availability.checkReadable()
        availability.update(true)
        availability.initialize(false)
        assertEquals(CaptureError.SILENCED, assertThrows(CaptureException::class.java) { availability.checkReadable() }.reason)
        availability.update(false)
        availability.checkReadable()
    }
}
