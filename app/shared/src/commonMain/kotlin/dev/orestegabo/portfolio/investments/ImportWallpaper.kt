package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Stationary, theme-aware linework. Cached paths never depend on chat progress or scrolling. */
@Composable
internal fun ImportWallpaper(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.primary
    val dark = base.luminance() < .5f
    Spacer(modifier.clearAndSetSemantics {}.drawWithCache {
        val unit = 1.dp.toPx()
        val stepX = 104 * unit
        val stepY = 112 * unit
        val motifs = mutableListOf<Pair<Path, Float>>()
        for (row in -1..ceil(size.height / stepY).toInt()) {
            for (column in -1..ceil(size.width / stepX).toInt()) {
                val x = column * stepX + if (row % 2 == 0) 30 * unit else 82 * unit
                val y = row * stepY + 30 * unit
                val edge = (abs(x - size.width / 2) / (size.width / 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val alpha = (if (dark) .075f else .06f) * (.4f + edge * .6f)
                val motif = ((row + column) % 3 + 3) % 3
                val path = Path().apply {
                    when (motif) {
                        0 -> { // Open geometric seal: a quiet reference to the Struct mark.
                            val radius = 13 * unit
                            for (corner in 0..5) {
                                val angle = (corner * 60 - 30) * PI.toFloat() / 180
                                val px = x + cos(angle) * radius
                                val py = y + sin(angle) * radius
                                if (corner == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                            moveTo(x - 5 * unit, y + 3 * unit)
                            lineTo(x, y + 6 * unit); lineTo(x + 5 * unit, y + 3 * unit)
                        }
                        1 -> { // Folded document, drawn as linework rather than an icon tile.
                            moveTo(x - 8 * unit, y - 11 * unit)
                            lineTo(x + 3 * unit, y - 11 * unit); lineTo(x + 9 * unit, y - 5 * unit)
                            lineTo(x + 9 * unit, y + 11 * unit); lineTo(x - 8 * unit, y + 11 * unit); close()
                            moveTo(x + 3 * unit, y - 11 * unit); lineTo(x + 3 * unit, y - 5 * unit); lineTo(x + 9 * unit, y - 5 * unit)
                            moveTo(x - 3 * unit, y + unit); lineTo(x + 4 * unit, y + unit)
                            moveTo(x - 3 * unit, y + 5 * unit); lineTo(x + 2 * unit, y + 5 * unit)
                        }
                        else -> { // Small financial graph, without axes or numbers.
                            moveTo(x - 13 * unit, y + 6 * unit); lineTo(x - 5 * unit, y - unit)
                            lineTo(x + unit, y + 3 * unit); lineTo(x + 13 * unit, y - 8 * unit)
                            moveTo(x + 6 * unit, y - 8 * unit); lineTo(x + 13 * unit, y - 8 * unit)
                            lineTo(x + 13 * unit, y - unit)
                        }
                    }
                }
                motifs += path to alpha
            }
        }
        val wash = Brush.verticalGradient(listOf(ink.copy(alpha = if (dark) .025f else .018f).compositeOver(base), base))
        val glow = Brush.radialGradient(listOf(ink.copy(alpha = if (dark) .04f else .035f), Color.Transparent),
            center = Offset(size.width * .95f, size.height * .15f), radius = max(size.width, size.height * .65f).coerceAtLeast(1f))
        val stroke = Stroke(.8f * unit, cap = StrokeCap.Round)
        onDrawBehind {
            drawRect(wash)
            drawRect(glow)
            motifs.forEach { (path, alpha) -> drawPath(path, ink.copy(alpha = alpha), style = stroke) }
        }
    })
}
