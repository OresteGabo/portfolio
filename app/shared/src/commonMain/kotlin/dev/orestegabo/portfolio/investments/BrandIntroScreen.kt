package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

// Same canonical 440 x 400 initial-and-frame geometry as the static brand mark.
private object IntroContours {
    private fun path(data: String) = PathParser().parsePathString(data).toPath()
    val initial = path(InziraBrandGeometry.INITIAL)
    val frame = path(InziraBrandGeometry.FRAME)
    // Follow the diagonal cut into the initial; keep the mark stationary.
    val journey = path(InziraBrandGeometry.JOURNEY)
}

/** Content arrives as the signature travels into the real welcome artwork. */
internal fun brandWelcomeReveal(progress: Float): Float = brandPhase(progress, .77f, .99f)

private fun brandPhase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

/** Uses the same centered 440-unit viewport as StructMark for an exact vector-to-vector handoff. */
@Composable
internal fun BrandIntroOverlay(progress: Float, destination: Rect?) {
    val ink = MaterialTheme.colorScheme.primary
    val tracing = brandPhase(progress, .03f, .53f)
    val materialize = brandPhase(progress, .48f, .63f)
    val ribbon = brandPhase(progress, .13f, .57f)
    val route = brandPhase(progress, .04f, .59f)
    val routeFade = 1f - brandPhase(progress, .58f, .70f)
    val wordmark = brandPhase(progress, .55f, .68f) * (1f - brandPhase(progress, .76f, .90f))
    val travel = brandPhase(progress, .76f, 1f)
    Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "Inzira" }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val startingSide = minOf(244.dp.toPx(), size.width * .64f, size.height * .40f)
            val startingTop = (size.height - startingSide) / 2f - 28.dp.toPx()
            val initial = Rect((size.width - startingSide) / 2f, startingTop,
                (size.width + startingSide) / 2f, startingTop + startingSide)
            val target = destination ?: initial
            val left = initial.left + (target.left - initial.left) * travel
            val top = initial.top + (target.top - initial.top) * travel
            val side = initial.width + (target.width - initial.width) * travel
            translate(left, top) {
                scale(side / 440f, side / 440f, pivot = Offset.Zero) {
                    translate(0f, 20f) {
                        // Trace the actual edges first; the solid mark grows out of those edges.
                        for (contour in listOf(IntroContours.frame, IntroContours.initial)) {
                            val measure = PathMeasure().apply { setPath(contour, false) }
                            val drawn = Path()
                            measure.getSegment(0f, measure.length * tracing, drawn, true)
                            drawPath(drawn, ink, alpha = (1f - materialize) * .65f,
                                style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            val laid = Path()
                            measure.getSegment(0f, measure.length * ribbon, laid, true)
                            clipPath(contour) {
                                drawPath(laid, ink, style = Stroke(150f, cap = StrokeCap.Round,
                                    join = StrokeJoin.Round))
                            }
                            drawPath(contour, ink, alpha = materialize)
                        }
                        if (route > 0f && routeFade > 0f) {
                            val measure = PathMeasure().apply { setPath(IntroContours.journey, false) }
                            val trail = Path()
                            val distance = measure.length * route
                            measure.getSegment((distance - 115f).coerceAtLeast(0f), distance, trail, true)
                            drawPath(trail, ink, alpha = .10f * routeFade,
                                style = Stroke(19f, cap = StrokeCap.Round))
                            drawPath(trail, ink, alpha = routeFade,
                                style = Stroke(3.5f, cap = StrokeCap.Round))
                            val head = measure.getPosition(distance)
                            drawCircle(ink, 12f, head, alpha = .10f * routeFade)
                            drawCircle(ink, 4f, head, alpha = routeFade)
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val startingSide = minOf(244.dp, maxWidth * .64f, maxHeight * .40f)
            InziraWordmark(Modifier.height(26.dp).align(Alignment.TopCenter)
                .offset(y = (maxHeight + startingSide) / 2f - 12.dp)
                .graphicsLayer {
                    alpha = wordmark
                    translationY = 5.dp.toPx() * (1f - brandPhase(progress, .55f, .68f))
                })
        }
    }
}
