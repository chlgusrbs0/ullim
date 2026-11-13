package com.ullim.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Uses the watch's Wear Material 3 palette when dynamic colors are available.
 * The Wear defaults remain the fallback and provide the round-screen shape system.
 */
@Composable
fun UllimTheme(content: @Composable () -> Unit) {
    val fallbackColorScheme = MaterialTheme.colorScheme
    val colorScheme = dynamicColorScheme(LocalContext.current) ?: fallbackColorScheme
    val defaultTypography = MaterialTheme.typography
    val typography = defaultTypography.copy(
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
