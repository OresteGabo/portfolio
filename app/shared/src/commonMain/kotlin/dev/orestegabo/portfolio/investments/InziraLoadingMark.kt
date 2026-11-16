package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

private object LoadingContours {
    private fun path(data: String) = PathParser().parsePathString(data).toPath()
    val initial = path(InziraBrandGeometry.INITIAL)
    val frame = path(InziraBrandGeometry.FRAME)
    // This route lights the folds; clipping retains the exact master outline and its gaps.
    val flow = path("M150 95 C205 65 260 80 302 119 C285 130 254 145 235 157 L220 200 L209 238 C187 250 162 268 139 284 C170 311 199 337 220 326 L285 304")
    val flowMeasure = PathMeasure().apply { setPath(flow, false) }
    // PathMeasure reads the first (outer) contour of the compound frame.
    val frameMeasure = PathMeasure().apply { setPath(frame, false) }
}

/** Indeterminate brand loader. Idle/reduced motion shows the complete, stationary logo. */
@Composable
fun InziraLoadingMark(
    active: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
    reducedMotion: Boolean = LocalReducedMotion.current,
) {
    val reduced = reducedMotion
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phase = remember { mutableFloatStateOf(0f) }
    val trail = remember { Path() }
    LaunchedEffect(active, reduced, lifecycle) {
        phase.floatValue = 0f
        if (active && !reduced) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous: Long? = null
            while (isActive) withFrameNanos { now ->
                previous?.let { last ->
                    phase.floatValue = (phase.floatValue + (now - last).coerceAtMost(100_000_000L) / 2_600_000_000f) % 1f
                }
                previous = now
            }
        }
    }
    Canvas(modifier.then(if (active) Modifier.progressSemantics() else Modifier)
        .semantics { if (label != null) contentDescription = label }) {
        val unit = minOf(size.width / 440f, size.height / 440f)
        translate((size.width - 440f * unit) / 2f, (size.height - 400f * unit) / 2f) {
            scale(unit, unit, pivot = Offset.Zero) {
                if (!active || reduced) {
                    drawPath(LoadingContours.frame, tint)
                    drawPath(LoadingContours.initial, tint)
                } else {
                    // Read animation state in draw, keeping the form out of frame-by-frame recomposition.
                    val progress = phase.floatValue
                    drawPath(LoadingContours.frame, tint, alpha = .18f)
                    drawPath(LoadingContours.initial, tint, alpha = .22f)
                    fun segment(measure: PathMeasure, start: Float, fraction: Float, width: Float, alpha: Float = 1f) {
                        val from = ((start % 1f + 1f) % 1f) * measure.length
                        val end = from + fraction * measure.length
                        trail.reset()
                        measure.getSegment(from, minOf(end, measure.length), trail, true)
                        if (end > measure.length) measure.getSegment(0f, end - measure.length, trail, true)
                        drawPath(trail, tint, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    clipPath(LoadingContours.initial) {
                        segment(LoadingContours.flowMeasure, progress - .15f, .40f, 140f, .18f)
                        segment(LoadingContours.flowMeasure, progress, .25f, 100f)
                    }
                    clipPath(LoadingContours.frame) {
                        segment(LoadingContours.frameMeasure, progress, .24f, 30f)
                    }
                }
            }
        }
    }
}
