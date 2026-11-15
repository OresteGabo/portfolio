package dev.orestegabo.portfolio.investments

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
    val result by produceState<CouponYearPage?>(null, purchases, receipts, year, today, available, complete) {
        value = null
        if (complete) {
            val selectedYear = year
            value = withContext(Dispatchers.Default) {
                couponYearPage(purchases, receipts, selectedYear, today, available)
            }
        }
    }
    val page = result?.takeIf { it.year == year }
    val data = page?.data
    val currencies = page?.currencies.orEmpty()
    LaunchedEffect(currencies) { if (currencies.isNotEmpty() && currency !in currencies) currency = currencies.first() }
    val displayCurrency = currency.takeIf { it in currencies } ?: currencies.firstOrNull() ?: currency
    val currencyPage = page?.byCurrency?.get(displayCurrency)
    val rows = currencyPage?.rows.orEmpty()
    val visible = rows.take(shown)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "year") {
                CouponYearHeader(year, today.year) { year = it }
            }
            when {
                !complete -> item(key = "history-status") {
                    if (model.busy || model.portfolioSyncing || !requested) LoadingState()
                    else EmptyState("Investment history unavailable", EmptyArtwork.BONDS, compact = true,
                        actionLabel = "Try again", onAction = { model.error = null; model.loadInvestmentHistory(force = true) })
                }
                data == null -> item(key = "calculating") { LoadingState() }
                else -> {
                    if (currencies.size > 1) item(key = "currencies") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            currencies.forEach { code -> FilterChip(selected = displayCurrency == code, onClick = { currency = code }, label = { AppText(code) }) }
                        }
                    }
                    if (currencyPage != null && rows.isNotEmpty()) item(key = "totals") {
                        CouponYearTotals(currencyPage.totals, displayCurrency, data.incomplete.isNotEmpty())
                    }
                    if (!available) item(key = "receipt-status") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DetailNote(if (model.notificationsLoading) "Loading receipt status…" else "Receipt status unavailable")
                            TextButton(enabled = !model.notificationsLoading, onClick = model::refreshNotifications) { AppText("Refresh receipts") }
                        }
                    }
                    if (data.incomplete.isNotEmpty()) item(key = "incomplete-terms") {
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
                            YearCouponRow(coupon, displayCurrency, openBond)
                        }
                    }
                    if (rows.isNotEmpty()) item(key = "pagination") {
                        CouponPagination(visible.size, rows.size) { shown += 12 }
                    }
                    if (year < 9998 && (rows.isNotEmpty() || page.next == null)) item(key = "next-year") {
                        FilledTonalButton(onClick = { year++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            AppText("${localized("Next year")} · ${year + 1}", Modifier.weight(1f))
                            AppIcon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                    item(key = "schedule-note") { DetailNote("Grouped by coupon due date, not bank settlement date. Schedules cover active and matured bonds; sold bonds show recorded entries only. Saved dates take precedence; other dates are estimates. Principal repayments are excluded.") }
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
