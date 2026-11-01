package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun YearlyCouponsScreen(model: PortfolioModel, openBond: (JsonRecord) -> Unit) {
    val today = rememberBondCalendarToday()
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var currency by rememberSaveable { mutableStateOf("RWF") }
    var shown by rememberSaveable(year, currency) { mutableIntStateOf(12) }
    val scroll = rememberLazyListState()
    var previousYear by rememberSaveable { mutableIntStateOf(year) }
    var previousCurrency by rememberSaveable { mutableStateOf(currency) }
    LaunchedEffect(year, currency) {
        if (year != previousYear || currency != previousCurrency) scroll.scrollToItem(0)
        previousYear = year; previousCurrency = currency
    }
    var requested by remember(model.user?.text("id")) { mutableStateOf(false) }
    LaunchedEffect(model.busy, model.portfolioSyncing, model.historyComplete) {
        if (!model.historyComplete && !model.busy && !model.portfolioSyncing && !requested) {
            requested = true; model.loadInvestmentHistory()
        }
    }
    LaunchedEffect(Unit) { if (!model.couponTrackingAvailable && !model.notificationsLoading) model.refreshNotifications() }
    val purchases = model.purchases
    val receipts = model.couponReceipts
    val available = model.couponTrackingAvailable
    val complete = model.historyComplete
    val result by produceState<Pair<Int, YearCoupons>?>(null, purchases, receipts, year, today, available, complete) {
        value = null
        if (complete) value = year to withContext(Dispatchers.Default) { yearlyCoupons(purchases, receipts, year, today, available) }
    }
    val data = result?.takeIf { it.first == year }?.second
    val currencies = data?.rows?.map { it.currency }?.distinct()?.sorted().orEmpty()
    LaunchedEffect(currencies) { if (currencies.isNotEmpty() && currency !in currencies) currency = currencies.first() }
    val displayCurrency = currency.takeIf { it in currencies } ?: currencies.firstOrNull() ?: currency
    val rows = data?.rows?.filter { it.currency == displayCurrency }.orEmpty()
    val visible = rows.take(shown)
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "year") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }, enabled = year > 1900) { AppIcon(Icons.Outlined.ChevronLeft, "Previous year") }
                    AppText(year.toString(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    if (year != today.year) TextButton(onClick = { year = today.year }) { AppText("This year") }
                    IconButton(onClick = { year++ }, enabled = year < 9998) { AppIcon(Icons.Outlined.ChevronRight, "Next year") }
                }
                DetailNote("A year of coupon dates across your bonds. Tap a payment to open its bond.")
            }
        }
        when {
            !complete -> item {
                if (model.busy || model.portfolioSyncing || !requested) LoadingState()
                else EmptyState("Investment history unavailable", EmptyArtwork.BONDS, compact = true,
                    actionLabel = "Try again", onAction = { model.error = null; model.loadInvestmentHistory(force = true) })
            }
            data == null -> item { LoadingState() }
            else -> {
                if (currencies.size > 1) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        currencies.forEach { code -> FilterChip(selected = displayCurrency == code, onClick = { currency = code }, label = { AppText(code) }) }
                    }
                }
                if (rows.isNotEmpty()) item(key = "totals") {
                    val totals = yearlyCouponTotals(rows, displayCurrency, available)
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        DetailAmount(if (data.incomplete.isEmpty()) "Scheduled net coupons" else "Known scheduled net coupons", totals.scheduledNet, displayCurrency)
                        DetailColumns(
                            first = { DetailStatistic("Confirmed by you", totals.confirmed?.let { money(it, displayCurrency) } ?: "—") },
                            second = { DetailStatistic("Marked automatically", totals.automatic?.let { money(it, displayCurrency) } ?: "—") })
                        DetailNote("Automatic markings do not verify receipt. Scheduled coupons are expected income, not cash received.")
                        HorizontalDivider()
                    }
                }
                if (!available) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailNote(if (model.notificationsLoading) "Loading receipt status…" else "Receipt status unavailable")
                        TextButton(enabled = !model.notificationsLoading, onClick = model::refreshNotifications) { AppText("Refresh receipts") }
                    }
                }
                if (data.incomplete.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailHeading("Some coupon terms need attention")
                        DetailNote("Missing terms are not treated as zero. Open the bond to check its dates, coupon rate and tax.")
                        data.incomplete.forEach { bond -> TextButton(onClick = { openBond(bond) }, modifier = Modifier.fillMaxWidth()) {
                            FinancialText(bond.text("bondName"), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            AppIcon(Icons.Outlined.ChevronRight, null)
                        } }
                    }
                }
                if (rows.isEmpty()) item {
                    EmptyState("No coupons in this year", EmptyArtwork.CALENDAR, compact = true,
                        description = "Try another year. Pending purchases are not included.")
                }
                visible.groupBy { it.date.month }.forEach { (_, monthRows) ->
                    item(key = "month:${monthRows.first().date.month}") {
                        DetailHeading(monthRows.first().date.monthLabel())
                    }
                    items(monthRows, key = { it.key }) { coupon ->
                        val description = "${localized("View bond details")}: ${privateFinancialValue(coupon.purchase.text("bondName"))} · ${detailDate(coupon.date)}"
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = localized("View bond details")) { openBond(coupon.purchase) }
                            .semantics { contentDescription = description }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AppText(detailDate(coupon.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FinancialText(coupon.purchase.text("bondName"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                }
                                AppIcon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AppText(coupon.state.label, style = MaterialTheme.typography.labelMedium,
                                color = if (coupon.state == CouponReceiptState.CONFIRMED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            DetailValueRow("Expected net coupon", coupon.expectedNet?.let { money(it, displayCurrency) } ?: "—")
                            if (coupon.recordedAmount != null) DetailValueRow("Recorded receipt amount", money(coupon.recordedAmount, displayCurrency))
                            if (coupon.estimatedDate) DetailNote("Estimated coupon date")
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        }
                    }
                }
                if (rows.isNotEmpty()) item(key = "pagination") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        FinancialText(localized("{0} of {1} coupons").replace("{0}", visible.size.toString()).replace("{1}", rows.size.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (shown < rows.size) TextButton(onClick = { shown += 12 }, modifier = Modifier.fillMaxWidth()) { AppText("Load more coupons") }
                    }
                }
                item { DetailNote("Grouped by coupon due date, not bank settlement date. Schedules cover active and matured bonds; sold bonds show recorded entries only. Saved dates take precedence; other dates are estimates. Principal repayments are excluded.") }
            }
        }
    }
}
