package com.ullim.watch.settings

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.LiveDsp
import org.junit.Assert.*
import org.junit.Test

class FeedbackDspIntegrationTest {
    @Test fun compensationRunsBeforeEnvelopeAndGateWithoutChangingFrameIdentity() {
        val dsp = LiveDsp(preprocess = { input -> AudioFrame(input.sessionId, input.timestampNs, ShortArray(320)) }) { 1f }
        val features = dsp.extract(AudioFrame(9, 20_000_000, ShortArray(320) { 16000 }))
        assertEquals(0f, features.rms, 0f)
        assertEquals(0f, features.level, 0f)
        assertEquals(9L, features.sessionId)
        assertEquals(20_000_000L, features.timestampNs)
    }
}
