package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class OnboardingChart { ALLOCATION, VALUE, DATES }

/** Illustrative diagrams have no amounts or axes suggesting live financial performance. */
@Composable
internal fun OnboardingChartArtwork(kind: OnboardingChart, progress: Float, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 64f
        fun point(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun trace(path: Path, amount: Float, width: Float = 1.1f) {
            val measure = PathMeasure().apply { setPath(path, false) }
            val drawn = Path()
            if (amount > 0f) {
                measure.getSegment(0f, measure.length * amount, drawn, true)
                drawPath(drawn, ink, style = Stroke(width.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        when (kind) {
            OnboardingChart.ALLOCATION -> {
                drawLine(ink, point(12f, 6f), point(12f, 57f), 1.dp.toPx(), alpha = .45f)
                drawLine(ink, point(12f, 57f), point(94f, 57f), 1.dp.toPx(), alpha = .45f)
                listOf(16f, 29f, 42f, 33f, 50f).forEachIndexed { index, height ->
                    val growth = onboardingPhase(progress, index * .10f, .54f + index * .10f)
                    val top = 56f - height * growth
                    val left = 20f + index * 14f
                    drawRect(ink, point(left, top), Size(8f * sx, height * growth * sy), alpha = .16f)
                    if (growth > 0f) drawRect(ink, point(left, top), Size(8f * sx, height * growth * sy),
                        style = Stroke(.85.dp.toPx()))
                    if (progress > 0f && progress < .98f && growth > 0f && growth < 1f) {
                        drawCircle(ink, 2.dp.toPx(), point(left + 4f, top), alpha = .55f)
                    }
                }
            }
            OnboardingChart.VALUE -> {
                for (y in listOf(14f, 28f, 42f, 56f)) drawLine(ink, point(12f, y), point(94f, y), .5.dp.toPx(), alpha = .15f)
                for (x in listOf(12f, 32f, 52f, 72f, 92f)) drawLine(ink, point(x, 6f), point(x, 56f), .5.dp.toPx(), alpha = .12f)
                val coordinates = listOf(14f to 52f, 24f to 38f, 33f to 44f, 43f to 29f,
                    52f to 37f, 63f to 20f, 72f to 28f, 82f to 12f, 92f to 7f)
                val path = Path().apply { coordinates.forEachIndexed { index, (x, y) ->
                    if (index == 0) moveTo(x * sx, y * sy) else lineTo(x * sx, y * sy)
                } }
                val p = onboardingPhase(progress, 0f, .95f)
                val measure = PathMeasure().apply { setPath(path, false) }
                if (p > 0f) {
                    val area = Path().apply {
                        measure.getSegment(0f, measure.length * p, this, true)
                        val end = measure.getPosition(measure.length * p)
                        lineTo(end.x, 56f * sy); lineTo(14f * sx, 56f * sy); close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(ink.copy(alpha = .18f), ink.copy(alpha = 0f))))
                    trace(path, p, 1.4f)
                    val head = measure.getPosition(measure.length * p)
                    drawCircle(ink, 4.dp.toPx(), head, alpha = .12f)
                    drawCircle(ink, 1.7.dp.toPx(), head)
                }
            }
            OnboardingChart.DATES -> {
                val outline = Path().apply {
                    moveTo(28f * sx, 10f * sy); lineTo(74f * sx, 10f * sy)
                    lineTo(78f * sx, 14f * sy); lineTo(78f * sx, 57f * sy)
                    lineTo(24f * sx, 57f * sy); lineTo(24f * sx, 14f * sy); close()
                }
                trace(outline, onboardingPhase(progress, 0f, .42f))
                drawLine(ink, point(24f, 22f), point(78f, 22f), .8.dp.toPx(), alpha = onboardingPhase(progress, .15f, .4f))
                for (x in listOf(36f, 66f)) drawLine(ink, point(x, 6f), point(x, 15f), 2.dp.toPx(),
                    alpha = onboardingPhase(progress, .10f, .35f))
                for (row in 0..2) for (col in 0..3) {
                    val p = onboardingPhase(progress, .30f + (row * 4 + col) * .025f, .53f + (row * 4 + col) * .025f)
                    drawRect(ink, point(30f + col * 11f, 28f + row * 8f), Size(6f * sx, 4f * sy), alpha = .3f * p)
                }
                val check = Path().apply { moveTo(43f * sx, 40f * sy); lineTo(48f * sx, 45f * sy); lineTo(60f * sx, 32f * sy) }
                trace(check, onboardingPhase(progress, .66f, 1f), 1.7f)
            }
        }
    }
}
