@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid

@Composable
fun CashLedgerScreen(model: PortfolioModel) {
    var kind by remember { mutableStateOf(if (model.ledgerHoldingId != null) "sale" else "deposit") }
    var holdingId by remember { mutableStateOf(model.ledgerHoldingId.orEmpty()) }
    var currency by remember { mutableStateOf("RWF") }
    var date by remember { mutableStateOf(Today.toString()) }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var id by remember { mutableStateOf(Uuid.random().toString()) }
    var pickingPurchase by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<JsonRecord?>(null) }
    var removing by remember { mutableStateOf<JsonRecord?>(null) }
    LaunchedEffect(model.user?.text("id"), model.isGuest) {
        if (!model.isGuest) model.action { model.reloadLedger() }
        if (!model.historyComplete && !model.busy) model.loadInvestmentHistory()
    }
    LaunchedEffect(model.busy, model.historyComplete) { if (!model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory() }
    val lots = remember(model.holdings) { model.holdings.filter { !it.text("id").startsWith("aggregate:") } }
    val displayedEntries = remember(model.ledgerEntries) { model.ledgerEntries.sortedWith(compareByDescending<JsonRecord> { it.text("date") }.thenByDescending { it.text("createdAt") }) }
    val cashSummaries = remember(model.portfolio, model.couponReceipts, model.ledgerEntries, Today) { recordedCash(model.portfolio, model.couponReceipts, model.ledgerEntries, Today) }
    val lot = lots.firstOrNull { it.text("id") == holdingId }
    LaunchedEffect(holdingId, kind) { if (kind in listOf("sale", "dividend")) lot?.let { currency = it.text("currency") } }
    val enabled = model.canEditInvestments && model.historyComplete && (model.isGuest || model.ledgerAvailable)
    if (pickingPurchase) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DetailHeading("Choose share purchase") }
            items(lots, key = { it.text("id") }) { purchase ->
                ListItem(headlineContent = { FinancialText(purchase.text("securityName").ifBlank { purchase.text("ticker") }) },
                    supportingContent = { FinancialText("${purchase.text("ticker")} · ${displayDate(purchase.text("tradeDate"))} · ${formatNumber(purchase.optDouble("remainingShares", purchase.optDouble("shares")))}") },
                    modifier = Modifier.clickable { holdingId = purchase.text("id"); currency = purchase.text("currency"); pickingPurchase = false })
            }
            item { TextButton(onClick = { pickingPurchase = false }) { AppText("Cancel") } }
        }
        return
    }
    if (pending != null) {
        val entry = checkNotNull(pending)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { DetailHeading("Confirm transaction") }
            item {
                AppText(displayDate(entry.text("date")))
                FinancialText(entry.text("ticker"))
                DetailAmount(if (entry.text("kind") == "sale") "Net sale proceeds" else "Amount", entry.optDouble("amount"), entry.text("currency"), prominent = false)
            }
            if (entry.text("kind") == "sale") item {
                DetailValueRow("Shares sold", formatNumber(entry.optDouble("shares")))
                DetailValueRow("Sale price per share", money(entry.optDouble("price"), currency))
                DetailValueRow("Selling fees", money(entry.optDouble("fees", 0.0), currency))
                DetailValueRow("Sale taxes", money(entry.optDouble("tax", 0.0), currency))
                DetailValueRow("Cost allocated to this sale", money(entry.optDouble("allocatedCost"), currency))
                DetailValueRow("Realized gain / loss", money(entry.optDouble("realizedGain"), currency))
                DetailNote("Purchase costs are allocated proportionally within the selected purchase. This is a portfolio record, not a tax calculation.")
            }
            item { Button(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                model.saveLedgerEntry(entry) { pending = null; amount = ""; quantity = ""; note = ""; id = Uuid.random().toString() }
            }) { AppText("Save transaction") } }
            item { TextButton(enabled = !model.busy && !model.guestStorageLoading, onClick = { pending = null }) { AppText("Cancel") } }
        }
        return
    }
    removing?.let { entry -> AlertDialog(onDismissRequest = { removing = null }, title = { AppText("Remove transaction?") },
        text = { AppText("This corrects your ledger and recalculates the balance. Original purchases stay saved.") },
        confirmButton = { TextButton(enabled = enabled, onClick = { model.deleteLedgerEntry(entry.text("id")); removing = null }) { AppText("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { AppText("Cancel") } }) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DetailNote("Recorded cash includes deposits, withdrawals, dividends, sale proceeds, purchase costs and manually confirmed coupons. Add your opening funding and missing transactions to reconcile it with your broker. Automatic coupon markings are excluded.") }
        if (!model.historyComplete) item { DetailNote("Loading complete purchase history…") }
        if (!model.isGuest && !model.ledgerAvailable) item { DetailNote(model.ledgerError ?: "Cash activity is currently unavailable. Your investments remain available.") }
        if (model.historyComplete && (model.isGuest || model.ledgerAvailable)) {
            items(cashSummaries, key = { "balance:${it.currency}" }) { cash ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailAmount("Recorded cash balance", cash.balance, cash.currency, prominent = false)
                        DetailValueRow("Dividends received", money(cash.dividends, cash.currency))
                        DetailValueRow("Realized gain / loss", money(cash.realizedGain, cash.currency))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailHeading("Add transaction")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("deposit" to "Deposit", "withdrawal" to "Withdrawal", "dividend" to "Dividend", "sale" to "Share sale").forEach { (value, title) ->
                        FilterChip(selected = kind == value, onClick = { kind = value; validation = null }, label = { AppText(title) })
                    }
                }
                if (kind in listOf("sale", "dividend")) {
                    DetailNote("Choose the purchase this transaction belongs to.")
                    lot?.let { FinancialText("${it.text("ticker")} · ${displayDate(it.text("tradeDate"))}") }
                    OutlinedButton(onClick = { pickingPurchase = true }) { AppText("Choose share purchase") }
                }
                Field("Date (YYYY-MM-DD)", date, { date = it })
                if (kind !in listOf("sale", "dividend")) Field("Currency", currency, { currency = it.uppercase().take(3) })
                if (kind == "sale") {
                    Field("Shares sold", quantity, { quantity = it }, KeyboardType.Decimal)
                    Field("Sale price per share", price, { price = it }, KeyboardType.Decimal)
                    Field("Selling fees", fees, { fees = it }, KeyboardType.Decimal)
                    Field("Sale taxes", tax, { tax = it }, KeyboardType.Decimal)
                } else Field(if (kind == "dividend") "Net dividend received" else "Amount", amount, { amount = it }, KeyboardType.Decimal)
                Field("Note (optional)", note, { note = it.take(500) })
                validation?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = enabled, onClick = {
                    try {
                        val raw = JsonRecord().put("kind", kind).put("date", date).put("currency", currency).put("note", note).put("holdingId", holdingId)
                        fun number(value: String) = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: error(localized("Enter valid transaction amounts."))
                        if (kind == "sale") raw.put("shares", number(quantity)).put("price", number(price)).put("fees", number(fees)).put("tax", number(tax))
                        else raw.put("amount", number(amount))
                        pending = validateLedgerEntry(raw, lots, model.ledgerEntries, Today).put("id", id)
                        validation = null
                    } catch (failure: Exception) { validation = failure.message ?: localized("Check the transaction details.") }
                }, modifier = Modifier.fillMaxWidth()) { AppText("Review transaction") }
            }
        }
        item { DetailHeading("Cash activity") }
        items(displayedEntries, key = { it.text("id") }) { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(when (entry.text("kind")) { "deposit" -> "Deposit"; "withdrawal" -> "Withdrawal"; "dividend" -> "Dividend"; else -> "Share sale" })
                AppText(displayDate(entry.text("date")))
                FinancialText(listOf(entry.text("ticker"), money(entry.optDouble("amount"), entry.text("currency"))).filter { it.isNotBlank() }.joinToString(" · "))
                if (entry.text("kind") == "sale") DetailValueRow("Realized gain / loss", money(entry.optDouble("realizedGain"), entry.text("currency")))
                if (entry.text("note").isNotBlank()) FinancialText(entry.text("note"))
                TextButton(enabled = enabled, onClick = { removing = entry }) { AppText("Remove transaction") }
                HorizontalDivider()
            }
        }
    }
}
