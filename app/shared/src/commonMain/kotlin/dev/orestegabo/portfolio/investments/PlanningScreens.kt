package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PdfImportButton(model: PortfolioModel, engine: InvestmentEngine, imported: (JsonRecord) -> Unit, bondOnly: Boolean = false, expectedStatement: JsonRecord? = null) {
    val actions = LocalNativeActions.current
    fun choosePdf() = model.importPurchasePdf(actions, engine, bondOnly, expectedStatement, imported)
    Panel {
        AppText(if (bondOnly) "Confirm purchase" else "Import confirmed purchase", fontWeight = FontWeight.Bold)
        if (bondOnly) AppText("BNR confirmation PDFs only", style = MaterialTheme.typography.bodySmall)
        else {
            AppText("Accepted PDFs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppText("BNR bond results • Share purchase contracts • BK Capital statements", style = MaterialTheme.typography.bodySmall)
        }
        Button(enabled = !model.busy, onClick = ::choosePdf, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            AppIcon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); AppText(if (model.pdfImportProgress != null) "Reading PDF…" else if (bondOnly) "Upload confirmation" else "Choose purchase PDF")
        }
        AppText("Read on your device · Up to 10 MB", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PurchaseEntryScreen(model: PortfolioModel, engine: InvestmentEngine, imported: (JsonRecord) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PdfImportButton(model, engine, imported) }
    }
}

@Composable
fun SimulatorScreen(model: PortfolioModel, engine: InvestmentEngine, back: () -> Unit) {
    val fields = remember {
        mutableStateMapOf("monthlyContribution" to "300000", "horizonYears" to "20", "startMonth" to "7", "startYear" to "2026",
            "couponPaymentsPerYear" to "2", "reinvestmentRate" to "100", "auctionFillRate" to "67", "purchaseMinimum" to "100000", "purchaseCharge" to "11000")
    }
    val rates = remember { mutableStateMapOf("5" to "11", "7" to "11.5", "10" to "12", "15" to "12.9", "20" to "13.1", "25" to "13.25") }
    val tenors = remember { mutableStateListOf(5, 7, 10, 15, 20, 25) }
    val periods = remember { mutableStateListOf<MutableMap<String, String>>() }
    val injections = remember { mutableStateListOf<MutableMap<String, String>>() }
    var include by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<JsonRecord?>(null) }
    var resultsOnly by rememberSaveable { mutableStateOf(false) }
    val names = mapOf("monthlyContribution" to "Monthly contribution (RWF)", "horizonYears" to "Horizon in years", "startMonth" to "Start month (1–12)", "startYear" to "Start year", "couponPaymentsPerYear" to "Coupon payments per year", "reinvestmentRate" to "Coupon reinvestment (%)", "auctionFillRate" to "Auction fill rate (%)", "purchaseMinimum" to "Purchase minimum (RWF)", "purchaseCharge" to "Purchase charge (RWF)")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = back) { AppText("← Portfolio") }; PageTitle("Plan the long term", "Investment simulator", "Explore monthly contributions, cash injections, coupon reinvestment, auction allocation, and maturity proceeds using the same model as your web app.") }
        item { ChoiceRow(listOf("assumptions", "results"), if (resultsOnly) "results" else "assumptions") { resultsOnly = it == "results" } }
        if (!resultsOnly) {
            item { Panel { AppText("Planning assumptions", fontWeight = FontWeight.Bold); AppText("Illustrative estimates. The model repeats the issuance tenor cycle and uses your configured rates; future auctions and returns are uncertain.", style = MaterialTheme.typography.bodySmall) } }
            names.forEach { (key, title) -> item { Field(title, fields[key].orEmpty(), { fields[key] = it }, KeyboardType.Decimal) } }
            item { Row { Checkbox(include, { include = it }); AppText("Include my active RWF bond purchases", Modifier.padding(top = 12.dp)) } }
            item { AppText("Allowed tenors & annual coupons", fontWeight = FontWeight.Bold) }
            listOf(5, 7, 10, 15, 20, 25).forEach { tenor -> item {
                Row { Checkbox(tenor in tenors, { selected -> if (selected) tenors.add(tenor) else tenors.remove(tenor) }); AppText("$tenor years", Modifier.padding(top = 12.dp)) }
                Field("$tenor-year annual coupon (%)", rates[tenor.toString()].orEmpty(), { rates[tenor.toString()] = it }, KeyboardType.Decimal)
            } }
            item { AppText("Contribution periods", fontWeight = FontWeight.Bold); AppText("When periods are added, they replace the default monthly contribution. Overlapping periods add together.", style = MaterialTheme.typography.bodySmall) }
            items(periods.size) { index ->
                val period = periods[index]
                Panel {
                    Field("Amount (RWF)", period["amount"].orEmpty(), { periods[index] = period.toMutableMap().apply { put("amount", it) } }, KeyboardType.Decimal)
                    Field("Start month index", period["startMonth"].orEmpty(), { periods[index] = period.toMutableMap().apply { put("startMonth", it) } }, KeyboardType.Number)
                    Field("End month index", period["endMonth"].orEmpty(), { periods[index] = period.toMutableMap().apply { put("endMonth", it) } }, KeyboardType.Number)
                    TextButton(onClick = { periods.removeAt(index) }) { AppText("Remove period") }
                }
            }
            item { OutlinedButton(onClick = { periods.add(mutableMapOf("amount" to "300000", "startMonth" to "1", "endMonth" to "240")) }) { AppText("+ Contribution period") } }
            item { AppText("One-time cash injections", fontWeight = FontWeight.Bold) }
            items(injections.size) { index ->
                val injection = injections[index]
                Panel {
                    Field("Label", injection["label"].orEmpty(), { injections[index] = injection.toMutableMap().apply { put("label", it) } })
                    Field("Amount (RWF)", injection["amount"].orEmpty(), { injections[index] = injection.toMutableMap().apply { put("amount", it) } }, KeyboardType.Decimal)
                    Field("Month index", injection["month"].orEmpty(), { injections[index] = injection.toMutableMap().apply { put("month", it) } }, KeyboardType.Number)
                    TextButton(onClick = { injections.removeAt(index) }) { AppText("Remove injection") }
                }
            }
            item { OutlinedButton(onClick = { injections.add(mutableMapOf("label" to "Cash injection", "amount" to "1000000", "month" to "1")) }) { AppText("+ Cash injection") } }
            item { Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                model.action {
                    val assumptions = JsonRecord()
                    fields.forEach { (key, text) ->
                        val value = text.toInputNumberOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Enter valid planning assumptions.")
                        assumptions.put(key, if (key == "reinvestmentRate" || key == "auctionFillRate") value / 100 else value)
                    }
                    val years = assumptions.getDouble("horizonYears")
                    require(years in 1.0..100.0 && years % 1.0 == 0.0) { "Use a whole horizon between 1 and 100 years." }
                    require(assumptions.getDouble("startMonth") in 1.0..12.0 && assumptions.getDouble("startMonth") % 1.0 == 0.0) { "Start month must be 1–12." }
                    require(assumptions.getDouble("startYear") in 2000.0..2199.0 && assumptions.getDouble("startYear") % 1.0 == 0.0) { "Use a whole start year between 2000 and 2199." }
                    require(assumptions.getDouble("purchaseMinimum") > 0) { "Purchase minimum must be positive." }
                    require(assumptions.getDouble("couponPaymentsPerYear") in listOf(1.0, 2.0, 3.0, 4.0, 6.0, 12.0)) { "Choose 1, 2, 3, 4, 6, or 12 coupon payments per year." }
                    require(assumptions.getDouble("reinvestmentRate") <= 1 && assumptions.getDouble("auctionFillRate") <= 1) { "Reinvestment and fill rates must be 0–100%." }
                    require(tenors.isNotEmpty()) { "Select at least one tenor." }
                    val rateJson = JsonRecord()
                    rates.forEach { (key, text) -> rateJson.put(key, (text.toInputNumberOrNull()?.takeIf { it in 10.65..13.5 } ?: error("Coupon assumptions must be between 10.65% and 13.5%, as in the web model.")) / 100) }
                    assumptions.put("tenorCouponRates", rateJson).put("allowedTenors", JsonList(tenors)).put("contributionPeriods", JsonList(periods.mapIndexed { index, period ->
                        val amount = period["amount"]?.toInputNumberOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Invalid contribution amount.")
                        val start = period["startMonth"]?.toIntOrNull() ?: error("Invalid start month.")
                        val end = period["endMonth"]?.toIntOrNull() ?: error("Invalid end month.")
                        require(start >= 1 && end >= start && end <= years.toInt() * 12) { "Contribution periods must fit the horizon." }
                        JsonRecord().put("id", "period-$index").put("amount", amount).put("startMonth", start).put("endMonth", end)
                    }))
                    val injectionJson = JsonList(injections.mapIndexed { index, injection ->
                        val amount = injection["amount"]?.toInputNumberOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Invalid injection amount.")
                        val month = injection["month"]?.toIntOrNull() ?: error("Invalid injection month.")
                        require(month in 1..years.toInt() * 12) { "Cash injections must fit the horizon." }
                        JsonRecord().put("id", "injection-$index").put("label", injection["label"]).put("amount", amount).put("month", month)
                    })
                    result = engine.run("projection", JsonRecord().put("assumptions", assumptions).put("injections", injectionJson).put("includeHoldings", include).put("purchases", JsonList(model.purchases.filter { it.text("currency") == "RWF" })))
                    resultsOnly = true
                }
            }) { AppText("Calculate projection") } }
        } else {
            val summary = result?.optJSONObject("summary")
            if (summary == null) item { EmptyState("No projection yet", EmptyArtwork.PROJECTION, actionLabel = "Edit assumptions", onAction = { resultsOnly = false }) }
            else {
                item { Panel {
                    AppText("Projected account value", fontWeight = FontWeight.Bold)
                    FinancialText(money(summary.optDouble("finalAccountValue", 0.0)), style = MaterialTheme.typography.headlineMedium)
                    listOf("finalPortfolio" to "Bond portfolio", "finalCashBalance" to "Cash balance", "totalContributions" to "Total contributions & charges", "totalCoupons" to "Total net coupons", "totalReinvested" to "Reinvested coupons", "annualPassiveIncome" to "Annual passive income", "monthlyPassiveIncome" to "Monthly passive income").forEach { (key, title) -> Fact(title, money(summary.optDouble(key, 0.0))) }
                    listOf("milestone50m" to "50M milestone month", "milestone100m" to "100M milestone month", "milestone200m" to "200M milestone month", "passiveIncomeCrossoverYear" to "Income crossover year").forEach { (key, title) -> Fact(title, summary.text(key).ifBlank { "Not reached" }) }
                } }
                val rows = result!!.rows("rows")
                if (rows.size > 1) item { HistoryGraph(rows.map { it.optDouble("totalAccountValue", 0.0) }, "Projected monthly account value") }
                item { AppText("Monthly projection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(rows) { row -> Panel {
                    AppText("${row.text("calendarYear")}-${row.text("calendarMonth").padStart(2, '0')}", fontWeight = FontWeight.Bold)
                    Fact("Closing account", money(row.optDouble("totalAccountValue", 0.0)))
                    Fact("Contribution", money(row.optDouble("personalContribution", 0.0)))
                    Fact("New bond purchase", money(row.optDouble("newBondPurchase", 0.0)))
                    Fact("Coupons", money(row.optDouble("couponPayment", 0.0)))
                    Fact("Matured principal", money(row.optDouble("maturedPrincipal", 0.0)))
                    Fact("Cash balance", money(row.optDouble("closingCashBalance", 0.0)))
                } }
            }
        }
    }
}

