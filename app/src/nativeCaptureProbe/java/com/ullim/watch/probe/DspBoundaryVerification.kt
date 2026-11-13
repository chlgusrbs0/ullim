package com.ullim.watch.probe

import com.ullim.watch.audio.AudioFanOut
import com.ullim.watch.core.AudioFeatures
import com.ullim.watch.core.AudioFrame
import com.ullim.watch.dsp.DspProcessor
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Bounded synthetic-only native DSP measurements; reports scalar metadata. */
internal object DspBoundaryVerification {
    private fun active() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun input(session: Long, timestamp: Long, length: Int = 320, level: Int = 0) =
        AudioFrame(session, timestamp, ShortArray(length) { level.toShort() })

    fun run(report: (String) -> Unit) {
        val checked = DspProcessor()
        val reference = DspProcessor()
        check(checked.extract(input(1, 20_000_000, level = 10_000)) == reference.extract(input(1, 20_000_000, level = 10_000))) { "BOUNDARY_REFERENCE" }
        for (length in intArrayOf(1, 319, 321, 640)) {
            check(checked.extract(input(1, 40_000_000, length, 32_767)).isSilent) { "INVALID_LENGTH" }
        }
        check(checked.extract(input(1, 0, level = 32_767)).isSilent) { "REORDERED" }
        check(checked.extract(input(1, 20_000_000, level = 32_767)).isSilent) { "DUPLICATE" }
        check(checked.extract(input(1, 40_000_000, level = 5_000)) == reference.extract(input(1, 40_000_000, level = 5_000))) { "REJECTED_STATE_CHANGED" }
        check(checked.extract(input(1, 2_000_000_000)).isSilent) { "GAP_HISTORY" }
        checked.extract(input(2, 0, level = 12_000))
        check(checked.extract(input(1, 2_020_000_000, level = 32_767)).isSilent) { "OLD_SESSION" }

        var callbacks = 0
        var last: AudioFeatures? = null
        val pipeline = DspProcessor()
        val assembly = AudioFanOut { callbacks++; last = pipeline.extract(it) }
        assembly.start(1)
        assembly.accept(input(1, 0, 100, 10_000))
        check(callbacks == 0) { "PARTIAL_EARLY" }
        assembly.accept(input(1, 6_250_000, 540, 10_000))
        check(callbacks == 2 && last?.timestampNs == 20_000_000L) { "PARTIAL_ASSEMBLY" }
        assembly.accept(input(1, 40_000_000, 100, 32_767))
        assembly.stop()
        check(!assembly.accept(input(1, 46_250_000, 220, 32_767)) && callbacks == 2) { "STOPPED_TAIL" }
        assembly.start(2)
        check(!assembly.accept(input(1, 60_000_000, level = 32_767))) { "STOPPED_OLD_SESSION" }
        assembly.accept(input(2, 0))
        check(callbacks == 3 && last?.isSilent == true) { "RESTART_RESIDUE" }
        assembly.stop()
        report("boundaries lengths=true reordered=true duplicate=true referenceState=true gapReset=true partialStop=true oldSession=true")

        val dsp = DspProcessor()
        val durations = LongArray(30_000)
        var count = 0
        var consecutiveOver = 0
        var longestOver = 0
        var overBudget = 0
        val fanout = AudioFanOut { frame ->
            val began = System.nanoTime()
            val value = dsp.extract(frame)
            val elapsed = System.nanoTime() - began
            durations[count % durations.size] = elapsed
            count++
            check(value.rms.isFinite() && value.peak.isFinite() && value.envelope.isFinite() && value.noise.isFinite() && value.onset.isFinite() && value.level.isFinite()) { "NON_FINITE" }
            if (elapsed >= 20_000_000L) { overBudget++; consecutiveOver++ } else consecutiveOver = 0
            longestOver = maxOf(longestOver, consecutiveOver)
        }
        val heap = LongArray(4)
        fanout.start(1)
        for (burst in 0 until 4) {
            active()
            overBudget = 0
            longestOver = 0
            consecutiveOver = 0
            val began = System.nanoTime()
            repeat(30_000) { offset ->
                active()
                val index = burst * 30_000 + offset
                check(fanout.accept(AudioFrame(1, index * 20_000_000L, signal((index / 100) % 7, index)))) { "STRESS_REJECTED" }
            }
            val elapsedMs = (System.nanoTime() - began) / 1_000_000
            check(count == (burst + 1) * 30_000) { "STRESS_COUNT" }
            // Sample while the same DSP/fanout session and its pending AI window remain active.
            System.gc()
            System.runFinalization()
            Thread.sleep(100)
            System.gc()
            heap[burst] = Runtime.getRuntime().run { totalMemory() - freeMemory() }
            durations.sort()
            val p95 = durations[28_499]
            check(p95 < 20_000_000L && longestOver < 3) { "SUSTAINED_OVER_BUDGET" }
            report("stress burst=$burst frames=$count simulatedMinutes=${(burst + 1) * 10} elapsedMs=$elapsedMs p95Us=${p95 / 1_000} maxUs=${durations.last() / 1_000} over20ms=$overBudget longestOver=$longestOver activeJavaHeap=${heap[burst]}")
        }
        val growth = heap.last() - heap.first()
        check(growth < 4 * 1024 * 1024) { "ACTIVE_JAVA_HEAP_GROWTH" }
        check(fanout.takeLatestAi()?.sampleCount == 15_600 && fanout.takeLatestAi() == null) { "STRESS_AI_SLOT" }
        fanout.stop()
        report("stress activeJavaHeapGrowth=$growth limitBytes=4194304 fixtureTypes=7 retainedTimingSamples=30000")
    }

    private fun signal(kind: Int, frame: Int): ShortArray {
        val random = Random(frame)
        var filtered = 0f
        return ShortArray(320) { sample ->
            val seconds = (frame * 320L + sample) / 16_000.0
            val value = when (kind) {
                0 -> 0.0
                1 -> .001 * sin(2 * PI * 440 * seconds)
                2 -> if (sample % 2 == 0) -1.0 else 1.0
                3 -> .4 * sin(2 * PI * 440 * seconds)
                4 -> { filtered += (random.nextFloat() * 2 - 1 - filtered) * .2f; filtered * .2.toDouble() }
                5 -> if (frame % 20 == 0 && sample < 80) .8 * random.nextDouble(-1.0, 1.0) else 0.0
                else -> (.1 + .3 * (1 + sin(2 * PI * 4 * seconds)) / 2) * sin(2 * PI * 220 * seconds)
            }
            (value * 32_768).toInt().coerceIn(-32_768, 32_767).toShort()
        }
    }
}
