package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A first-use guide on the wallpaper, deliberately separate from the message timeline. */
@Composable
internal fun ImportEmptyState(modifier: Modifier = Modifier) {
    val colours = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(88.dp).clearAndSetSemantics {}, shape = CircleShape,
            color = colours.primary.copy(alpha = .055f)) {
            Box(contentAlignment = Alignment.Center) { PdfCover(colours.primary) }
        }
        Spacer(Modifier.height(20.dp))
        AppText("Start with a PDF", Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        AppText("Attach a PDF below. Review its details, save it and follow its progress here.",
            Modifier.widthIn(max = 300.dp), style = MaterialTheme.typography.bodyMedium,
            color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        AppText("Bond confirmations · Share contracts\nIssuance reports · BK Capital statements",
            Modifier.widthIn(max = 300.dp), style = MaterialTheme.typography.labelSmall,
            color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
