package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Re-created fictional examples, never model.portfolio, persisted holdings or a backend account. */
internal fun guestSamplePortfolio() = PortfolioSnapshot(
    purchases = listOf(JsonRecord().put("id", "sample:bond").put("bondName", "Example bond — fictional")
        .put("currency", "RWF").put("status", "active").put("purchaseDate", "2020-01-01")
        .put("faceValue", 1000000).put("pricePercent", 100).put("feesPaid", 0).put("accruedInterestPaid", 0)),
    holdings = listOf(JsonRecord().put("id", "sample:shares").put("securityName", "Example company — fictional")
        .put("ticker", "DEMO").put("currency", "RWF").put("status", "active").put("tradeDate", "2020-01-01")
        .put("shares", 100).put("netAmountPayable", 50000)))

@Composable
internal fun GuestEntryDialog(close: () -> Unit, choose: (GuestPath) -> Unit) {
    AlertDialog(onDismissRequest = close, title = { AppText("Continue as Guest") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppText("Choose how to begin. No account or upload is required.")
            OutlinedButton(onClick = { choose(GuestPath.SAMPLE) }, modifier = Modifier.fillMaxWidth()) { AppText("Explore fictional sample") }
            DetailNote("Illustrative examples only. Sample records are separate from your personal portfolio.")
            Button(onClick = { choose(GuestPath.PERSONAL) }, modifier = Modifier.fillMaxWidth()) { AppText("Start personal portfolio") }
            DetailNote("Investments and supported PDFs are saved privately on this device. Live markets are not available in Guest Mode yet.")
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = close) { AppText("Cancel") } })
}

