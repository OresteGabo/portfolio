package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Today's manually confirmed payment is complete; an automatic marker is not bank evidence. */
internal fun nextDetailCoupon(purchase: JsonRecord, receipts: List<JsonRecord>, today: LocalDate): LocalDate? {
    if (purchase.text("status") != "active") return null
    val terms = purchase.terms() ?: return null
    return couponSchedule(terms).firstOrNull { due ->
        due >= today && !(due == today && receipts.any {
            it.text("purchaseId") == purchase.text("id") && it.text("dueDate") == due.toString() &&
                it.text("currency").ifBlank { purchase.text("currency") } == purchase.text("currency") &&
                it.optBoolean("received") && it.text("method") == "manual"
        })
    }
}

internal fun shareDetailLots(holdings: List<JsonRecord>, selected: JsonRecord): List<JsonRecord> = holdings.filter {
    it.text("ticker") == selected.text("ticker") && it.text("currency") == selected.text("currency")
}.sortedByDescending { it.text("tradeDate") }

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .055f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinancialText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailPair(first: Pair<String, String>, second: Pair<String, String>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 270.dp) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailMetric(first.first, first.second); DetailMetric(second.first, second.second)
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailMetric(first.first, first.second, Modifier.weight(1f))
            DetailMetric(second.first, second.second, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailTabs(options: List<String>, selected: String, choose: (String) -> Unit) {
    SecondaryScrollableTabRow(selectedTabIndex = options.indexOf(selected).coerceAtLeast(0), edgePadding = 0.dp,
        containerColor = Color.Transparent, divider = {}) {
        options.forEach { label -> Tab(selected = label == selected, onClick = { choose(label) },
            text = { AppText(label, fontWeight = if (selected == label) FontWeight.SemiBold else FontWeight.Normal) }) }
    }
}

@Composable
private fun DetailHero(title: String, subtitle: String, label: String, value: String, bond: Boolean, ticker: String = "",
    footer: @Composable ColumnScope.() -> Unit = {}) {
    val cream = Color(0xFFF2E6A7)
    Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF1C291F),
        border = BorderStroke(1.dp, cream.copy(alpha = .18f))) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF2C3A2C), Color(0xFF16221B))))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InvestmentLogo(bond = bond, ticker = ticker)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FinancialText(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFFFFFDF5),
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    FinancialText(subtitle, style = MaterialTheme.typography.labelSmall, color = cream.copy(alpha = .8f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(label, style = MaterialTheme.typography.labelLarge, color = cream)
                FinancialText(value, fontSize = 34.sp, color = Color(0xFFFFFDF5), fontWeight = FontWeight.Medium)
            }
            footer()
        }
    }
}

@Composable
private fun DetailDocuments(model: PortfolioModel, record: JsonRecord) {
    val actions = LocalNativeActions.current
    var downloading by remember { mutableStateOf<String?>(null) }
    val files = investmentEvidence(record, model.documents)
    if (files.isEmpty()) EmptyState("No linked document", EmptyArtwork.DOCUMENTS, compact = true)
    files.forEach { evidence ->
        val doc = evidence.document
        Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { AppIcon(Icons.Outlined.Description, null) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FinancialText(doc.text("label"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(enabled = !model.busy, onClick = { model.openDocumentPreview(doc) }, contentPadding = PaddingValues(0.dp)) { AppText("Open PDF") }
                }
                FilledTonalIconButton(enabled = !model.busy, onClick = { model.action {
                    downloading = doc.text("id")
                    try { if (shareInvestmentDocument(model.api, actions, doc)) model.message = "PDF ready to share." }
                    finally { downloading = null }
                } }, modifier = Modifier.size(48.dp)) {
                    if (downloading == doc.text("id")) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else AppIcon(Icons.Outlined.Download, "Download ${privateFinancialValue(doc.text("label"))}")
                }
            }
        }
    }
}

