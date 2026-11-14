package com.ullim.watch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.wear.compose.material3.ColorScheme

internal fun contrastRatio(foreground: Color, background: Color): Double {
    val first = foreground.compositeOver(background).luminance().toDouble()
    val second = background.luminance().toDouble()
    return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
}

internal fun accessibleColors(candidate: ColorScheme): ColorScheme {
    val textPairs = listOf(
        candidate.onBackground to candidate.background,
        candidate.onSurface to candidate.surfaceContainer,
        candidate.onSurface to candidate.background,
        candidate.onSurfaceVariant to candidate.background,
        candidate.onPrimary to candidate.primary,
        candidate.onPrimaryContainer to candidate.primaryContainer,
        candidate.primary to candidate.background,
    )
    return if (textPairs.all { contrastRatio(it.first, it.second) >= 4.5 } &&
        contrastRatio(candidate.secondary, candidate.background) >= 3.0 &&
        contrastRatio(candidate.primary.copy(alpha = .8f), candidate.background) >= 3.0 &&
        contrastRatio(candidate.outline, candidate.surfaceContainer) >= 3.0 &&
        contrastRatio(candidate.outline, candidate.background) >= 3.0) candidate else ColorScheme()
}