@Composable
internal fun GuestContent(model: PortfolioModel, tab: String, engine: InvestmentEngine, choosePath: () -> Unit, signIn: () -> Unit) {
    val sample = model.guestPath == GuestPath.SAMPLE
    var editingBond by remember(model.sessionState) { mutableStateOf<JsonRecord?>(null) }
    var editingShare by remember(model.sessionState) { mutableStateOf<JsonRecord?>(null) }
    var deleting by remember(model.sessionState) { mutableStateOf<Pair<String, Boolean>?>(null) }
    if (!sample && editingBond != null) {
        GuestStorageNotice(model)
        PurchaseEditor(model, requireNotNull(editingBond), { editingBond = null })
        return
    }
    if (!sample && editingShare != null) {
        GuestStorageNotice(model)
        EquityEditor(model, requireNotNull(editingShare), { editingShare = null })
        return
    }
    deleting?.let { (id, bond) -> AlertDialog(onDismissRequest = { deleting = null },
        title = { AppText("Delete local investment?") }, text = { AppText("This removes the investment and its receipt states. Saved PDFs stay in Documents until you delete them separately.") },
        confirmButton = { TextButton(enabled = !model.guestStorageLoading, onClick = {
            if (bond) model.deletePurchase(id) { deleting = null } else model.deleteGuestShare(id) { deleting = null }
        }) { AppText("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { AppText("Cancel") } }) }
    val importer = model.guestImport
    val actions = LocalNativeActions.current
    val local = model.guestPortfolio
    val incoming = NativePdfInbox.pending
    LaunchedEffect(incoming?.id, model.guestStorageLoading, importer?.review, importer?.previewBytes) {
        if (!sample && !model.guestStorageLoading && importer?.review == null && importer?.previewBytes == null && editingBond == null && editingShare == null && incoming != null && local != null) {
            NativePdfInbox.take(incoming.id)?.let { received ->
                model.selectGuestPdf(ReceivedPdfActions(actions, received.bytes, received.fileName), engine)
            }
        }
    }
    var deletingDocument by remember(model.sessionState) { mutableStateOf<String?>(null) }
    deletingDocument?.let { id -> AlertDialog(onDismissRequest = { deletingDocument = null },
        title = { AppText("Delete local PDF?") }, text = { AppText("Delete the original PDF from this device? Investment records and coupon receipts will remain. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !model.guestStorageLoading, onClick = { model.deleteGuestDocument(id); deletingDocument = null }) { AppText("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deletingDocument = null }) { AppText("Cancel") } }) }
    if (!sample && importer?.previewBytes != null) {
        PlatformBackHandler(true) { importer.closePreview() }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = importer::closePreview) { AppText("Back") }
            val preview = remember(importer.previewBytes, importer.previewName) { DocumentPreview(JsonRecord(), "Local PDF", importer.previewName, importer.previewBytes) }
            Box(Modifier.weight(1f)) { PdfViewerScreen(model, preview, retry = { importer.closePreview() }) }
        }
        return
    }
    if (!sample && importer?.review != null) {
        val fields = remember(importer.review) { importer.fields() } ?: return
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
        return
    }
    val snapshot = remember { guestSamplePortfolio() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(if (sample) "Guest · Fictional sample" else "Guest · Personal workspace", fontWeight = FontWeight.SemiBold)
                    AppText(if (sample) "These examples are fictional. They are not your investments or live market values."
                        else "Your investments and PDFs stay on this device. They are not synced or backed up. Live markets are not available yet.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = !model.guestStorageLoading, onClick = signIn) { AppText("Sign in to your account") }
                }
            }
        }
        when (tab) {
            "Portfolio" -> {
                if (!sample) {
                    importer?.duplicate?.let { doc -> item { DetailHeading("This PDF and its investment are already saved."); DetailValueRow("First added", doc.text("createdAt").take(10)) } }
                    item { GuestStorageNotice(model) }
                    item { importer?.failure?.let { AppText(it, color = MaterialTheme.colorScheme.error) } }
                    item { Button(enabled = !model.guestStorageLoading, onClick = { model.selectGuestPdf(actions, engine) }, modifier = Modifier.fillMaxWidth()) { AppText("Import PDF privately") } }
                    if (local != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !model.guestStorageLoading, onClick = { editingBond = JsonRecord() }, modifier = Modifier.fillMaxWidth()) { AppText("Add bond purchase") }
                                OutlinedButton(enabled = !model.guestStorageLoading, onClick = { editingShare = JsonRecord() }, modifier = Modifier.fillMaxWidth()) { AppText("Add share purchase") }
                            }
                        }
                        if (local.portfolio.purchases.isEmpty() && local.portfolio.holdings.isEmpty()) item {
                            EmptyState("Your personal portfolio starts here", EmptyArtwork.PORTFOLIO, compact = true,
                                description = "Add a bond or share purchase. Your records are saved privately on this device.")
                        }
                        local.portfolio.summary(Today).forEach { summary -> item {
                            DetailHeading(summary.currency)
                            DetailValueRow("Principal", money(summary.bondFaceValue, summary.currency))
                            DetailValueRow("Acquisition cost", money(summary.investedCash, summary.currency))
                            DetailNote("Acquisition amounts only. No live market valuation.")
                        } }
                        local.portfolio.purchases.forEach { bond -> item(key = bond.text("id")) {
                            DetailHeading(bond.text("bondName"), bond.text("isin"))
                            DetailValueRow("Principal", money(bond.optDouble("faceValue"), bond.text("currency")))
                            GuestBondReceipt(model, bond, local.receipts)
                            GuestRecordActions(model, edit = { editingBond = bond }, delete = { deleting = bond.text("id") to true })
                            HorizontalDivider()
                        } }
                        local.portfolio.holdings.forEach { share -> item(key = share.text("id")) {
                            DetailHeading(share.text("securityName"), share.text("ticker"))
                            DetailValueRow("Shares", share.text("shares"))
                            DetailValueRow("Acquisition cost", money(share.optDouble("netAmountPayable"), share.text("currency")))
                            DetailValueRow("Purchase fees", money(share.optDouble("totalCharges"), share.text("currency")))
                            GuestRecordActions(model, edit = { editingShare = share }, delete = { deleting = share.text("id") to false })
                            HorizontalDivider()
                        } }
                    }
                }
                if (sample) {
                    item { DetailHeading("Sample portfolio", "Fictional examples · acquisition amounts only") }
                    item {
                        DetailAmount("Sample bond principal", snapshot.summary(Today).single().bondFaceValue, "RWF")
                    }
                    item {
                        DetailHeading("Example bond — fictional")
                        DetailValueRow("Principal", money(1000000.0, "RWF"))
                        HorizontalDivider()
                    }
                    item {
                        DetailHeading("Example company — fictional")
                        DetailValueRow("Shares", "100")
                        DetailValueRow("Acquisition cost", money(50000.0, "RWF"))
                        DetailNote("No live valuation or verified income is shown for sample records.")
                    }
                }
                item { OutlinedButton(enabled = !model.guestStorageLoading, onClick = choosePath, modifier = Modifier.fillMaxWidth()) { AppText("Change guest experience") } }
            }
            "Documents" -> if (sample) item {
                EmptyState("Switch to your personal workspace to import PDFs.", EmptyArtwork.DOCUMENTS, compact = true)
            } else {
                item { GuestStorageNotice(model); importer?.failure?.let { AppText(it, color = MaterialTheme.colorScheme.error) } }
                item { Button(enabled = !model.guestStorageLoading, onClick = { model.selectGuestPdf(actions, engine) }, modifier = Modifier.fillMaxWidth()) { AppText("Import PDF privately") } }
                importer?.duplicate?.let { doc -> item {
                    DetailHeading("This PDF and its investment are already saved.")
                    DetailValueRow("First added", doc.text("createdAt").take(10))
                } }
                if (local?.portfolio?.documents?.isEmpty() == true) item { EmptyState("No local PDFs yet", EmptyArtwork.DOCUMENTS, compact = true) }
                local?.portfolio?.documents.orEmpty().forEach { doc -> item(key = doc.text("id")) {
                    DetailHeading(doc.text("fileName"))
                    DetailValueRow("First added", doc.text("createdAt").take(10))
                    Row {
                        TextButton(enabled = !model.guestStorageLoading, onClick = { model.previewGuestPdf(doc.text("id"), doc.text("fileName")) }) { AppText("View original PDF") }
                        TextButton(enabled = !model.guestStorageLoading, onClick = { deletingDocument = doc.text("id") }) { AppText("Delete PDF", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                } }
            }
            "Settings" -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailHeading("Guest settings")
                    DetailNote("Changing guest experience never transfers sample records or uploads a personal portfolio.")
                    OutlinedButton(enabled = !model.guestStorageLoading, onClick = choosePath, modifier = Modifier.fillMaxWidth()) { AppText("Change guest experience") }
                    TextButton(enabled = !model.guestStorageLoading, onClick = signIn) { AppText("Sign in to your account") }
                }
            }
            else -> item {
                EmptyState(when(tab) { "Documents" -> "Guest PDF import is not available yet"; "Calendar" -> "Guest payment calendar is not available yet"; else -> "Guest live markets are not available yet" },
                    EmptyArtwork.PORTFOLIO, compact = true,
                    description = "This feature is not available in Guest Mode yet. Sign in to use account features.")
            }
        }
    }
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
        if (model.guestStorageError != null) {
            AppText(requireNotNull(model.guestStorageError), color = MaterialTheme.colorScheme.error)
            TextButton(enabled = !model.guestStorageLoading, onClick = model::loadGuestPortfolio) { AppText("Try again") }
        }
    }
}
@Composable
private fun GuestRecordActions(model: PortfolioModel, edit: () -> Unit, delete: () -> Unit) {
    Row {
        TextButton(enabled = !model.guestStorageLoading, onClick = edit) { AppText("Edit") }
        TextButton(enabled = !model.guestStorageLoading, onClick = delete) { AppText("Delete", color = MaterialTheme.colorScheme.error) }
    }
}
@Composable
private fun GuestBondReceipt(model: PortfolioModel, bond: JsonRecord, receipts: List<JsonRecord>) {
    val terms = bond.terms() ?: return
    val schedule = couponSchedule(terms)
    val next = schedule.firstOrNull { it > Today }
    if (next != null) DetailValueRow("Next coupon", detailDate(next))
    if (bond.text("status") !in listOf("active", "matured")) return
    val due = schedule.lastOrNull { it <= Today } ?: return
    val receipt = receipts.firstOrNull { it.text("purchaseId") == bond.text("id") && it.text("dueDate") == due.toString() }
    DetailValueRow("Latest due coupon", detailDate(due))
    DetailValueRow("Expected net payment", money(terms.netCouponPayment(), bond.text("currency")))
    AppText(couponReceiptState(due, Today, true, receipt).label, style = MaterialTheme.typography.bodySmall)
    recordedCouponAmount(receipt, bond.text("currency"))?.let { amount ->
        DetailValueRow("Recorded received amount", money(amount, bond.text("currency")))
    }
    Row {
        TextButton(enabled = !model.guestStorageLoading, onClick = { model.confirmCoupon(bond.text("id"), due.toString(), true) }) { AppText("Mark received") }
        TextButton(enabled = !model.guestStorageLoading, onClick = { model.confirmCoupon(bond.text("id"), due.toString(), false) }) { AppText("Mark not received") }
    }
}