@Composable
fun BondDetailsScreen(model: PortfolioModel, purchase: JsonRecord, engine: InvestmentEngine,
    imported: (JsonRecord) -> Unit, back: () -> Unit, edit: () -> Unit) {
    val currency = purchase.text("currency")
    val today = Today
    val terms = purchase.terms()
    val tracking = terms?.let { trackBond(it, today) }
    val next = nextDetailCoupon(purchase, model.couponReceipts, today)
    val active = purchase.text("status") == "active"
    var confirmDelete by remember(purchase.text("id")) { mutableStateOf(false) }
    var section by rememberSaveable(purchase.text("id")) { mutableStateOf("Overview") }
    var scheduleSection by rememberSaveable(purchase.text("id")) { mutableStateOf("Upcoming") }
    LaunchedEffect(purchase.text("id")) {
        if (!model.couponTrackingAvailable && !model.notificationsLoading) model.refreshNotifications()
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { AppText("Delete purchase?") },
        text = { AppText("This removes ${purchase.text("bondName")} from your synchronized portfolio.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = {
            model.deletePurchase(purchase.text("id"), back); confirmDelete = false
        }) { AppText("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { AppText("Keep purchase") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { DetailHero(purchase.text("bondName"), purchase.text("isin"),
            if (purchase.text("status") == "submitted") "Amount applied" else "Bond principal",
            money(purchase.optDouble("faceValue"), currency), bond = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .1f)) {
                        AppText(displayRecordValue(purchase.text("status")), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color(0xFFF2E6A7), style = MaterialTheme.typography.labelMedium)
                    }
                    if (terms != null && purchase.text("status") != "submitted") Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .1f)) {
                        FinancialText(percent(terms.couponRate) + " · " + localized("Coupon rate"), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color(0xFFF2E6A7), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } }
        item { DetailTabs(listOf("Overview", "Payments", "Documents", "Details"), section) { section = it } }
        if (section == "Overview") {
        item { DetailSection("Next coupon") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { AppIcon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f)) {
                    FinancialText(next?.toString() ?: localized(when {
                        purchase.text("status") == "submitted" -> "Awaiting BNR confirmation"
                        !active -> "No upcoming payments for this holding"
                        terms == null -> "Coupon terms unavailable"
                        tracking?.schedule.isNullOrEmpty() && terms.maturityDate > today -> "Coupon dates unavailable"
                        else -> "No remaining coupons"
                    }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (next != null) AppText(when (today.daysUntil(next)) { 0 -> "Due today"; 1 -> "Tomorrow"; else -> "In ${today.daysUntil(next)} days" },
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (next != null && terms != null) {
                HorizontalDivider()
                AppText("Expected net coupon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FinancialText(money(terms.netCouponPayment(), currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Fact("Gross per payment", money(terms.grossCouponPayment(), currency))
                Fact("Withholding tax", percent(terms.taxRate))
            }
            AppText("Scheduled income is not confirmation of payment. Dates and amounts follow your saved terms.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Fact("Schedule", purchase.text("scheduleConfidence").ifBlank { localized("Estimated") })
        } }
        if (terms != null && tracking != null && purchase.text("status") != "submitted") item { DetailSection("Maturity timeline") {
            Fact("Maturity", terms.maturityDate.toString())
            LinearProgressIndicator(progress = { tracking.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
            AppText(when { terms.maturityDate <= today -> "Maturity reached"; today.daysUntil(terms.maturityDate) == 1 -> "1 day to maturity"; else -> "${today.daysUntil(terms.maturityDate)} days to maturity" },
                style = MaterialTheme.typography.bodySmall)
            DetailPair("Coupon rate" to percent(terms.couponRate), "Principal at maturity" to money(terms.faceValue, currency))
            AppText("Coupon rate is not yield to maturity. Redemption has not been verified by this timeline.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (purchase.text("status") != "submitted") item { DetailSection("Income tracking") {
            val receipts = couponReceiptTotals(purchase, model.couponReceipts, today)
            DetailPair("Confirmed by you" to if (model.couponTrackingAvailable) money(receipts.confirmedByUser, currency) else "—",
                "Marked automatically" to if (model.couponTrackingAvailable) money(receipts.markedAutomatically, currency) else "—")
            if (tracking != null) {
                Fact("Scheduled to date", money(tracking.scheduledCouponsToDate, currency))
                if (active) {
                    Fact("Accrued coupon", money(tracking.accruedCoupon, currency))
                    Fact("Remaining coupons", money(tracking.remainingCoupons, currency))
                }
            }
            AppText("Automatic markings follow your settings; they do not verify payment into your account.", style = MaterialTheme.typography.bodySmall)
            if (!model.couponTrackingAvailable) {
                AppText(if (model.notificationsLoading) "Loading receipt status…" else "Receipt status unavailable")
                TextButton(enabled = !model.notificationsLoading, onClick = model::refreshNotifications) { AppText("Refresh receipts") }
            }
        } }
        }
        if (section == "Documents") item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailDocuments(model, purchase)
            if (needsPersonalConfirmation(purchase)) {
                PendingConfirmationAction(purchase, !model.busy) { model.confirmPendingPurchase(purchase) }
                PdfImportButton(model, engine, imported, bondOnly = true, targetPurchaseId = purchase.text("id"))
            }
        } }
        if (section == "Details") item { DetailSection("Terms and purchase") {
            if (purchase.text("status") == "submitted") PendingPurchaseFacts(purchase) else BondPurchaseFacts(purchase)
        } }
        if (section == "Payments" && (tracking == null || purchase.text("status") == "submitted")) item {
            EmptyState(if (purchase.text("status") == "submitted") "Awaiting BNR confirmation" else "Coupon dates unavailable", EmptyArtwork.BONDS, compact = true)
        }
        if (section == "Payments" && tracking != null && purchase.text("status") != "submitted") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceRow(listOf("Upcoming", "Past"), scheduleSection) { scheduleSection = it }
                }
            }
            val dates = if (scheduleSection == "Upcoming") tracking.schedule.filter { it >= today } else tracking.schedule.filter { it < today }.reversed()
            if (dates.isEmpty()) item { AppText("No payments in this period", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(dates, key = { it.toString() }) { date ->
                val receipt = model.couponReceipt(purchase.text("id"), date.toString())
                val state = couponReceiptState(date, today, model.couponTrackingAvailable, receipt)
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { AppIcon(Icons.Outlined.Payments, null) }
                        }
                        Column(Modifier.weight(1f)) {
                            FinancialText(date.toString(), fontWeight = FontWeight.SemiBold)
                            AppText(state.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Fact("Expected net coupon", money(tracking.netPerPayment, currency))
                    recordedCouponAmount(receipt, currency)?.let { Fact("Recorded receipt amount", money(it, currency)) }
                    if (date <= today && model.couponTrackingAvailable) {
                        TextButton(enabled = !model.busy, onClick = {
                            model.confirmCoupon(purchase.text("id"), date.toString(), receipt?.optBoolean("received") != true)
                        }) { AppText(if (receipt?.optBoolean("received") == true) "Mark not received" else "Confirm received") }
                        if (state == CouponReceiptState.AUTOMATIC) TextButton(enabled = !model.busy, onClick = {
                            model.confirmCoupon(purchase.text("id"), date.toString(), true)
                        }) { AppText("Confirm received") }
                    }
                }
            }
        }
        if (section == "Details") item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (purchase.text("status") != "submitted") OutlinedButton(onClick = edit, enabled = !model.busy) { AppText("Edit purchase") }
            TextButton(onClick = { confirmDelete = true }, enabled = !model.busy) { AppText("Delete", color = MaterialTheme.colorScheme.error) }
        } }
    }
}

