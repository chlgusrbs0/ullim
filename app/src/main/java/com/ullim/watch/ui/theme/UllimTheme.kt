package com.ullim.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Keeps the Wear OS Material 3 color and shape system as the source of truth.
 * Brand styling is intentionally restrained so system appearance remains legible.
 */
@Composable
fun UllimTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
