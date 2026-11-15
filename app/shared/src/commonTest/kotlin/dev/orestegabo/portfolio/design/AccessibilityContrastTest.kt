package dev.orestegabo.portfolio.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class AccessibilityContrastTest {
    @Test fun dashboardLabelsStayReadableOnBothThemesIncludingDecorativeTint() {
        for (dark in listOf(false, true)) {
            val foreground = StructColors.Dashboard.insightForeground.resolve(dark)
            val gradients = listOf(StructColors.Dashboard.allocationGradient, StructColors.Dashboard.incomeGradient,
                StructColors.Dashboard.paymentGradient)
            val accents = listOf(StructColors.Dashboard.allocationAccent, StructColors.Dashboard.incomeAccent,
                StructColors.Dashboard.paymentAccent)
            gradients.zip(accents).forEach { (gradient, accent) ->
                gradient.resolve(dark).forEach { surface ->
                    // Conservative combined tint envelope for the decorative glow and fine paths.
                    val tinted = accent.resolve(dark).copy(alpha = .3f).compositeOver(surface)
                    assertTrue(contrast(foreground, surface) >= 4.5)
                    assertTrue(contrast(foreground, tinted) >= 4.5)
                }
            }
        }
    }

    @Test fun credentialBoundaryAndFocusRemainVisibleInBothThemes() {
        for (dark in listOf(false, true)) {
            val colors = structCapitalColorScheme(dark)
            val fill = colors.onSurface.copy(alpha = .025f).compositeOver(colors.surface)
            assertTrue(contrast(colors.outline, fill) >= 3.0, "Field boundary ($dark)")
            assertTrue(contrast(colors.primary, fill) >= 3.0, "Focused field boundary ($dark)")
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val low = minOf(a.luminance(), b.luminance()).toDouble()
        val high = maxOf(a.luminance(), b.luminance()).toDouble()
        return (high + .05) / (low + .05)
    }
}
