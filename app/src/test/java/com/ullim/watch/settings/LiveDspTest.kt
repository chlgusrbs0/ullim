package com.ullim.watch.settings

import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.LiveDsp
import com.ullim.watch.haptics.HybridHapticMapper
import com.ullim.watch.runtime.CaptureRuntime
import org.junit.Assert.*
import org.junit.Test

class LiveDspTest {
    @Test fun appliedRuntimeSettingChangesTheNextActualDspAndMappedFrameWithoutRestart() {
        CaptureRuntime.applySettings(UserSettings(.75f))
        try {
            val live = LiveDsp { CaptureRuntime.sensitivity.value }
            val reference = LiveDsp { .75f }
            val mapper = HybridHapticMapper()
            val referenceMapper = HybridHapticMapper()
            repeat(30) { i ->
                if (i == 10) CaptureRuntime.applySettings(UserSettings(1.25f))
                val frame = AudioFrame(1, i * 20_000_000L, ShortArray(320) { if (it % 2 == 0) 2000 else -2000 })
                val features = live.extract(frame)
                val fixed = reference.extract(frame)
                val command = mapper.map(features)
                val fixedCommand = referenceMapper.map(fixed)
                if (i < 10) assertEquals(fixedCommand, command)
                else {
                    assertTrue(features.level > fixed.level)
                    assertTrue(command.commandedIntensity > fixedCommand.commandedIntensity)
                }
                assertEquals(frame.timestampNs, command.timestampNs)
                assertEquals(20L, command.durationMs)
                assertTrue(command.commandedIntensity <= .85f)
            }
        } finally { CaptureRuntime.applySettings(UserSettings()) }
    }
}
