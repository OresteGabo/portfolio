package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
            DetailNote("Investments are saved privately on this device. PDF import and live markets are not available in Guest Mode yet.")
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = close) { AppText("Cancel") } })
}

@Composable
internal fun GuestContent(model: PortfolioModel, tab: String, choosePath: () -> Unit, signIn: () -> Unit) {
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
        title = { AppText("Delete local investment?") }, text = { AppText("This removes the investment and its receipt states from this device. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !model.guestStorageLoading, onClick = {
            if (bond) model.deletePurchase(id) { deleting = null } else model.deleteGuestShare(id) { deleting = null }
        }) { AppText("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { AppText("Cancel") } }) }
    val local = model.guestPortfolio
    val snapshot = remember { guestSamplePortfolio() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(if (sample) "Guest · Fictional sample" else "Guest · Personal workspace", fontWeight = FontWeight.SemiBold)
                    AppText(if (sample) "These examples are fictional. They are not your investments or live market values."
                        else "Your investments stay on this device. They are not synced or backed up. PDF import and live markets are not available yet.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = !model.guestStorageLoading, onClick = signIn) { AppText("Sign in to your account") }
                }
            }
        }
        when (tab) {
            "Portfolio" -> {
                if (!sample) {
                    item { GuestStorageNotice(model) }
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
        if (model.guestStorageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
