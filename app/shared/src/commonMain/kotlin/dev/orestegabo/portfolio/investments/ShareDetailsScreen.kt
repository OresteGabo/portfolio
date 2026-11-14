package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ShareDetailsScreen(model: PortfolioModel, selected: JsonRecord, openPurchase: (JsonRecord) -> Unit = {}) {
    val ticker = selected.text("ticker")
    val currency = selected.text("currency")
    val lots = shareDetailLots(model.holdings, selected)
    val valuation = model.portfolio.valuedShares(Today, model.quotes).firstOrNull {
        it.position.ticker == ticker && it.position.currency == currency
    }
    val quote = detailQuote(model.quotes, ticker, currency, Today)
    val name = model.quotes?.rows("quotes")?.firstOrNull { it.text("ticker")==ticker }?.text("name")?.takeIf { it.isNotBlank() }
        ?: lots.firstOrNull()?.text("securityName")?.ifBlank { null }
        ?: selected.text("securityName").ifBlank { ticker }
    val exchange = lots.firstOrNull()?.text("exchange").orEmpty()
    var requestedQuotes by remember(ticker, currency) { mutableStateOf(false) }
    var allLots by rememberSaveable(ticker, currency) { mutableStateOf(false) }
    LaunchedEffect(ticker, currency, model.historyComplete, model.busy) {
        if (!model.busy && model.error == null) {
            if (!model.historyComplete) model.loadInvestmentHistory()
            else if (model.quotes == null && !requestedQuotes) {
                requestedQuotes = true
                model.loadMarket()
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "company") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    InvestmentLogo(ticker = ticker, prominent = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FinancialText(listOf(ticker, exchange).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                FinancialText(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics { heading() })
                DetailAmount(when {
                    valuation == null -> "Closing price"
                    valuation.closingPrice == null -> "Purchase cost"
                    else -> "Market value"
                }, valuation?.estimatedValue ?: quote.price, currency)
                valuation?.let { ShareReturnSummary(it) }
            }
        }
        item(key = "quote") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppText(when {
                            quote.price == null -> "Market price unavailable"
                            quote.cached || model.quoteRefreshFailed -> "Cached closing price"
                            else -> "Official closing price"
                        }, style = MaterialTheme.typography.labelLarge)
                        quote.date?.takeIf { quote.price != null }?.let { DetailNote("${localized("Market date")} · ${detailDate(it)}") }
                        if (quote.price != null) DetailNote("Source: Rwanda Stock Exchange")
                        if (quote.cached || model.quoteRefreshFailed) DetailNote("This is a saved quote, not a live trading price.")
                    }
                    IconButton(enabled = !model.busy, onClick = { model.loadMarket(true) }) { AppIcon(Icons.Outlined.Refresh, "Refresh prices") }
                }
                DetailValueRow("Closing price", quote.price?.let { money(it, currency) } ?: localized("Unavailable"))
                if (quote.price == null) DetailNote("No usable market price. Purchase cost is not a market valuation.")
                HorizontalDivider()
            }
        }
        if (valuation != null) item(key = "position") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailHeading("Your position")
                DetailColumns(first = { DetailStatistic("Shares", formatNumber(valuation.position.shares)) },
                    second = { DetailStatistic("Acquisition cost", money(valuation.position.cashCost, currency)) })
                val pendingQuantity = model.portfolio.pendingShares().filter { it.text("ticker") == ticker && it.text("currency") == currency }
                    .sumOf { it.optDouble("shares", 0.0) }
                if (pendingQuantity > 0) DetailValueRow("Pending shares", formatNumber(pendingQuantity))
                DetailNote("Overall return is based on the combined acquisition cost, including purchase fees.")
                DetailNote("Includes recorded purchase fees. Excludes dividends, realized gains and future selling fees.")
                HorizontalDivider()
            }
        }
        item(key = "history-title") { DetailHeading("Purchase history", "Each purchase, its return and original evidence.") }
        if (!model.historyComplete) item(key = "history-state") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (model.busy) {
                    AppLoadingLine(true, reserveSpace = true)
                    DetailNote("Loading purchase history…")
                } else {
                    DetailNote("History unavailable")
                    TextButton(onClick = { model.loadInvestmentHistory(force = true) }) { AppText("Try again") }
                }
            }
        } else if (lots.isEmpty()) item(key = "no-purchases") {
            EmptyState("No purchases recorded for this share", EmptyArtwork.SHARES, compact = true)
        } else items(if (allLots) lots else lots.take(3), key = { "lot:${it.text("id")}" }) { lot -> SharePurchaseHistoryCard(lot, valuedSharePurchase(lot, Today, model.quotes)) { openPurchase(lot) } }
        if (model.historyComplete && lots.size > 3) item(key = "all-purchases") {
            TextButton(onClick = { allLots = !allLots }, modifier = Modifier.fillMaxWidth()) {
                AppText(if (allLots) "Show fewer purchases" else "Show all ${lots.size} purchases")
            }
        }
        item(key = "share-activity") {
            if (!model.sampleGuest) OutlinedButton(enabled = model.canEditInvestments, onClick = { model.openCashLedger(lots.firstOrNull()?.text("id")) }) { AppText("Record sale or dividend") }
            InvestmentActivityTimeline(model, lots)
        }
        item(key = "dividend-note") {
            DetailNote("No verified upcoming dividend is available here. Dividend income is not included in the displayed return.")
        }
    }
}

