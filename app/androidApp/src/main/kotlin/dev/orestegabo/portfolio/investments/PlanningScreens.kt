package dev.orestegabo.portfolio.investments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PdfImportButton(model: PortfolioModel, engine: LocalInvestmentEngine, imported: (JSONObject) -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) model.action {
            engine.onProgress = { progress = it }
            progress = "Opening PDF…"
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            require(output.size() + count <= 10 * 1024 * 1024) { "Choose a PDF of up to 10 MB." }
                            output.write(chunk, 0, count)
                        }
                        output.toByteArray()
                    } ?: error("Could not open the selected PDF.")
                }
                val result = engine.run("pdf", pdf = bytes)
                imported(result)
            } finally { engine.onProgress = {}; progress = "" }
        }
    }
    Panel {
        Text("Import a purchase PDF", fontWeight = FontWeight.Bold)
        Text("Use a BNR bond result or RSE equity purchase contract note. We’ll detect the purchase type for you.", style = MaterialTheme.typography.bodySmall)
        if (progress.isNotBlank()) Text(progress, color = MaterialTheme.colorScheme.primary)
        Button(enabled = !model.busy, onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Choose PDF") }
        Text("The PDF and recognized text stay on this device. Review the extracted details before saving.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SimulatorScreen(model: PortfolioModel, engine: LocalInvestmentEngine, back: () -> Unit) {
    val fields = remember {
        mutableStateMapOf("monthlyContribution" to "300000", "horizonYears" to "20", "startMonth" to "7", "startYear" to "2026",
            "couponPaymentsPerYear" to "2", "reinvestmentRate" to "100", "auctionFillRate" to "67", "purchaseMinimum" to "100000", "purchaseCharge" to "11000")
    }
    val rates = remember { mutableStateMapOf("5" to "11", "7" to "11.5", "10" to "12", "15" to "12.9", "20" to "13.1", "25" to "13.25") }
    val tenors = remember { mutableStateListOf(5, 7, 10, 15, 20, 25) }
    val periods = remember { mutableStateListOf<MutableMap<String, String>>() }
    val injections = remember { mutableStateListOf<MutableMap<String, String>>() }
    var include by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var resultsOnly by rememberSaveable { mutableStateOf(false) }
    val names = mapOf("monthlyContribution" to "Monthly contribution (RWF)", "horizonYears" to "Horizon in years", "startMonth" to "Start month (1–12)", "startYear" to "Start year", "couponPaymentsPerYear" to "Coupon payments per year", "reinvestmentRate" to "Coupon reinvestment (%)", "auctionFillRate" to "Auction fill rate (%)", "purchaseMinimum" to "Purchase minimum (RWF)", "purchaseCharge" to "Purchase charge (RWF)")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = back) { Text("← Portfolio") }; PageTitle("Plan the long term", "Investment simulator", "Explore monthly contributions, cash injections, coupon reinvestment, auction allocation, and maturity proceeds using the same model as your web app.") }
        item { ChoiceRow(listOf("assumptions", "results"), if (resultsOnly) "results" else "assumptions") { resultsOnly = it == "results" } }
        if (!resultsOnly) {
            item { Panel { Text("Planning assumptions", fontWeight = FontWeight.Bold); Text("Illustrative estimates. The model repeats the issuance tenor cycle and uses your configured rates; future auctions and returns are uncertain.", style = MaterialTheme.typography.bodySmall) } }
            names.forEach { (key, title) -> item { Field(title, fields[key].orEmpty(), { fields[key] = it }, KeyboardType.Decimal) } }
            item { Row { Checkbox(include, { include = it }); Text("Include my active RWF bond purchases", Modifier.padding(top = 12.dp)) } }
            item { Text("Allowed tenors & annual coupons", fontWeight = FontWeight.Bold) }
            listOf(5, 7, 10, 15, 20, 25).forEach { tenor -> item {
                Row { Checkbox(tenor in tenors, { selected -> if (selected) tenors.add(tenor) else tenors.remove(tenor) }); Text("$tenor years", Modifier.padding(top = 12.dp)) }
                Field("$tenor-year annual coupon (%)", rates[tenor.toString()].orEmpty(), { rates[tenor.toString()] = it }, KeyboardType.Decimal)
            } }
            item { Text("Contribution periods", fontWeight = FontWeight.Bold); Text("When periods are added, they replace the default monthly contribution. Overlapping periods add together.", style = MaterialTheme.typography.bodySmall) }
            items(periods.size) { index ->
                val period = periods[index]
                Panel {
                    Field("Amount (RWF)", period["amount"].orEmpty(), { periods[index] = period.toMutableMap().apply { put("amount", it) } }, KeyboardType.Decimal)
                    Field("Start month index", period["startMonth"].orEmpty(), { periods[index] = period.toMutableMap().apply { put("startMonth", it) } }, KeyboardType.Number)
                    Field("End month index", period["endMonth"].orEmpty(), { periods[index] = period.toMutableMap().apply { put("endMonth", it) } }, KeyboardType.Number)
                    TextButton(onClick = { periods.removeAt(index) }) { Text("Remove period") }
                }
            }
            item { OutlinedButton(onClick = { periods.add(mutableMapOf("amount" to "300000", "startMonth" to "1", "endMonth" to "240")) }) { Text("+ Contribution period") } }
            item { Text("One-time cash injections", fontWeight = FontWeight.Bold) }
            items(injections.size) { index ->
                val injection = injections[index]
                Panel {
                    Field("Label", injection["label"].orEmpty(), { injections[index] = injection.toMutableMap().apply { put("label", it) } })
                    Field("Amount (RWF)", injection["amount"].orEmpty(), { injections[index] = injection.toMutableMap().apply { put("amount", it) } }, KeyboardType.Decimal)
                    Field("Month index", injection["month"].orEmpty(), { injections[index] = injection.toMutableMap().apply { put("month", it) } }, KeyboardType.Number)
                    TextButton(onClick = { injections.removeAt(index) }) { Text("Remove injection") }
                }
            }
            item { OutlinedButton(onClick = { injections.add(mutableMapOf("label" to "Cash injection", "amount" to "1000000", "month" to "1")) }) { Text("+ Cash injection") } }
            item { Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                model.action {
                    val assumptions = JSONObject()
                    fields.forEach { (key, text) ->
                        val value = text.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Enter valid planning assumptions.")
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
                    val rateJson = JSONObject()
                    rates.forEach { (key, text) -> rateJson.put(key, (text.toDoubleOrNull()?.takeIf { it in 10.65..13.5 } ?: error("Coupon assumptions must be between 10.65% and 13.5%, as in the web model.")) / 100) }
                    assumptions.put("tenorCouponRates", rateJson).put("allowedTenors", JSONArray(tenors)).put("contributionPeriods", JSONArray(periods.mapIndexed { index, period ->
                        val amount = period["amount"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Invalid contribution amount.")
                        val start = period["startMonth"]?.toIntOrNull() ?: error("Invalid start month.")
                        val end = period["endMonth"]?.toIntOrNull() ?: error("Invalid end month.")
                        require(start >= 1 && end >= start && end <= years.toInt() * 12) { "Contribution periods must fit the horizon." }
                        JSONObject().put("id", "period-$index").put("amount", amount).put("startMonth", start).put("endMonth", end)
                    }))
                    val injectionJson = JSONArray(injections.mapIndexed { index, injection ->
                        val amount = injection["amount"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Invalid injection amount.")
                        val month = injection["month"]?.toIntOrNull() ?: error("Invalid injection month.")
                        require(month in 1..years.toInt() * 12) { "Cash injections must fit the horizon." }
                        JSONObject().put("id", "injection-$index").put("label", injection["label"]).put("amount", amount).put("month", month)
                    })
                    result = engine.run("projection", JSONObject().put("assumptions", assumptions).put("injections", injectionJson).put("includeHoldings", include).put("purchases", JSONArray(model.purchases.filter { it.text("currency") == "RWF" })))
                    resultsOnly = true
                }
            }) { Text("Calculate projection") } }
        } else {
            val summary = result?.optJSONObject("summary")
            if (summary == null) item { EmptyState("No projection yet", "Configure assumptions and calculate a projection.") }
            else {
                item { Panel {
                    Text("Projected account value", fontWeight = FontWeight.Bold)
                    Text(money(summary.optDouble("finalAccountValue", 0.0)), style = MaterialTheme.typography.headlineMedium)
                    listOf("finalPortfolio" to "Bond portfolio", "finalCashBalance" to "Cash balance", "totalContributions" to "Total contributions & charges", "totalCoupons" to "Total net coupons", "totalReinvested" to "Reinvested coupons", "annualPassiveIncome" to "Annual passive income", "monthlyPassiveIncome" to "Monthly passive income").forEach { (key, title) -> Fact(title, money(summary.optDouble(key, 0.0))) }
                    listOf("milestone50m" to "50M milestone month", "milestone100m" to "100M milestone month", "milestone200m" to "200M milestone month", "passiveIncomeCrossoverYear" to "Income crossover year").forEach { (key, title) -> Fact(title, summary.text(key).ifBlank { "Not reached" }) }
                } }
                val rows = result!!.rows("rows")
                if (rows.size > 1) item { HistoryGraph(rows.map { it.optDouble("totalAccountValue", 0.0) }, "Projected monthly account value") }
                item { Text("Monthly projection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(rows) { row -> Panel {
                    Text("${row.text("calendarYear")}-${row.text("calendarMonth").padStart(2, '0')}", fontWeight = FontWeight.Bold)
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
fun EducationScreen(back: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember { JSONArray(context.assets.open("education.json").bufferedReader().use { it.readText() }).objects() }
    var search by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton(onClick = back) { Text("← Portfolio") }; PageTitle("Learning library", "Bond education", "The educational content from your web app, adapted for reading on a phone.") }
        item { Field("Search lessons", search, { search = it }) }
        items(blocks.filter { it.text("text").contains(search, true) }) { block ->
            val kind = block.text("kind")
            Text(block.text("text"), style = when (kind) {
                "h1", "h2" -> MaterialTheme.typography.headlineSmall
                "h3", "h4", "summary" -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.bodyMedium
            }, fontWeight = if (kind.startsWith("h") || kind == "summary") FontWeight.Bold else FontWeight.Normal)
            if (block.text("url").isNotBlank()) TextButton(onClick = {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(block.text("url"))))
            }) { Text("Open source ↗") }
        }
    }
}
