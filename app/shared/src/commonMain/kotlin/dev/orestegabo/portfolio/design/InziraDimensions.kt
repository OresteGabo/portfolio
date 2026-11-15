package dev.orestegabo.portfolio.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Layout rhythm for shared controls; artwork/chart geometry keeps its own dimensions. */
object InziraSpacing {
    val tight = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val screen = 20.dp
    val section = 24.dp
    val spacious = 32.dp
}

object InziraDimensions {
    val inlineIcon = 20.dp
    val progressIndicator = 28.dp
    val progressStroke = 3.dp
    val primaryActionMinHeight = 56.dp
    val formMaxWidth = 600.dp
}

object InziraShapes {
    val action = RoundedCornerShape(18.dp)
    val card = RoundedCornerShape(24.dp)
    val spaciousCard = RoundedCornerShape(28.dp)
}
