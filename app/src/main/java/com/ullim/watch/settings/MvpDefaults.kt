package com.ullim.watch.settings

import com.ullim.watch.settings.TuningParameter.*

/** Final user-selected choices captured from the watch on 2026-11-14. Saved settings take precedence. */
object MvpDefaults {
    const val SENSITIVITY = 1.25f
    const val AI_ENABLED = true
    val tuning: TuningSettings = TuningSettings.decode { parameter ->
        when (parameter) {
            REFERENCE_RMS -> 0.5f
            LOUDNESS_KNEE -> 1.5f
            MIN_RMS -> 0.006f
            NOISE_MARGIN -> 1f
            OPEN_OFFSET -> 0.015f
            CLOSE_OFFSET -> 0.005f
            OPEN_RATIO -> 1.2f
            CLOSE_RATIO -> 0.7f
            NOISE_RISE -> 0.005f
            NOISE_FALL -> 0.05f
            ATTACK_MS -> 20f
            RELEASE_MS -> 120f
            OUTPUT_ATTACK -> 0.8f
            OUTPUT_RELEASE -> 0.8f
            GAP_HOLD_MS -> 20f
            OUTPUT_GAIN -> 1f
            CONTRAST -> 1f
            ONSET_ACCENT -> 0.2f
            WEAK_OUTPUT -> 0.1f
            MIDDLE_OUTPUT -> 0.42349127f
            STRONG_OUTPUT -> 1f
            OUTPUT_FLOOR -> 0.04f
            OUTPUT_CEILING -> 1f
            LONG_START -> 0.6f
            LONG_END -> 0.75f
            GROUP_MS -> 60f
            MIN_ON_MS -> 3f
            SIMILARITY_MS -> 2f
            MOTOR_SUBTRACTION -> 1.5f
            MOTOR_TAIL_MS -> 200f
            AI_CONFIDENCE -> 0.65f
            AI_MARGIN -> 0.08f
            AI_SMOOTHING -> 0.35f
            AI_LIMIT -> 0.03f
            AI_WAIT_MS -> 500f
        }
    }
}
