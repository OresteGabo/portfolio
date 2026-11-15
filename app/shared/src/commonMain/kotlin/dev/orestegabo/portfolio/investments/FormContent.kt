package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.orestegabo.portfolio.design.InziraDimensions

/** Centers a readable form on wide windows; callers own scrolling, padding and keyboard insets. */
@Composable
fun FormContent(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        content(Modifier.widthIn(max = InziraDimensions.formMaxWidth).fillMaxSize())
    }
}
