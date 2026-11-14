package com.ullim.watch.ui

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import com.ullim.watch.ui.theme.accessibleColors
import com.ullim.watch.ui.theme.contrastRatio
import org.junit.Assert.*
import org.junit.Test

class AccessibleColorsTest {
    @Test fun actualFallbackTextButtonAndChartPairsHaveMeasuredContrast() {
        val colors = ColorScheme()
        assertTrue(contrastRatio(colors.onBackground, colors.background) >= 4.5)
        assertTrue(contrastRatio(colors.onSurface, colors.surfaceContainer) >= 4.5)
        assertTrue(contrastRatio(colors.onSurface, colors.background) >= 4.5)
        assertTrue(contrastRatio(colors.onSurfaceVariant, colors.background) >= 4.5)
        assertTrue(contrastRatio(colors.onPrimary, colors.primary) >= 4.5)
        assertTrue(contrastRatio(colors.primary, colors.background) >= 4.5)
        assertTrue(contrastRatio(colors.secondary, colors.background) >= 3.0)
        assertTrue(contrastRatio(colors.primary.copy(alpha = .8f), colors.background) >= 3.0)
        assertTrue(contrastRatio(colors.outline, colors.surfaceContainer) >= 3.0)
        assertTrue(contrastRatio(colors.outline, colors.background) >= 3.0)
    }

    @Test fun lowContrastCandidateFallsBackAndGoodDynamicCandidateIsPreserved() {
        val fallback = ColorScheme()
        val bad = fallback.copy(onBackground = fallback.background)
        assertEquals(fallback.onBackground, accessibleColors(bad).onBackground)
        assertSame(fallback, accessibleColors(fallback))
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), .001)
        assertEquals(1.0, contrastRatio(Color.Black, Color.Black), .001)
    }
}
