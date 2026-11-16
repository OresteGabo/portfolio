package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Decorative dashboard identity: only the inner initial turns; the metallic hexagon is stationary. */
@Composable
fun DashboardBrandMark(modifier: Modifier = Modifier, reducedMotion: Boolean = LocalReducedMotion.current) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reducedMotion, lifecycle) {
        angle.floatValue = 0f
        if (!reducedMotion) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous: Long? = null
            while (isActive) withFrameNanos { now ->
                previous?.let { last ->
                    angle.floatValue = (angle.floatValue + (now - last).coerceAtMost(100_000_000L) * 360f / 6_000_000_000f) % 360f
                }
                previous = now
            }
        }
    }
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Inzira" }) {
        val unit = minOf(size.width / 440f, size.height / 440f)
        translate((size.width - 440f * unit) / 2f, (size.height - 400f * unit) / 2f) {
            scale(unit, unit, pivot = Offset.Zero) {
                // State is read only in drawing; balances and the rest of the dashboard do not recompose.
                drawRotatingInziraInitial(angle.floatValue, dark)
                drawInziraMetalFrame(dark)
            }
        }
    }
}
