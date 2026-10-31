package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PortfolioReportDialog(model: PortfolioModel, close: () -> Unit) {
    val actions = LocalNativeActions.current
    AlertDialog(onDismissRequest = close, title = { AppText("Portfolio report") },
        text = { AppText("Export all bonds, shares, estimated gains and losses, and target progress. The PDF includes your financial figures even when Hide numbers is on. Original PDFs must be downloaded separately.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { close(); model.exportPortfolioReport(actions) }) { AppText("Generate PDF") } },
        dismissButton = { TextButton(onClick = close) { AppText("Cancel") } })
}

@Composable
internal fun PortfolioReportProgressDialog(model: PortfolioModel) {
    model.reportProgress?.let { progress ->
        AlertDialog(onDismissRequest = {}, title = { AppText("Portfolio report") }, text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp)); AppText(progress, Modifier.weight(1f))
            }
        }, confirmButton = {})
    }
}
