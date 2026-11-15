package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import dev.orestegabo.portfolio.design.InziraDimensions
import dev.orestegabo.portfolio.design.InziraSpacing

/** Shared modal progress presentation; admission, safe detail text and cancellation stay with callers. */
@Composable
fun ProgressContent(label: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InziraSpacing.large)) {
        CircularProgressIndicator(Modifier.size(InziraDimensions.progressIndicator).clearAndSetSemantics {},
            strokeWidth = InziraDimensions.progressStroke)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InziraSpacing.tight)) {
            AppText(label, style = MaterialTheme.typography.bodyMedium)
            if (!detail.isNullOrBlank()) AppText(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
