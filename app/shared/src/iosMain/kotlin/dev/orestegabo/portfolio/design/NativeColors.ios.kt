package dev.orestegabo.portfolio.design

import androidx.compose.ui.graphics.Color
import platform.UIKit.UIColor

/** Native covers and document rendering consume the same sRGB roles as Compose. */
internal fun Color.uiColor(): UIColor = UIColor(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble()
)
