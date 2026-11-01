package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CouponYearPage(val year: Int, val data: YearCoupons, val next: NextCouponYear?)

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
    val result by produceState<CouponYearPage?>(null, purchases, receipts, year, today, available, complete) {
        value = null
        if (complete) {
            val selectedYear = year
            value = withContext(Dispatchers.Default) {
                val selected = yearlyCoupons(purchases, receipts, selectedYear, today, available)
                CouponYearPage(selectedYear, selected, if (selected.rows.isEmpty()) nextCouponYear(purchases, receipts, selectedYear, today, available) else null)
            }
        }
    }
    val page = result?.takeIf { it.year == year }
    val data = page?.data
    val currencies = data?.rows?.map { it.currency }?.distinct()?.sorted().orEmpty()
    LaunchedEffect(currencies) { if (currencies.isNotEmpty() && currency !in currencies) currency = currencies.first() }
    val displayCurrency = currency.takeIf { it in currencies } ?: currencies.firstOrNull() ?: currency
    val rows = data?.rows?.filter { it.currency == displayCurrency }.orEmpty()
    val visible = rows.take(shown)
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "year") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }, enabled = year > 1900) { AppIcon(Icons.Outlined.ChevronLeft, "Previous year") }
                    AppText(year.toString(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    if (year != today.year) TextButton(onClick = { year = today.year }) { AppText("This year") }
                    IconButton(onClick = { year++ }, enabled = year < 9998) { AppIcon(Icons.Outlined.ChevronRight, "Next year") }
                }
                DetailNote(localized("Coupons due in {0}. Tap a payment to open its bond.").replace("{0}", year.toString()))
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
                if (rows.isEmpty()) item(key = "empty-year") {
                    CouponYearEmptyState(year, page.next)
                }
                if (rows.isEmpty()) page.next?.let { next -> item(key = "next-preview") {
                    NextCouponYearPreview(next) { year = next.year }
                } }
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
                if (year < 9998 && (rows.isNotEmpty() || page.next == null)) item(key = "next-year") {
                    FilledTonalButton(onClick = { year++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        AppText("${localized("Next year")} · ${year + 1}", Modifier.weight(1f))
                        AppIcon(Icons.Outlined.ChevronRight, null)
                    }
                }
                item { DetailNote("Grouped by coupon due date, not bank settlement date. Schedules cover active and matured bonds; sold bonds show recorded entries only. Saved dates take precedence; other dates are estimates. Principal repayments are excluded.") }
            }
        }
    }
    if (data != null && rows.isEmpty()) page.next?.let { next ->
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Button(onClick = { year = next.year }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp)) {
                    AppText(localized("View {0} coupons").replace("{0}", next.year.toString()), Modifier.weight(1f))
                    AppIcon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
    }
    }
}

@Composable
private fun NextCouponYearPreview(next: NextCouponYear, open: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val action = localized("View {0} coupons").replace("{0}", next.year.toString())
    Surface(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = action, onClick = open),
        shape = MaterialTheme.shapes.large, color = colors.primary.copy(alpha = .045f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = .15f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailNote("Next year with scheduled coupons")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(next.year.toString(), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                AppIcon(Icons.Outlined.ChevronRight, null, tint = colors.primary)
            }
            next.coupons.take(2).forEach { coupon ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(detailDate(coupon.date), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    FinancialText(coupon.purchase.text("bondName"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FinancialText(money(coupon.expectedNet!!, coupon.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (coupon.estimatedDate) DetailNote("Estimated coupon date")
                }
            }
            DetailNote("Preview of scheduled net payments, not confirmed receipts.")
        }
    }
}

@Composable
private fun CouponYearEmptyState(year: Int, next: NextCouponYear?) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(Icons.Outlined.EventBusy, null, Modifier.size(32.dp), tint = colors.onSurfaceVariant)
        AppText(localized("No coupons due in {0}").replace("{0}", year.toString()), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        AppText(next?.let { localized("You have scheduled coupons in {0}. Preview them below.").replace("{0}", it.year.toString()) }
            ?: localized("Try another year. Pending purchases are not included."),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
