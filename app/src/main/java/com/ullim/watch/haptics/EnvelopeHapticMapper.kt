package com.ullim.watch.haptics

import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.Classification
import com.ullim.watch.core.HapticFrame
import com.ullim.watch.core.HapticMapper

class EnvelopeHapticMapper : HapticMapper {
    private var previous = 0f
    private var sessionId = 0L

    override fun map(features: AudioFeatures, context: Classification?): HapticFrame {
        if (features.sessionId != sessionId) {
            reset()
            sessionId = features.sessionId
        }
        if (features.isSilent || features.rms <= maxOf(0.002f, features.noise * 1.1f)) previous = 0f
        else {
            val level = (features.level + features.onset * 0.1f * (1f - features.level)).coerceIn(0f, 1f)
            // Give ordinary audible levels more of the available output range,
            // with a gentler upper slope to retain differences near the maximum.
            val target = if (level <= .5f) level * 1.6f else .8f + (level - .5f) * .4f
            previous += (target - previous) * if (target > previous) 0.7f else 0.45f
        }
        return HapticFrame(features.sessionId, features.timestampNs, 20, previous.coerceIn(0f, 1f))
    }

    override fun reset() {
        previous = 0f
        sessionId = 0
    }
}
