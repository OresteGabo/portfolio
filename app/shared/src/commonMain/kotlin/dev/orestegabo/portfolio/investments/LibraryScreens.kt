package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun CompanyPriceChart(
    symbol: String, name: String, quote: JsonRecord?, quotes: JsonRecord?, history: JsonRecord?,
    failed: Boolean, period: String, onPeriod: (String) -> Unit, onRetry: () -> Unit,
) {
    val observations = stockPriceHistory(history, quotes, symbol, period, Today)
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InvestmentLogo(ticker = symbol)
            Column(Modifier.weight(1f)) {
                AppText(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppText(name, style = MaterialTheme.typography.bodySmall)
            }
            if (observations.isEmpty()) FinancialText(quote?.let { money(it.optDouble("closing")) } ?: "—",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        ChoiceRow(listOf("month", "year", "all"), period, onPeriod)
        Box(Modifier.fillMaxWidth().heightIn(min = 360.dp).clip(MaterialTheme.shapes.medium)) {
            // Decorative identity stays with each pager page, including loading and empty states.
            val logo = investmentLogoResource(symbol)
            if (logo != null) {
                Image(painterResource(logo), contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(.72f).height(200.dp),
                    contentScale = ContentScale.Fit, alpha = .08f)
            } else {
                Text(symbol, modifier = Modifier.align(Alignment.Center),
                    fontSize = 64.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (history == null && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (observations.isNotEmpty()) {
                    InvestmentChart(observations.map { it.price }, observations.map { it.date.toString() },
                        notes = observations.map { it.note }, sparse = observations.any { it.contract },
                        dateRange = if (period == "year") yearlyPriceChartRange(Today) else null)
                    AppText(if (observations.any { it.contract }) "Closes & trades · known dates only"
                        else "Exchange closes · known dates only",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (history != null && !history.optBoolean("tradeHistoryAvailable")) AppText("Contract prices unavailable",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (history != null) {
                    Box(Modifier.fillMaxWidth().height(276.dp), contentAlignment = Alignment.Center) {
                        EmptyState("No price history", EmptyArtwork.CHARTS, compact = true, framed = false)
                    }
                }
                if (failed) TextButton(onClick = onRetry) { AppText("Retry price history") }
            }
        }
    }
}

@Composable
fun StocksScreen(model: PortfolioModel) {
    val reducedMotion = LocalReducedMotion.current
    var section by rememberSaveable { mutableStateOf("shares") }
    var search by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("month") }
    val histories = remember { mutableStateMapOf<String, JsonRecord>() }
    val failedHistories = remember { mutableStateMapOf<String, Boolean>() }
    var refreshRevision by remember { mutableIntStateOf(0) }
    val quoteRows = model.quotes?.rows("quotes").orEmpty()
    val watchlist = listOf("BOK" to "BK Group", "BLR" to "Bralirwa", "IMR" to "I&M Bank Rwanda", "CMR" to "Cimerwa", "RHB" to "RH Bophelo", "NMG" to "Nation Media Group", "USL" to "Uchumi", "KCB" to "KCB", "MTNR" to "MTN Rwanda", "EQTY" to "Equity Bank Group")
    val pager = rememberPagerState(pageCount = { watchlist.size })
    val scope = rememberCoroutineScope()
    val ticker = watchlist[pager.currentPage].first
    fun selectCompany(symbol: String) {
        val page = watchlist.indexOfFirst { it.first == symbol }
        if (page >= 0) scope.launch {
            if (reducedMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page)
        }
    }
    LaunchedEffect(Unit) { if (model.quotes == null) model.loadMarket() }
    // Fetch only the settled company. Captured keys keep rapid swipes from
    // displaying a previous company's response, and cached pages reopen instantly.
    LaunchedEffect(pager.settledPage, period, section, refreshRevision) {
        if (section != "shares") return@LaunchedEffect
        val symbol = watchlist[pager.settledPage].first
        val selectedPeriod = period
        val key = "$symbol/$selectedPeriod"
        if (histories.containsKey(key)) return@LaunchedEffect
        failedHistories.remove(key)
        try {
            histories[key] = model.api.request("/api/bonds/rse/equity-history?tickers=$symbol&period=$selectedPeriod")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failedHistories[key] = true
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChoiceRow(listOf("shares", "bonds"), section) { section = it } }
            model.quotes?.optJSONObject("source")?.let { source -> AppText(if (source.optBoolean("stale")) "Stale prices" else source.text("publishedAt").take(10), style = MaterialTheme.typography.labelSmall, color = if (source.optBoolean("stale")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(enabled = !model.busy, onClick = { histories.clear(); failedHistories.clear(); refreshRevision++; model.loadMarket(true) }) { AppIcon(Icons.Outlined.Refresh, "Refresh prices") }
        } }
        if (section == "shares") {
            item {
                SecondaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 0.dp, divider = {}) {
                    watchlist.forEachIndexed { index, (symbol, _) ->
                        Tab(selected = pager.currentPage == index, onClick = { selectCompany(symbol) }, text = { AppText(symbol) })
                    }
                }
            }
            item {
                HorizontalPager(state = pager, key = { watchlist[it].first }, pageSpacing = 12.dp,
                    verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { page ->
                    val (symbol, name) = watchlist[page]
                    val key = "$symbol/$period"
                    CompanyPriceChart(symbol, name, quoteRows.find { it.text("ticker") == symbol }, model.quotes,
                        histories[key], failedHistories[key] == true, period,
                        onPeriod = { period = it }, onRetry = { failedHistories.remove(key); refreshRevision++ })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().semantics { contentDescription = localized("Company ${pager.currentPage + 1} of ${watchlist.size}") },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    watchlist.indices.forEach { page ->
                        Surface(Modifier.size(width = if (page == pager.currentPage) 16.dp else 5.dp, height = 5.dp),
                            shape = MaterialTheme.shapes.small,
                            color = if (page == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {}
                    }
                }
            }
            val positions = model.portfolio.valuedShares(Today, model.quotes)
            item { AppText("Your holdings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (positions.isEmpty() && !model.busy) item {
                EmptyState("No active share holdings", EmptyArtwork.SHARES, compact = true)
            }
            items(positions) { valuation ->
                ShareHoldingCard(valuation,
                    name = watchlist.firstOrNull { it.first == valuation.position.ticker }?.second ?: valuation.position.ticker,
                    onClick = { selectCompany(valuation.position.ticker) })
            }
            item { AppText("Watchlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(watchlist) { (symbol, name) ->
                val quote = quoteRows.find { it.text("ticker") == symbol }
                ListItem(
                    headlineContent = { AppText(symbol, fontWeight = FontWeight.Bold) },
                    leadingContent = { InvestmentLogo(ticker = symbol) },
                    supportingContent = { AppText(name) },
                    trailingContent = { Column(horizontalAlignment = Alignment.End) {
                        FinancialText(quote?.let { money(it.optDouble("closing")) } ?: "—", fontWeight = FontWeight.SemiBold)
                        if (quote != null && quote.optDouble("previous", 0.0) > 0) {
                            val change = (quote.optDouble("closing") - quote.optDouble("previous")) / quote.optDouble("previous")
                            FinancialText((if (change > 0) "+" else "") + percent(change), style = MaterialTheme.typography.labelSmall, color = if (change < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    } },
                    modifier = Modifier.clickable { selectCompany(symbol) },
                    colors = ListItemDefaults.colors(containerColor = if (ticker == symbol) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
                )
            }
            item { AppText("Closing prices · excludes dividends and selling fees", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            if (model.market?.rows("outstanding").orEmpty().isNotEmpty()) item { Field("Search bond or ISIN", search, { search = it }) }
            val rows = model.market?.rows("outstanding").orEmpty().filter { it.text("bond").contains(search, true) || it.text("code").contains(search, true) }.sortedByDescending { it.optDouble("strategyScore", 0.0) }
            if (rows.isEmpty()) item {
                if (model.busy) LoadingState()
                else if (search.isNotBlank()) EmptyState("No matches", EmptyArtwork.SEARCH, actionLabel = "Clear search", onAction = { search = "" })
                else EmptyState("No bond prices yet", EmptyArtwork.BONDS, actionLabel = "Refresh", onAction = { model.loadMarket(refresh = true) })
            }
            items(rows) { bond -> Panel {
                AppText(bond.text("bond"), fontWeight = FontWeight.Bold)
                AppText(bond.text("code"), style = MaterialTheme.typography.labelSmall)
                Fact("Coupon", bond.text("couponRate")); Fact("Maturity", bond.text("maturityDate"))
                Fact("YTM", bond.text("yieldToMaturity")); Fact("Price", bond.text("closingPrice"))
            } }
        }
    }
}

@Composable
fun HistoryGraph(values: List<Double>, title: String = "Saved closing prices") {
    Panel {
        AppText(title, fontWeight = FontWeight.Bold)
        InvestmentChart(values)
        FinancialText("${money(values.minOrNull() ?: 0.0)} – ${money(values.maxOrNull() ?: 0.0)}", style = MaterialTheme.typography.bodySmall)
    }
}