@Composable
fun EducationScreen(model: PortfolioModel, back: () -> Unit) {
    val actions = LocalNativeActions.current
    val blocks = model.education
    var search by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton(onClick = back) { AppText("← Portfolio") }; PageTitle("Learning library", "Bond education", "The educational content from your web app, adapted for reading on a phone.") }
        if (blocks.isNotEmpty()) item { Field("Search lessons", search, { search = it }) }
        val filtered = blocks.filter { localized(it.text("text")).contains(search, true) }
        if (filtered.isEmpty()) item {
            if (model.busy) LoadingState()
            else if (blocks.isEmpty()) EmptyState("No lessons available", EmptyArtwork.DOCUMENTS, compact = true)
            else EmptyState("No matches", EmptyArtwork.SEARCH, compact = true,
                actionLabel = "Clear search", onAction = { search = "" })
        }
        items(filtered) { block ->
            val kind = block.text("kind")
            AppText(block.text("text"), style = when (kind) {
                "h1", "h2" -> MaterialTheme.typography.headlineSmall
                "h3", "h4", "summary" -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.bodyMedium
            }, fontWeight = if (kind.startsWith("h") || kind == "summary") FontWeight.Bold else FontWeight.Normal)
            if (block.text("url").isNotBlank()) TextButton(onClick = {
                actions.openUrl(block.text("url"))
            }) { AppText("Open source ↗") }
        }
    }
}
