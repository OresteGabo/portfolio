package dev.orestegabo.portfolio.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.test.*

class DesignColorsTest {
    @Test
    fun brandedTextAndActionPairsKeepReadableContrastInBothThemes() {
        fun contrast(a: Color, b: Color): Double {
            val luminances = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sorted()
            return (luminances[1] + .05) / (luminances[0] + .05)
        }
        for (dark in listOf(false, true)) {
            val colors = structCapitalColorScheme(dark)
            assertTrue(
                contrast(colors.primary, colors.onPrimary) >= 4.5,
                "Primary action contrast ($dark)"
            )
            assertTrue(
                contrast(colors.surface, colors.onSurface) >= 4.5,
                "Surface text contrast ($dark)"
            )
            assertTrue(
                contrast(colors.primaryContainer, colors.onPrimaryContainer) >= 4.5,
                "Container text contrast ($dark)"
            )
        }
    }

    @Test
    fun pdfOperandsStayPortableAndWithinTheirDeclaredPrecision() {
        val color = Color(.123456f, .987654f, .00001f)
        for (precision in 0..5) {
            val encoded = color.pdfRgb(precision)
            assertTrue(
                Regex("[0-9]+(?:\\.[0-9]+)? [0-9]+(?:\\.[0-9]+)? [0-9]+(?:\\.[0-9]+)?").matches(
                    encoded
                )
            )
            val parsed = encoded.split(' ').map(String::toDouble)
            val tolerance = .5 / (0 until precision).fold(1) { value, _ -> value * 10 } + .000001
            listOf(color.red, color.green, color.blue).zip(parsed).forEach { (original, operand) ->
                assertTrue(abs(original - operand) <= tolerance)
            }
        }
        assertEquals("1 1 1", ReportColors.onBanner)
    }
}
