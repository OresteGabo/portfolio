package dev.orestegabo.portfolio.investments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DocumentsScreen(model: PortfolioModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val actions = LocalNativeActions.current
    fun save(document: JsonRecord) = model.action {
        val path = document.text("downloadUrl")
        require(path.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+"))) { "Invalid document URL." }
        val data = model.api.bytes(path)
        require(data.take(5).toByteArray().decodeToString() == "%PDF-") { "The server did not return a PDF." }
        if (actions.savePdf(document.text("originalFileName").ifBlank { "investment.pdf" }, data)) model.message = "Document saved."
    }
    val categories = listOf("all") + model.documents.map { it.text("category") }.distinct()
    val filtered = model.documents.filter { (category == "all" || it.text("category") == category) && listOf("label", "instrumentName", "description", "issuer", "originalFileName").any { key -> it.text(key).contains(search, true) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (model.documents.isNotEmpty()) item { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Search documents", search, { search = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ChoiceRow(categories, category) { category = it } }
                IconButton(onClick = model::refresh, enabled = !model.busy) { Icon(Icons.Outlined.Refresh, "Refresh documents") }
            }
        } }
        if (filtered.isEmpty()) item { Box(Modifier.padding(20.dp)) {
            if (model.busy) LoadingState()
            else if (model.documents.isEmpty()) EmptyState("No documents yet", EmptyArtwork.DOCUMENTS,
                actionLabel = "Refresh", onAction = model::refresh)
            else EmptyState("No matches", EmptyArtwork.SEARCH, actionLabel = "Clear filters", onAction = { search = ""; category = "all" })
        } }
        items(filtered, key = { it.text("id") }) { document ->
            val isExpanded = expanded == document.text("id")
            Column {
                ListItem(
                    headlineContent = { FinancialText(document.text("label"), maxLines = 2, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(listOf(document.text("category"), document.text("documentDate")).filter { it.isNotBlank() }.joinToString(" · ")) },
                    leadingContent = { Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { save(document) }, enabled = !model.busy) { Icon(Icons.Outlined.Download, "Download ${privateFinancialValue(document.text("label"))}") }
                            Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (isExpanded) "Collapse details" else "Expand details")
                        }
                    },
                    modifier = Modifier.clickable { expanded = if (isExpanded) null else document.text("id") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                )
                AnimatedVisibility(isExpanded) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (document.text("instrumentName").isNotBlank()) Text(document.text("instrumentName"), fontWeight = FontWeight.SemiBold)
                        if (document.text("description").isNotBlank()) FinancialText(document.text("description"), style = MaterialTheme.typography.bodySmall)
                        if (document.text("issuer").isNotBlank()) Fact("Issuer", document.text("issuer"))
                        document.optJSONObject("details")?.let { details ->
                            if (details.text("summary").isNotBlank()) FinancialText(details.text("summary"), style = MaterialTheme.typography.bodySmall)
                            listOf("facts", "charges", "flow").forEach { key -> details.rows(key).forEach { Fact(it.text("label"), it.text("value")) } }
                            details.rows("sections").forEach { section -> Text(section.text("title"), fontWeight = FontWeight.Bold); section.rows("items").forEach { Fact(it.text("label"), it.text("value")) } }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun StocksScreen(model: PortfolioModel) {
    var section by rememberSaveable { mutableStateOf("shares") }
    var search by rememberSaveable { mutableStateOf("") }
    var ticker by rememberSaveable { mutableStateOf("BOK") }
    var period by rememberSaveable { mutableStateOf("month") }
    var history by remember { mutableStateOf<JsonRecord?>(null) }
    var requested by remember { mutableStateOf("") }
    val quoteRows = model.quotes?.rows("quotes").orEmpty()
    val watchlist = listOf("BOK" to "BK Group", "BLR" to "Bralirwa", "IMR" to "I&M Bank Rwanda", "CMR" to "Cimerwa", "RHB" to "RH Bophelo", "NMG" to "Nation Media Group", "USL" to "Uchumi", "KCB" to "KCB", "MTNR" to "MTN Rwanda", "EQTY" to "Equity Bank Group")
    LaunchedEffect(Unit) { if (model.quotes == null) model.loadMarket() }
    LaunchedEffect(ticker, period, model.busy) {
        val key = "$ticker/$period"
        if (!model.busy && requested != key) {
            requested = key
            history = null
            model.action { history = model.api.request("/api/bonds/rse/equity-history?tickers=$ticker&period=$period") }
        }
    }
    val observations = history?.rows("observations").orEmpty().filter { it.text("ticker") == ticker }.sortedBy { it.text("date") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChoiceRow(listOf("shares", "bonds"), section) { section = it } }
            model.quotes?.optJSONObject("source")?.let { source -> Text(if (source.optBoolean("stale")) "Stale prices" else source.text("publishedAt").take(10), style = MaterialTheme.typography.labelSmall, color = if (source.optBoolean("stale")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(enabled = !model.busy, onClick = { requested = ""; model.loadMarket(true) }) { Icon(Icons.Outlined.Refresh, "Refresh prices") }
        } }
        if (section == "shares") {
            item { ChoiceRow(watchlist.map { it.first }, ticker) { ticker = it } }
            item { Panel {
                val quote = quoteRows.find { it.text("ticker") == ticker }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(ticker, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(watchlist.first { it.first == ticker }.second, style = MaterialTheme.typography.bodySmall)
                    }
                    if (observations.size <= 1) FinancialText(quote?.let { money(it.optDouble("closing")) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                ChoiceRow(listOf("month", "year", "all"), period) { period = it }
                if (observations.size > 1) {
                    InvestmentChart(observations.map { it.optDouble("closing", Double.NaN) }, observations.map { it.text("date").take(10) })
                } else {
                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        if (model.busy && history == null) LoadingState()
                        else EmptyState("No price history", EmptyArtwork.CHARTS, compact = true, framed = false)
                    }
                }
            } }
            val positions = model.portfolio.valuedShares(Today, model.quotes)
            item { Text("Your holdings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (positions.isEmpty() && !model.busy) item {
                EmptyState("No active share holdings", EmptyArtwork.SHARES, compact = true)
            }
            items(positions) { valuation ->
                val position = valuation.position
                val shares = position.shares
                val cost = position.cashCost
                Panel(Modifier.clickable { ticker = position.ticker.takeIf { value -> watchlist.any { it.first == value } } ?: ticker }) {
                    FinancialText("${position.ticker} · ${shares.toLong()} shares", fontWeight = FontWeight.Bold)
                    Fact("Cost", money(cost, position.currency))
                    Fact("Estimated value", money(valuation.estimatedValue, position.currency))
                    valuation.gainLoss?.let { Fact("Gain / loss", money(it, position.currency)) }
                    if (valuation.closingPrice == null) Text("Purchase cost used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Text("Watchlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(watchlist) { (symbol, name) ->
                val quote = quoteRows.find { it.text("ticker") == symbol }
                ListItem(
                    headlineContent = { Text(symbol, fontWeight = FontWeight.Bold) },
                    leadingContent = { InvestmentLogo(ticker = symbol) },
                    supportingContent = { Text(name) },
                    trailingContent = { Column(horizontalAlignment = Alignment.End) {
                        FinancialText(quote?.let { money(it.optDouble("closing")) } ?: "—", fontWeight = FontWeight.SemiBold)
                        if (quote != null && quote.optDouble("previous", 0.0) > 0) {
                            val change = (quote.optDouble("closing") - quote.optDouble("previous")) / quote.optDouble("previous")
                            FinancialText((if (change > 0) "+" else "") + percent(change), style = MaterialTheme.typography.labelSmall, color = if (change < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    } },
                    modifier = Modifier.clickable { ticker = symbol },
                    colors = ListItemDefaults.colors(containerColor = if (ticker == symbol) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
                )
            }
            item { Text("Closing prices · excludes dividends and selling fees", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            if (model.market?.rows("outstanding").orEmpty().isNotEmpty()) item { Field("Search bond or ISIN", search, { search = it }) }
            val rows = model.market?.rows("outstanding").orEmpty().filter { it.text("bond").contains(search, true) || it.text("code").contains(search, true) }.sortedByDescending { it.optDouble("strategyScore", 0.0) }
            if (rows.isEmpty()) item {
                if (model.busy) LoadingState()
                else if (search.isNotBlank()) EmptyState("No matches", EmptyArtwork.SEARCH, actionLabel = "Clear search", onAction = { search = "" })
                else EmptyState("No bond prices yet", EmptyArtwork.BONDS, actionLabel = "Refresh", onAction = { model.loadMarket(refresh = true) })
            }
            items(rows) { bond -> Panel {
                Text(bond.text("bond"), fontWeight = FontWeight.Bold)
                Text(bond.text("code"), style = MaterialTheme.typography.labelSmall)
                Fact("Coupon", bond.text("couponRate")); Fact("Maturity", bond.text("maturityDate"))
                Fact("YTM", bond.text("yieldToMaturity")); Fact("Price", bond.text("closingPrice"))
            } }
        }
    }
}

@Composable
fun HistoryGraph(values: List<Double>, title: String = "Saved closing prices") {
    Panel {
        Text(title, fontWeight = FontWeight.Bold)
        InvestmentChart(values)
        FinancialText("${money(values.minOrNull() ?: 0.0)} – ${money(values.maxOrNull() ?: 0.0)}", style = MaterialTheme.typography.bodySmall)
    }
}
