package dev.orestegabo.portfolio.design

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** PDF sRGB operands. Print-specific shades retain the original renderer's precision. */
internal object ReportColors {
    const val ink = "0.13 0.16 0.14"
    const val muted = "0.38 0.42 0.39"
    val accent get() = StructColors.Brand.olive.pdfRgb(2)
    val logoInk get() = StructColors.Brand.olive.pdfRgb(5)
    val onBanner get() = StructColors.Fixed.white.pdfRgb(0)
    const val sectionBacking = "0.98 0.97 0.94"
    const val headingRule = "0.85 0.84 0.77"
    const val banner = "0.14 0.19 0.16"
    const val bannerMotif = "0.28 0.31 0.20"
    const val bannerLabel = "0.83 0.79 0.56"
    const val progressTrack = "0.25 0.38 0.30"
    const val progressFill = "0.65 0.55 0.22"
    const val metricBacking = "0.975 0.977 0.965"
    const val metricAccent = "0.68 0.60 0.30"
    const val alternateRow = "0.965 0.968 0.951"
    const val tableHeader = "0.18 0.23 0.19"
    const val barTrack = "0.93 0.93 0.89"
    const val barFill = "0.59 0.48 0.14"
    const val gain = "0.08 0.43 0.27"
    const val loss = "0.71 0.18 0.18"
    const val rule = "0.87 0.88 0.84"
}

/** Locale-independent PDF numbers; no exponent notation, commas or platform formatting. */
internal fun Color.pdfRgb(decimals: Int): String {
    require(decimals in 0..5)
    val scale = (0 until decimals).fold(1) { value, _ -> value * 10 }
    fun channel(value: Float): String {
        val scaled = (value * scale).roundToInt()
        if (decimals == 0) return scaled.toString()
        return "${scaled / scale}.${(scaled % scale).toString().padStart(decimals, '0')}".trimEnd(
            '0'
        ).trimEnd('.')
    }
    return listOf(red, green, blue).joinToString(" ", transform = ::channel)
}
