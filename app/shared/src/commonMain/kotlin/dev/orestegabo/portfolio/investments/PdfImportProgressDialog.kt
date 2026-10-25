package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun PdfImportProgressDialog(progress: PdfImportProgress, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { AppText(when (progress.stage) {
            PdfImportStage.UPLOADING -> "Saving PDF"
            PdfImportStage.SAVING -> "Saving purchase"
            else -> "Reading PDF"
        }) },
        text = { Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText("${progress.stage.label}…")
                val detail = safePdfProgressDetail(progress.detail)
                if (detail.isNotBlank()) AppText(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } },
        confirmButton = { TextButton(onClick = cancel) { AppText("Cancel") } })
}
