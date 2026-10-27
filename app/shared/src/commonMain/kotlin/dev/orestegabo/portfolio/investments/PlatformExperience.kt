package dev.orestegabo.portfolio.investments

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Device preferences and feedback stay in the platform implementations. */
@Composable internal expect fun rememberReducedMotion(): Boolean
@Composable internal expect fun rememberSelectionFeedback(): () -> Unit
internal val LocalReducedMotion = staticCompositionLocalOf { false }
internal val LocalSelectionFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/** Keep normal Compose transitions, but remove decorative motion when the device requests it. */
@Composable
internal fun ColumnScope.AppAnimatedVisibility(
    visible: Boolean,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
) {
    if (LocalReducedMotion.current) {
        AnimatedVisibility(visible,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = androidx.compose.animation.ExitTransition.None, content = content)
    } else {
        AnimatedVisibility(visible, enter = fadeIn(tween(180)) + expandVertically(tween(240), expandFrom = Alignment.Top),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(240), shrinkTowards = Alignment.Top), content = content)
    }
}
