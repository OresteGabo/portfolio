package dev.orestegabo.portfolio.investments

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val ContentMotionMillis = 240

/** Layout changes settle together instead of snapping when asynchronous content arrives. */
@Composable
internal fun Modifier.smoothContentSize(): Modifier = if (LocalReducedMotion.current) this else
    animateContentSize(tween(ContentMotionMillis, easing = FastOutSlowInEasing), alignment = Alignment.TopStart)

/** Fade while reclaiming space smoothly. */
@Composable
internal fun AppReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(visible, modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(tween(180)) + expandVertically(tween(ContentMotionMillis), expandFrom = Alignment.Top),
        exit = if (reduced) ExitTransition.None else fadeOut(tween(140)) + shrinkVertically(tween(ContentMotionMillis), shrinkTowards = Alignment.Top),
    ) { content() }
}

/** Charts reserve the thin loading slot so the plot and its labels never move when it clears. */
@Composable
internal fun AppLoadingLine(visible: Boolean, reserveSpace: Boolean = false) {
    if (reserveSpace) {
        val alpha by animateFloatAsState(if (visible) 1f else 0f,
            tween(if (LocalReducedMotion.current) 0 else 180), label = "Loading line")
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (visible || alpha > 0f) LinearProgressIndicator(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha })
        }
    } else AppReveal(visible) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
}

/** Only incoming content fades in; we never retain outgoing financial values during privacy changes. */
@Composable
internal fun AppFadeIn(contentKey: Any?, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val alpha = remember(contentKey) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(alpha, reduced) {
        if (reduced) alpha.snapTo(1f) else alpha.animateTo(1f, tween(ContentMotionMillis))
    }
    Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}

@Composable
internal fun LazyItemScope.AppMovingItem(content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    Box(Modifier.animateItem(fadeInSpec = if (reduced) null else tween(180),
        placementSpec = if (reduced) null else tween(ContentMotionMillis), fadeOutSpec = null)) { content() }
}
