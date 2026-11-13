package com.ullim.watch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Follows the current watch-face palette when Wear OS provides dynamic colors.
 * Wear Material 3 defaults are used when dynamic colors are unavailable.
 */
@Composable
fun UllimTheme(content: @Composable () -> Unit) {
    val watchColorScheme = dynamicColorScheme(LocalContext.current)
    val readableColors = remember(watchColorScheme) { accessibleColors(watchColorScheme ?: ColorScheme()) }
    MaterialTheme(
        colorScheme = readableColors,
        typography = Typography(defaultFontFamily = FontFamily.SansSerif),
        content = content,
    )
}
