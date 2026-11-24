package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Compact toolbar entry; freshness and recovery details remain available on demand. */
@Composable
internal fun SavedPortfolioStatus(model: PortfolioModel, modifier: Modifier = Modifier) {
    if (!model.viewingSavedPortfolio) return
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val checking = model.verifyingSession || model.portfolioSyncing
    val label = localized("Saved portfolio")
    val state = localized(if (checking) "Checking connection…" else "Read-only · May be out of date")
    FilledTonalIconButton(onClick = { showDetails = true }, modifier = modifier.size(48.dp).semantics {
        contentDescription = label
        stateDescription = state
    }) {
        if (checking) InziraLoadingMark(active = true, modifier = Modifier.size(22.dp))
        else AppIcon(Icons.Outlined.CloudOff, null, Modifier.size(22.dp))
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        icon = { AppIcon(Icons.Outlined.CloudOff, null) },
        title = { AppText("Saved portfolio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText("Read-only · May be out of date", style = MaterialTheme.typography.bodyMedium)
                model.savedPortfolioAt?.take(10)?.let { date ->
                    AppText(localized("Saved {0}").replace("{0}", localized(displayDateValue(date))),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (checking) AppText("Checking connection…")
            }
        },
        confirmButton = {
            TextButton(enabled = !checking && !model.busy, onClick = {
                showDetails = false
                model.retryPortfolioSync()
            }) { AppText("Retry connection") }
        },
        dismissButton = { TextButton(onClick = { showDetails = false }) { AppText("Close") } },
    )
}
