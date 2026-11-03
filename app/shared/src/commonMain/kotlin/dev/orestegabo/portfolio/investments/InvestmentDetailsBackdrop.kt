package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Scene-level decoration spans the app bar and content without taking space or handling input. */
@Composable
fun InvestmentDetailsBackdrop(enabled: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().then(if (enabled) Modifier.clipToBounds()
        .background(MaterialTheme.colorScheme.background) else Modifier)) {
        if (enabled) {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            StructMark(Modifier.align(Alignment.TopEnd).offset(x = 84.dp, y = (-56).dp)
                .size(320.dp).alpha(if (dark) 0.07f else 0.09f).clearAndSetSemantics {}, onDark = dark)
        }
        content()
    }
}
