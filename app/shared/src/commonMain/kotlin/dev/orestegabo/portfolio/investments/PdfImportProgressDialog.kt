package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
fun PdfImportProgressDialog(progress: PdfImportProgress, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { AppText(when (progress.stage) {
            PdfImportStage.UPLOADING -> "Saving PDF"
            PdfImportStage.SAVING -> "Saving purchase"
            else -> "Reading PDF"
        }) },
        text = { ProgressContent("${progress.stage.label}…", detail = safePdfProgressDetail(progress.detail),
            modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = cancel) { AppText("Cancel") } })
}
