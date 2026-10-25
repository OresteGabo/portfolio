package dev.orestegabo.portfolio.investments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
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
        AnimatedVisibility(visible, content = content)
    }
}
