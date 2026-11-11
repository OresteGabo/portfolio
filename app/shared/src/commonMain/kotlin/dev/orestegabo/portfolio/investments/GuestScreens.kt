package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GuestEntryDialog(close: () -> Unit, choose: (GuestPath) -> Unit) {
    AlertDialog(onDismissRequest = close, title = { AppText("Continue as Guest") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppText("Choose how to begin. No account or upload is required.")
            OutlinedButton(onClick = { choose(GuestPath.SAMPLE) }, modifier = Modifier.fillMaxWidth()) { AppText("Explore fictional sample") }
            DetailNote("Illustrative examples only. Sample records are separate from your personal portfolio.")
            Button(onClick = { choose(GuestPath.PERSONAL) }, modifier = Modifier.fillMaxWidth()) { AppText("Start personal portfolio") }
            DetailNote("Investments and supported PDFs are saved privately on this device. Public closing quotes are optional and may be unavailable.")
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = close) { AppText("Cancel") } })
}

@Composable
internal fun GuestImportContent(model: PortfolioModel, engine: InvestmentEngine): Boolean {
    val sample = model.sampleGuest
    val importer = model.guestImport
    val actions = LocalNativeActions.current
    val local = model.guestPortfolio
    val incoming = NativePdfInbox.pending
    LaunchedEffect(incoming?.id, model.guestStorageLoading, importer?.review, importer?.previewBytes) {
        if (!sample && !model.guestStorageLoading && importer?.review == null && importer?.previewBytes == null && incoming != null && local != null) {
            NativePdfInbox.take(incoming.id)?.let { received ->
                model.selectGuestPdf(ReceivedPdfActions(actions, received.bytes, received.fileName), engine)
            }
        }
    }
    if (!sample && importer?.previewBytes != null) {
        PlatformBackHandler(true) { importer.closePreview() }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = importer::closePreview) { AppText("Back") }
            val preview = remember(importer.previewBytes, importer.previewName) { DocumentPreview(JsonRecord(), "Local PDF", importer.previewName, importer.previewBytes) }
            Box(Modifier.weight(1f)) { PdfViewerScreen(model, preview, retry = { importer.closePreview() }) }
        }
        return true
    }
    if (!sample && importer?.review != null) {
        val fields = remember(importer.review) { importer.fields() } ?: return true
        PlatformBackHandler(!model.guestStorageLoading) { model.cancelGuestImport() }
        Column(Modifier.fillMaxSize()) {
            GuestStorageNotice(model)
            AppText("Private on-device import", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(enabled = !model.guestStorageLoading, onClick = { model.previewGuestPdf() }) { AppText("View original PDF") }
                TextButton(enabled = !model.guestStorageLoading, onClick = model::cancelGuestImport) { AppText("Cancel") }
            }
            val records = if (importer.review?.text("kind") == "bond") local?.portfolio?.purchases.orEmpty() else local?.portfolio?.holdings.orEmpty()
            var choosingExisting by remember(importer.review) { mutableStateOf(false) }
            if (records.isNotEmpty()) TextButton(enabled = !model.guestStorageLoading, onClick = { choosingExisting = true }) {
                AppText(if (importer.targetId == null) "Link to an existing purchase (optional)" else "Existing purchase selected")
            }
            if (choosingExisting) AlertDialog(onDismissRequest = { choosingExisting = false }, title = { AppText("Choose existing purchase") },
                text = { LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(records, key = { it.text("id") }) { record ->
                        TextButton(onClick = { importer.targetId = record.text("id"); choosingExisting = false }) {
                            Column {
                                FinancialText(record.text("bondName").ifBlank { record.text("securityName") })
                                FinancialText(record.text("purchaseDate").ifBlank { record.text("tradeDate") }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { importer.targetId = null; choosingExisting = false }) { AppText("New purchase") } },
                dismissButton = { TextButton(onClick = { choosingExisting = false }) { AppText("Cancel") } })
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = importer.confirmed,
                enabled = !model.guestStorageLoading, role = Role.Checkbox, onValueChange = { importer.confirmed = it })) {
                Checkbox(checked = importer.confirmed, onCheckedChange = null, enabled = !model.guestStorageLoading)
                AppText("I checked these details against the original PDF.", Modifier.weight(1f).padding(top = 12.dp))
            }
            Box(Modifier.weight(1f)) {
                if (importer.review?.text("kind") == "bond") PurchaseEditor(model, fields, model::finishGuestImport, importer.review)
                else EquityEditor(model, fields, model::finishGuestImport, importer.review)
            }
        }
        return true
    }
    return false
}

internal class GuestUnavailableEngine : InvestmentEngine {
    override var onProgress: (String) -> Unit = {}
    override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("Guest imports are not available yet.")
    override fun cancel() {}
    override fun destroy() {}
}

@Composable
private fun GuestStorageNotice(model: PortfolioModel) {
    Column {
        if (model.guestStorageLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            model.guestImport?.progress?.let {
                AppText(it, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = model::cancelGuestImport) { AppText("Cancel") }
            }
        }

    }
}
