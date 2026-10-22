package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

@Composable
fun CalendarScreen(model: PortfolioModel) {
    var filter by rememberSaveable { mutableStateOf("upcoming") }
    val today = Today
    val events = model.calendar.filter { filter == "all" || it.text("settlementDate") >= today.toString() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Plan ahead", "Issuance calendar", "Rwanda Treasury Bond calendar with recorded applications, purchase status, settlement dates, and maturity tracking.") }
        item { Panel {
            Text("BNR · FY 2026/2027", fontWeight = FontWeight.Bold)
            Text("BNR Treasury Bond Issuance Calendar for FY 2026/2027", style = MaterialTheme.typography.bodyMedium)
            Text("Copied from your web app. Extracted 2026-06-24. Confirm any changes with BNR and your broker.", style = MaterialTheme.typography.bodySmall)
        } }
        item { ChoiceRow(listOf("upcoming", "all"), filter) { filter = it } }
        items(events, key = { it.text("id") }) { event ->
            val open = LocalDate.parse(event.text("openBookDate"))
            val close = LocalDate.parse(event.text("closingBookDate"))
            val settle = LocalDate.parse(event.text("settlementDate"))
            val status = when {
                today < open -> "Opens in ${today.daysUntil(open)}d"
                today <= close -> "Book open"
                today <= settle -> "Settling"
                else -> "Completed"
            }
            val alert = when {
                today == open -> "Book opens today" to "This is the day to confirm the prospectus, coupon terms, available cash, and send the bid instruction to your broker."
                today < open && today.daysUntil(open) <= 3 -> "Book opens soon" to "Prepare cash, decide the bid amount, and keep the broker order details ready before the book opens."
                today == close -> "Book closes today" to "This is the final day in the published window. Confirm your bid status with the broker before close."
                today > open && today < close -> "Book is open" to "The auction window is open. Submit or confirm your bid before the closing book date."
                today > close && today < settle -> "Awaiting settlement" to "The book is closed. Watch for allocation confirmation and settlement cash movement."
                today == settle -> "Settlement today" to "Check the final allocation, cash debit, and update your portfolio records after broker confirmation."
                else -> null
            }
            Panel {
                Text(status.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(event.text("title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Fact("Announcement", event.text("announcementDate"))
                Fact("Book opens", event.text("openBookDate"))
                Fact("Book closes", event.text("closingBookDate"))
                Fact("Settlement", event.text("settlementDate"))
                Fact("Maturity", event.text("maturityDate"))
                alert?.let { (title, body) -> HorizontalDivider(); Text(title, fontWeight = FontWeight.Bold); Text(body, style = MaterialTheme.typography.bodySmall) }
                val recorded = model.purchases.filter { it.text("settlementDate") == event.text("settlementDate") && it.text("maturityDate") == event.text("maturityDate") }
                Fact("Recorded applications", recorded.size.toString())
                recorded.forEach { Fact(it.text("bondName"), "${it.text("status")} · ${money(it.optDouble("faceValue", 0.0), it.text("currency"))}") }
            }
        }
        if (events.isEmpty()) item { EmptyState("No upcoming issuances", "The copied calendar ends in June 2027. Select All to see the published schedule.") }
        val futureCoupons = model.purchases.filter { it.text("status") == "active" }.flatMap { purchase ->
            purchase.terms()?.let { terms -> trackBond(terms, today).schedule.filter { it >= today }.map { Triple(it, purchase, trackBond(terms, today).netPerPayment) } }.orEmpty()
        }.sortedBy { it.first }.take(30)
        if (futureCoupons.isNotEmpty()) item { Text("Your upcoming coupons", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(futureCoupons) { (date, purchase, amount) -> Panel {
            Text(date.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(purchase.text("bondName"))
            Fact("Expected net coupon", money(amount, purchase.text("currency")))
            Text("${purchase.text("scheduleConfidence")} schedule", style = MaterialTheme.typography.bodySmall)
        } }
    }
}

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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Your library", "Investment documents", "Private investment document library for bond results and equity contract notes.") }
        item { Field("Search documents", search, { search = it }); ChoiceRow(categories, category) { category = it } }
        item { TextButton(onClick = model::refresh, enabled = !model.busy) { Text("Refresh library") } }
        if (filtered.isEmpty()) item { EmptyState("No documents to show", if (model.user?.optBoolean("isOwner") == true) "Documents come from your server’s private library. Try another search or refresh." else "The web API makes the private document library available to the owner account only.") }
        items(filtered, key = { it.text("id") }) { document -> Panel {
            Text("${document.text("category")} · ${document.text("documentDate")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(document.text("label"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(document.text("instrumentName"))
            Text(document.text("description"), style = MaterialTheme.typography.bodySmall)
            Fact("Issuer", document.text("issuer"))
            if (expanded == document.text("id")) {
                document.optJSONObject("details")?.let { details ->
                    if (details.text("summary").isNotBlank()) Text(details.text("summary"))
                    listOf("facts", "charges", "flow").forEach { key -> details.rows(key).forEach { Fact(it.text("label"), it.text("value")) } }
                    details.rows("sections").forEach { section -> Text(section.text("title"), fontWeight = FontWeight.Bold); section.rows("items").forEach { Fact(it.text("label"), it.text("value")) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = if (expanded == document.text("id")) null else document.text("id") }) { Text(if (expanded == document.text("id")) "Less" else "Details") }
                Button(onClick = { save(document) }, enabled = !model.busy) { Text("Save PDF") }
            }
        } }
    }
}

@Composable
fun StocksScreen(model: PortfolioModel) {
    var section by rememberSaveable { mutableStateOf("shares") }
    var search by rememberSaveable { mutableStateOf("") }
    var ticker by rememberSaveable { mutableStateOf("BOK") }
    var period by rememberSaveable { mutableStateOf("all") }
    var history by remember { mutableStateOf<JsonRecord?>(null) }
    val quoteRows = model.quotes?.rows("quotes").orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Market watch", "Stocks & market prices", "Follow ten RSE companies and compare your shares with their purchase cost. Estimated values exclude dividends and selling fees.") }
        item { Button(enabled = !model.busy, onClick = { model.loadMarket(true) }) { Text(if (model.market == null) "Load RSE market" else "Refresh RSE prices") } }
        item { ChoiceRow(listOf("shares", "bonds", "history"), section) { section = it } }
        model.quotes?.optJSONObject("source")?.let { source -> item { Panel {
            Text("RSE · ${source.text("status")}", fontWeight = FontWeight.Bold)
            Fact("Checked", source.text("checkedAt")); Fact("Published", source.text("publishedAt"))
            if (source.optBoolean("stale")) Text("The source is stale. These prices may not reflect current trading.", color = MaterialTheme.colorScheme.error)
        } } }
        if (section == "shares") {
            item { Text("Your shares", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            val groups = model.holdings.filter { it.text("status") == "active" && it.text("tradeDate") <= Today.toString() }.groupBy { "${it.text("ticker")}-${it.text("currency")}" }
            if (groups.isEmpty()) item { EmptyState("No active stock holdings", "Add share purchases from Portfolio to track your purchase cost alongside market prices.") }
            items(groups.values.toList()) { lots ->
                val first = lots.first()
                val shares = lots.sumOf { it.optDouble("shares", 0.0) }
                val cost = lots.sumOf { it.optDouble("netAmountPayable", 0.0) }
                val quote = quoteRows.find { it.text("ticker") == first.text("ticker") }
                Panel {
                    Text("${first.text("securityName")} · ${first.text("ticker")}", fontWeight = FontWeight.Bold)
                    Fact("Shares", shares.toLong().toString()); Fact("Purchase cost", money(cost, first.text("currency")))
                    if (quote != null && first.text("currency") == "RWF") {
                        val value = shares * quote.optDouble("closing", 0.0)
                        Fact("RSE estimated market value", money(value))
                        Fact("Unrealized gain / loss", money(value - cost))
                        if (quote.optDouble("volume", -1.0) == 0.0) Text("No shares traded in RSE’s displayed update; the closing quote is not a fresh execution.", style = MaterialTheme.typography.bodySmall)
                    } else Text("Market valuation unavailable.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("RSE companies", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            val watchlist = listOf("BLR" to "Bralirwa", "IMR" to "I&M Bank Rwanda", "CMR" to "Cimerwa", "RHB" to "RH Bophelo", "NMG" to "Nation Media Group", "USL" to "Uchumi", "BOK" to "BK Group", "KCB" to "KCB", "MTNR" to "MTN Rwanda", "EQTY" to "Equity Bank Group")
            items(watchlist) { (symbol, name) ->
                val quote = quoteRows.find { it.text("ticker") == symbol }
                Panel {
                    Text("$symbol · $name", fontWeight = FontWeight.Bold)
                    if (quote == null) Text("Quote unavailable") else {
                        Fact("Closing price", money(quote.optDouble("closing")))
                        if (!quote.isNull("previous") && quote.optDouble("previous", 0.0) > 0) Fact("Change", percent((quote.optDouble("closing") - quote.optDouble("previous")) / quote.optDouble("previous")))
                        Fact("Volume", quote.text("volume"))
                    }
                }
            }
        } else if (section == "bonds") {
            item { Field("Search bond or ISIN", search, { search = it }) }
            model.market?.let { data -> item { Fact("Market checked", data.text("fetchedAt")); Fact("Trades status", data.text("tradesStatus")) } }
            val rows = model.market?.rows("outstanding").orEmpty().filter { it.text("bond").contains(search, true) || it.text("code").contains(search, true) }.sortedByDescending { it.optDouble("strategyScore", 0.0) }
            if (rows.isEmpty()) item { EmptyState("No bond market data", "Load the market or change your search. Missing quotes are shown as unavailable.") }
            items(rows) { bond -> Panel {
                Text(bond.text("bond"), fontWeight = FontWeight.Bold)
                Fact("ISIN", bond.text("code")); Fact("Coupon", bond.text("couponRate")); Fact("Maturity", bond.text("maturityDate"))
                Fact("Published YTM", bond.text("yieldToMaturity")); Fact("Net annualized yield", percent(bond.optDouble("netAnnualizedYield", 0.0)))
                Fact("Yield source", bond.text("yieldSource")); Fact("Closing price", bond.text("closingPrice"))
                Fact("Last traded", bond.text("lastTradedAt")); Fact("Recent trade count", bond.text("recentTradeCount"))
                Text("Estimates follow your web API; confirm executable prices and fees with your broker.", style = MaterialTheme.typography.bodySmall)
            } }
        } else {
            item { ChoiceRow(listOf("BLR", "IMR", "CMR", "RHB", "NMG", "USL", "BOK", "KCB", "MTNR", "EQTY"), ticker) { ticker = it; history = null } }
            item { ChoiceRow(listOf("month", "year", "all"), period) { period = it; history = null } }
            item { Button(enabled = !model.busy, onClick = { model.action { history = model.api.request("/api/bonds/rse/equity-history?tickers=$ticker&period=$period") } }) { Text("Load saved history") } }
            val observations = history?.rows("observations").orEmpty().filter { it.text("ticker") == ticker }.sortedBy { it.text("date") }
            if (observations.size > 1) item { HistoryGraph(observations.map { it.optDouble("closing", 0.0) }) }
            if (history != null && observations.isEmpty()) item { EmptyState("No saved prices in this range", "History builds as daily RSE updates are collected; earlier prices cannot be reconstructed from today’s quote.") }
            if (history?.optBoolean("available") == false) item { Text("Saved history is unavailable.", color = MaterialTheme.colorScheme.error) }
            items(observations.reversed()) { point -> Panel { Fact(point.text("date"), money(point.optDouble("closing", 0.0))) } }
        }
    }
}

@Composable
fun HistoryGraph(values: List<Double>, title: String = "Saved closing prices") {
    val color = MaterialTheme.colorScheme.primary
    Panel {
        Text(title, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val low = values.minOrNull() ?: 0.0
            val high = values.maxOrNull() ?: 1.0
            val span = (high - low).coerceAtLeast(1.0)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                val y = size.height - ((value - low) / span * (size.height - 10)).toFloat() - 5
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
        Text("${money(values.minOrNull() ?: 0.0)} – ${money(values.maxOrNull() ?: 0.0)}\nObservation sequence; no interpolation of missing days.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AccountScreen(model: PortfolioModel) {
    var origin by rememberSaveable { mutableStateOf(model.origin) }
    var accounts by remember { mutableStateOf<JsonRecord?>(null) }
    var registration by remember { mutableStateOf<JsonRecord?>(null) }
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    val actions = LocalNativeActions.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Settings", "Your account", model.user?.text("email") ?: "Connect to your existing investment app.") }
        item { Panel {
            Text("Server connection", fontWeight = FontWeight.Bold)
            Field("Server origin", origin, { origin = it }, helper = "For example https://bonds.orestegabo.dev")
            Text("Changing server signs you out. Debug builds also allow HTTP for a local development server.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { accounts = null; registration = null; model.connect(origin) }, enabled = !model.busy) { Text("Connect") }
            if (model.user != null) OutlinedButton(enabled = !model.busy, onClick = model::logout) { Text("Sign out") }
        } }
        if (model.user?.optBoolean("isOwner") == true) {
            item { Panel {
                Text("Account management", fontWeight = FontWeight.Bold)
                Button(enabled = !model.busy, onClick = { model.action {
                    accounts = model.api.request("/api/bonds/auth/accounts")
                    registration = model.api.request("/api/bonds/auth/registration")
                } }) { Text("Load accounts & registration") }
                accounts?.rows("accounts")?.forEach { account ->
                    Fact(account.text("email"), "${account.text("kind")} · ${account.text("status")}")
                    account.optJSONObject("portfolio")?.let { Fact("Bond purchases", it.text("purchaseCount")) }
                }
                registration?.let { settings ->
                    Fact("Invitations", if (settings.optBoolean("available")) "Available" else "Unavailable on this deployment")
                    settings.optJSONObject("invitation")?.let { Fact("Active invitation", it.text("email")); Fact("Expires", it.text("expiresAt")) }
                    if (settings.optBoolean("available")) {
                        Field("Invitation email (optional)", inviteEmail, { inviteEmail = it })
                        Button(enabled = !model.busy, onClick = { model.action {
                            val response = model.api.request("/api/bonds/auth/registration", "POST", JsonRecord().put("email", inviteEmail))
                            registration = model.api.request("/api/bonds/auth/registration")
                            val url = "${model.origin}/join#invite=${response.text("invite")}"
                            actions.shareText(url)
                        } }) { Text("Create & share invitation") }
                        OutlinedButton(enabled = !model.busy, onClick = { model.action { model.api.request("/api/bonds/auth/registration", "DELETE"); registration = model.api.request("/api/bonds/auth/registration") } }) { Text("Close invitations") }
                    }
                }
            } }
        }
        if (model.user != null) item { OnlineTools(model) }
    }
}

@Composable
fun OnlineTools(model: PortfolioModel) {
    val actions = LocalNativeActions.current
    Panel {
        Text("Connected web tools", fontWeight = FontWeight.Bold)
        Text("Open your existing tools online. Your browser has its own sign-in session.", style = MaterialTheme.typography.bodySmall)
        listOf("/portfolio" to "PDF import & full portfolio tools", "/simulator" to "Investment simulator", "/education" to "Bond education", "/create-password" to "Password setup").forEach { (path, title) ->
            TextButton(onClick = { actions.openUrl(model.origin + path) }) { Text("$title ↗") }
        }
    }
}