@Composable
fun ShareDetailsScreen(model: PortfolioModel, selected: JsonRecord) {
    val ticker = selected.text("ticker")
    val currency = selected.text("currency")
    val lots = shareDetailLots(model.holdings, selected)
    val valuation = model.portfolio.valuedShares(Today, model.quotes).firstOrNull {
        it.position.ticker == ticker && it.position.currency == currency
    }
    val name = lots.firstOrNull()?.text("securityName")?.ifBlank { null }
        ?: selected.text("securityName").ifBlank { ticker }
    val quote = model.quotes?.rows("quotes")?.firstOrNull { it.text("ticker") == ticker }
        ?.optDouble("closing", Double.NaN)?.takeIf { currency == "RWF" && it.isFinite() && it > 0 &&
            calendarDate(model.quotes?.optJSONObject("source")?.text("publishedAt").orEmpty().take(10))?.let { it <= Today } == true }
    var section by rememberSaveable(ticker, currency) { mutableStateOf("Overview") }
    var requestedQuotes by remember(ticker, currency) { mutableStateOf(false) }
    LaunchedEffect(ticker, currency, model.historyComplete, model.busy) {
        if (!model.busy && model.error == null) {
            if (!model.historyComplete) model.loadInvestmentHistory()
            else if (model.quotes == null && !requestedQuotes) {
                requestedQuotes = true
                model.loadMarket()
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DetailHero(name, "$ticker · $currency", when {
            valuation == null -> "Closing price"
            valuation.closingPrice == null -> "Purchase cost"
            else -> "Market value"
        }, when {
            valuation != null -> money(valuation.estimatedValue, currency)
            quote != null -> money(quote, currency)
            else -> "—"
        }, bond = false, ticker = ticker) {
            valuation?.gainLoss?.let { gain ->
                val returnColor = if (gain >= 0) Color(0xFFB8E2B2) else Color(0xFFFFB4AB)
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(if (gain >= 0) Icons.Outlined.NorthEast else Icons.Outlined.SouthEast, null, Modifier.size(16.dp), tint = returnColor)
                        FinancialText((if (gain > 0) "+" else "") + money(gain, currency), style = MaterialTheme.typography.labelLarge, color = returnColor)
                        valuation.gainLossRate?.let { FinancialText(percent(it), color = Color(0xFFF2E6A7), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                AppText("Unrealized gain / loss", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2E6A7))
            }
        } }
        item { DetailTabs(listOf("Overview", "Purchase history", "Documents"), section) { section = it } }
        if (section == "Overview") {
        if (valuation != null) item { DetailSection("Your position") {
            DetailPair("Shares" to formatNumber(valuation.position.shares), "Total cash cost" to money(valuation.position.cashCost, currency))
            AppText("Includes recorded purchase fees. Excludes dividends, realized gains and future selling fees.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { DetailSection("Price information") {
            Fact("Closing price", quote?.let { money(it, currency) } ?: localized("Unavailable"))
            Fact("Quote date", model.quotes?.optJSONObject("source")?.text("publishedAt")?.take(10)?.ifBlank { "—" } ?: "—")
            if (model.quotes?.optJSONObject("source")?.optBoolean("stale") == true) AppText("Stale prices", color = MaterialTheme.colorScheme.error)
            if (quote == null) AppText("No usable market price. Purchase cost is not a market valuation.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !model.busy, onClick = { model.loadMarket(true) }) { AppText("Refresh prices") }
        } }
        item {
            AppText("No verified upcoming dividend is available here. Dividend income is not included in the displayed return.",
                modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        if (section != "Overview") {
        if (!model.historyComplete) item { Panel {
            if (model.busy) LoadingState() else {
                AppText("History unavailable")
                TextButton(onClick = { model.loadInvestmentHistory(force = true) }) { AppText("Try again") }
            }
        } } else if (lots.isEmpty()) item {
            EmptyState("No purchases recorded for this share", EmptyArtwork.SHARES, compact = true)
        } else items(lots, key = { it.text("id") }) { lot -> DetailSection(lot.text("tradeDate")) {
            if (section == "Documents") DetailDocuments(model, lot) else SharePurchaseFacts(lot)
        } }
        }
    }
}