@Composable
private fun ShareReturnSummary(valuation: ValuedSharePosition, compact: Boolean = false) {
    valuation.gainLoss?.let { gain ->
        val color = when {
            gain < 0 -> MaterialTheme.colorScheme.error
            gain > 0 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val change = (if (gain > 0) "+" else "") + money(gain, valuation.position.currency)
        val rate = valuation.gainLossRate?.let { (if (it > 0) "+" else "") + percent(it) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FinancialText(listOfNotNull(change, rate).joinToString("  ·  "),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = color, fontWeight = FontWeight.Medium)
            DetailNote("Unrealized gain / loss")
        }
    }
}

@Composable
private fun SharePurchaseHistoryCard(lot: JsonRecord, valuation: ValuedSharePosition?, open: () -> Unit) {
    Surface(onClick = open, shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(calendarDate(lot.text("tradeDate"))?.let(::detailDate) ?: localized("Unavailable"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FinancialText("${formatNumber(lot.optDouble("shares"))} ${localized("Shares").lowercase()} · ${money(lot.optDouble("pricePerShare"), lot.text("currency"))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIcon(Icons.Outlined.ChevronRight, "View purchase", Modifier.size(22.dp))
            }
            DetailValueRow("Total cash cost", if (lot.has("netAmountPayable")) money(lot.optDouble("netAmountPayable"), lot.text("currency")) else "—")
            if (valuation?.gainLoss != null) ShareReturnSummary(valuation, compact = true)
            else DetailNote(if (lot.optDouble("remainingShares", lot.optDouble("shares")) <= 0) "No shares remaining" else "Return unavailable")
            if (lot.optDouble("soldShares", 0.0) > 0) DetailValueRow("Shares remaining", formatNumber(lot.optDouble("remainingShares")))
        }
    }
}

@Composable
fun SharePurchaseDetailsScreen(model: PortfolioModel, selected: JsonRecord,
                                       edit: (JsonRecord) -> Unit = {}, back: () -> Unit = {}) {
    val lot = model.holdings.firstOrNull { it.text("id") == selected.text("id") } ?: selected
    val valuation = valuedSharePurchase(lot, Today, model.quotes)
    val quote = detailQuote(model.quotes, lot.text("ticker"), lot.text("currency"), Today)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    InvestmentLogo(ticker = lot.text("ticker"), prominent = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FinancialText(lot.text("securityName").ifBlank { lot.text("ticker") },
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                        DetailNote(calendarDate(lot.text("tradeDate"))?.let(::detailDate) ?: localized("Unavailable"))
                    }
                }
                valuation?.let {
                    DetailAmount(if (it.closingPrice == null) "Purchase cost" else "Market value", it.estimatedValue, lot.text("currency"))
                    ShareReturnSummary(it)
                }
                if (valuation?.gainLoss == null) DetailNote(if (lot.optDouble("remainingShares", lot.optDouble("shares")) <= 0) "No shares remaining" else "Return unavailable")
                quote.price?.let { DetailValueRow("Closing price", money(it, lot.text("currency"))) }
                quote.date?.takeIf { quote.price != null }?.let { DetailNote("${localized("Market date")} · ${detailDate(it)}") }
                if (quote.cached || model.quoteRefreshFailed) DetailNote("This is a saved quote, not a live trading price.")
                DetailNote("Return uses this purchase’s acquisition cost, including fees, for the shares still held.")
                HorizontalDivider()
            }
        }
        item { SharePurchaseLedgerRow(model, lot, edit, back) }
        item { InvestmentActivityTimeline(model, listOf(lot)) }
    }
}

@Composable
private fun SharePurchaseLedgerRow(model: PortfolioModel, lot: JsonRecord, edit: (JsonRecord) -> Unit, back: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { AppText("Delete share purchase?") }, text = { AppText("This removes the local purchase. Saved PDFs stay in Documents.") }, confirmButton = { TextButton(enabled = model.canEditInvestments, onClick = { model.deleteGuestShare(lot.text("id")) { deleting = false; back() } }) { AppText("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = false }) { AppText("Cancel") } })
    val currency = lot.text("currency")
    var expanded by rememberSaveable(lot.text("id")) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailColumns(first = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(calendarDate(lot.text("tradeDate"))?.let(::detailDate) ?: localized("Unavailable"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                AppText(displayRecordValue(lot.text("status")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, second = {
            DetailStatistic("Total cash cost", if (lot.has("netAmountPayable")) money(lot.optDouble("netAmountPayable"), currency) else "—")
        })
        if (lot.optDouble("soldShares", 0.0) > 0 || lot.optDouble("remainingShares", lot.optDouble("shares")) < lot.optDouble("shares")) {
            DetailValueRow("Shares remaining", formatNumber(lot.optDouble("remainingShares")))
            DetailValueRow("Remaining acquisition cost", money(lot.optDouble("remainingCost"), currency))
        }
        if (lot.has("shares")) DetailValueRow("Shares", formatNumber(lot.optDouble("shares")))
        if (lot.has("pricePerShare")) DetailValueRow("Price per share", money(lot.optDouble("pricePerShare"), currency))
        if (lot.has("grossConsideration")) DetailValueRow("Gross consideration", money(lot.optDouble("grossConsideration"), currency))
        if (lot.has("totalCharges")) DetailValueRow("Purchase fees", money(lot.optDouble("totalCharges"), currency))
        DetailDocumentRows(model, lot)
        if (model.isGuest && !model.sampleGuest) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = model.canEditInvestments, onClick = { edit(lot) }) { AppText("Edit purchase") }
            TextButton(enabled = model.canEditInvestments, onClick = { deleting = true }) { AppText("Delete purchase", color = MaterialTheme.colorScheme.error) }
        }
        DetailDisclosure("Purchase breakdown", expanded) { expanded = !expanded }
        if (expanded) SharePurchaseFacts(lot)
        HorizontalDivider()
    }
}
