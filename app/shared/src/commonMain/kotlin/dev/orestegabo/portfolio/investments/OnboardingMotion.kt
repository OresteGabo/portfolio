package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Offscreen pager neighbours stay still. Each visit plays once; backgrounding pauses the story. */
@Composable
internal fun rememberOnboardingMotion(active: Boolean, durationMillis: Int): Float {
    val reduced = LocalReducedMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(active, reduced, lifecycle) {
        if (reduced) progress.snapTo(1f)
        if (active && !reduced) {
            progress.snapTo(0f)
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(180)
                progress.animateTo(1f, tween(
                    durationMillis = ((1f - progress.value) * durationMillis).toInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ))
                awaitCancellation()
            }
        }
    }
    return progress.value
}

internal fun onboardingPhase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

/** A short illuminated tail follows the actual connector, then disappears at its destination. */
internal fun DrawScope.drawOnboardingSignal(path: Path, progress: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val trail = Path()
    val distance = measure.length * progress
    measure.getSegment((distance - measure.length * .20f).coerceAtLeast(0f), distance, trail, true)
    val alpha = minOf(progress / .08f, (1f - progress) / .12f, 1f)
    drawPath(trail, color, alpha = alpha * .8f, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    val head = measure.getPosition(distance)
    drawCircle(color, 5.dp.toPx(), head, alpha = alpha * .12f)
    drawCircle(color, 2.2.dp.toPx(), head, alpha = alpha)
}
