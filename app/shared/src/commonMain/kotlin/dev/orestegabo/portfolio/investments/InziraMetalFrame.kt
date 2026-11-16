package dev.orestegabo.portfolio.investments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import dev.orestegabo.portfolio.design.StructColors

private object MetalFrame {
    private fun path(data: String): Path = PathParser().parsePathString(data).toPath()
    val shape = path(InziraBrandGeometry.FRAME)
    // Union the slices once: separately stroked slices leave visible ribs on the sidewalls.
    val body = run {
        var body = shape
        for (depth in 1..9) {
            val slice = Path().apply { addPath(shape, Offset(depth * .55f, depth.toFloat())) }
            body = Path.combine(PathOperation.Union, body, slice)
        }
        body
    }
    val shadow = lerp(StructColors.Brand.olive, StructColors.Brand.ink, .80f)
    val midtone = lerp(StructColors.Brand.olive, StructColors.Brand.cream, .36f)
    val highlight = lerp(StructColors.Brand.cream, StructColors.Brand.canvas, .55f)
    val face = Brush.linearGradient(
        0f to highlight, .12f to midtone, .39f to StructColors.Brand.olive,
        .54f to midtone, .63f to StructColors.Brand.cream, .68f to midtone, 1f to StructColors.Brand.olive,
        start = Offset(40f, 0f), end = Offset(370f, 400f),
    )
    val edge = Brush.linearGradient(
        0f to highlight, .25f to midtone, .48f to shadow,
        .68f to StructColors.Brand.cream, 1f to shadow,
        start = Offset(20f, 30f), end = Offset(410f, 390f),
    )
}

/** Stationary dashboard surround, using the canonical open hexagon and the established gold finish. */
internal fun DrawScope.drawInziraMetalFrame(dark: Boolean) {
    val shape = MetalFrame.shape
    // Soft layered contact shadow, expressed in master units so it scales with the artwork.
    for (spread in 4 downTo 1) translate(3f, 8f + spread * 2f) {
        drawPath(shape, StructColors.Brand.ink, alpha = if (dark) .025f else .035f,
            style = Stroke(spread * 3f, join = StrokeJoin.Round))
    }
    drawPath(MetalFrame.body, MetalFrame.edge)
    drawPath(MetalFrame.body, MetalFrame.shadow, alpha = .35f)
    drawPath(shape, MetalFrame.face)
    // The compound frame includes its inner edge; clipping protects the open center and cuts.
    clipPath(shape) {
        translate(-1.4f, -1.8f) {
            drawPath(shape, MetalFrame.shadow, alpha = .7f,
                style = Stroke(4f, join = StrokeJoin.Round))
        }
        translate(1.2f, 1.4f) {
            drawPath(shape, MetalFrame.highlight, alpha = .85f,
                style = Stroke(3.2f, join = StrokeJoin.Round))
        }
    }
    drawPath(shape, MetalFrame.edge,
        style = Stroke(.9f, join = StrokeJoin.Round))
}
