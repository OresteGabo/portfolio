package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.isActive
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Optional, pausable brand motion. Authentication never waits for or depends on this artwork. */
@Composable
fun UnlockBrandArtwork(modifier: Modifier = Modifier, reducedMotion: Boolean = LocalReducedMotion.current) {
    val ink = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    var paused by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phase = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion, paused, lifecycle) {
        if (reducedMotion) phase.snapTo(0f)
        else if (!paused) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                phase.animateTo(target, tween(6_000, easing = FastOutSlowInEasing))
                target = if (target == 1f) 0f else 1f
            }
        }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val center = Offset(size.width / 2, size.height * .48f)
            val radius = minOf(size.width * .43f, size.height * .48f)
            if (radius <= 0f) return@Canvas
            drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = .10f), ink.copy(alpha = .025f), Color.Transparent), center, radius),
                radius, center)
            drawCircle(ink, radius * .86f, center, alpha = .12f, style = Stroke(.7.dp.toPx()))
            drawArc(ink, 212f, 58f, false, center - Offset(radius, radius), Size(radius * 2, radius * 2),
                alpha = .18f, style = Stroke(1.dp.toPx()))
            drawArc(ink, 32f, 58f, false, center - Offset(radius, radius), Size(radius * 2, radius * 2),
                alpha = .18f, style = Stroke(1.dp.toPx()))
            val side = minOf(size.width * .64f, size.height * .79f)
            val unit = side / 440f
            translate(center.x - side / 2, center.y - 200f * unit) {
                scale(unit, unit, pivot = Offset.Zero) {
                    // Full turns reverse smoothly; the hexagon stays fixed.
                    drawRotatingInziraInitial(phase.value * 360f - 22f, dark)
                    drawInziraMetalFrame(dark)
                }
            }
        }
        if (!reducedMotion) IconButton(
            onClick = { paused = !paused },
            modifier = Modifier.align(Alignment.BottomEnd).size(48.dp),
        ) {
            AppIcon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                if (paused) "Play logo animation" else "Pause logo animation",
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
