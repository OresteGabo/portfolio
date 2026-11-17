package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Static, low-contrast edge motifs; no motion or personal information behind the controls. */
@Composable
internal fun UnlockBackdrop(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val frame = remember { PathParser().parsePathString(InziraBrandGeometry.FRAME).toPath() }
    val initial = remember { PathParser().parsePathString(InziraBrandGeometry.INITIAL).toPath() }
    // Sparse, deliberately asymmetric placement keeps the central reading column quiet.
    val motifs = remember {
        listOf(
            Triple(.01f, .12f, 92f), Triple(.97f, .28f, 124f),
            Triple(-.04f, .49f, 148f), Triple(1.03f, .68f, 100f),
            Triple(.06f, .92f, 116f), Triple(.88f, 1.01f, 156f),
        )
    }
    Canvas(modifier.clipToBounds().clearAndSetSemantics {}) {
        motifs.forEachIndexed { index, (x, y, diameter) ->
            val side = diameter.dp.toPx()
            val unit = side / 440f
            translate(size.width * x - side / 2, size.height * y - side / 2) {
                scale(unit, unit, pivot = Offset.Zero) {
                    drawPath(frame, ink, alpha = .045f)
                    if (index % 2 == 1) drawPath(initial, ink, alpha = .025f, style = Stroke(2f / unit))
                }
            }
        }
    }
}
