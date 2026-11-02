package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ShareDetailsScreen(model: PortfolioModel, selected: JsonRecord, edit: (JsonRecord) -> Unit = {}, back: () -> Unit = {}) {
    val ticker = selected.text("ticker")
    val currency = selected.text("currency")
    val lots = shareDetailLots(model.holdings, selected)
    val valuation = model.portfolio.valuedShares(Today, model.quotes).firstOrNull {
        it.position.ticker == ticker && it.position.currency == currency
    }
    val quote = detailQuote(model.quotes, ticker, currency, Today)
    val name = lots.firstOrNull()?.text("securityName")?.ifBlank { null }
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    InvestmentLogo(ticker = ticker)
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
                valuation?.gainLoss?.let { gain ->
                    val color = if (gain < 0) MaterialTheme.colorScheme.error else if (gain > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val change = (if (gain > 0) "+" else "") + money(gain, currency)
                        val rate = valuation.gainLossRate?.let { (if (it > 0) "+" else "") + percent(it) }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.4f) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FinancialText(change, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, color = color)
                                    rate?.let { FinancialText(it, style = MaterialTheme.typography.bodyLarge, color = color) }
                                }
                            } else FinancialText(listOfNotNull(change, rate).joinToString("  ·  "), Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, color = color)
                        }
                        DetailNote("Unrealized gain / loss")
                    }
                }
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
                            else -> "Latest available close"
                        }, style = MaterialTheme.typography.labelLarge)
                        quote.date?.takeIf { quote.price != null }?.let { DetailNote(detailDate(it)) }
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
                DetailColumns(first = { DetailStatistic("Shares held", formatNumber(valuation.position.shares)) },
                    second = { DetailStatistic("Acquisition cost", money(valuation.position.cashCost, currency)) })
                DetailNote("Includes recorded purchase fees. Excludes dividends, realized gains and future selling fees.")
                HorizontalDivider()
            }
        }
        item(key = "history-title") { DetailHeading("Purchase ledger", "Recorded trades, fees and original evidence.") }
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
        } else items(if (allLots) lots else lots.take(3), key = { "lot:${it.text("id")}" }) { lot -> SharePurchaseLedgerRow(model, lot, edit, back) }
        if (model.historyComplete && lots.size > 3) item(key = "all-purchases") {
            TextButton(onClick = { allLots = !allLots }, modifier = Modifier.fillMaxWidth()) {
                AppText(if (allLots) "Show fewer purchases" else "Show all ${lots.size} purchases")
            }
        }
        item(key = "dividend-note") {
            DetailNote("No verified upcoming dividend is available here. Dividend income is not included in the displayed return.")
        }
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
