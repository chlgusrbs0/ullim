package com.ullim.watch.haptics

/** History is needed because microphone samples arrive after newer output requests. */
class MotorOutputTimeline(private val tailNs: Long = 120_000_000, private val maxWindows: Int = 256) {
    private data class Window(val start: Long, var end: Long, val amplitude: Int)
    private val windows = ArrayDeque<Window>()
    init { require(tailNs in 0..500_000_000 && maxWindows in 1..1024) }

    @Synchronized fun record(timings: LongArray, amplitudes: IntArray, requestedAtNs: Long) {
        require(timings.size == amplitudes.size)
        clip(requestedAtNs)
        var offset = 0L
        for (index in timings.indices) {
            val duration = timings[index] * 1_000_000
            if (duration > 0 && amplitudes[index] != 0) {
                windows.addLast(Window(requestedAtNs + offset, requestedAtNs + offset + duration,
                    if (amplitudes[index] == -1) 255 else amplitudes[index]))
            }
            offset += duration
        }
        while (windows.size > maxWindows) windows.removeFirst()
    }

    @Synchronized fun cancel(atNs: Long) { clip(atNs) }
    @Synchronized fun clear() { windows.clear() }

    @Synchronized fun amplitudeAt(sampleNs: Long, durationNs: Long): Int {
        var amplitude = 0
        for (window in windows) if (sampleNs < window.end + tailNs && sampleNs + durationNs > window.start) {
            amplitude = maxOf(amplitude, window.amplitude)
        }
        return amplitude
    }

    private fun clip(atNs: Long) {
        windows.removeAll { it.start >= atNs }
        for (window in windows) if (window.end > atNs) window.end = atNs
    }
}
