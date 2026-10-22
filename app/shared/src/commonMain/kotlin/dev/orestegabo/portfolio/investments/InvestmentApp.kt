package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate

private val Pine = Color(0xFF075E51)
private val Mint = Color(0xFFC9F3D8)
private val Ink = Color(0xFF132D28)
fun JsonRecord.terms(): BondTerms? = runCatching {
    BondTerms(LocalDate.parse(text("purchaseDate")), LocalDate.parse(text("maturityDate")), optDouble("faceValue", 0.0),
        optDouble("couponRate", 0.0), optDouble("withholdingTaxRate", 0.0), optInt("couponFrequency", 2),
        text("firstCouponDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        optJSONArray("couponDates")?.let { dates -> (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) } }.orEmpty())
}.getOrNull()

@Composable
fun InvestmentApp(model: PortfolioModel) {
    var tab by rememberSaveable { mutableStateOf("Portfolio") }
    var detail by remember { mutableStateOf<JsonRecord?>(null) }
    var editor by remember { mutableStateOf<JsonRecord?>(null) }
    var equityEditor by remember { mutableStateOf<JsonRecord?>(null) }
    var simulator by rememberSaveable { mutableStateOf(false) }
    var education by rememberSaveable { mutableStateOf(false) }
    var importReview by remember { mutableStateOf<JsonRecord?>(null) }
    val engine = rememberInvestmentEngine()
    DisposableEffect(engine) { onDispose { engine.destroy() } }
    var settings by rememberSaveable { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    importReview?.let { review ->
        MaterialTheme {
            AlertDialog(onDismissRequest = { importReview = null }, title = { Text("Review extracted purchase") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("We detected a ${review.text("kind")} purchase. Check every field before saving.")
                    review.optJSONArray("warnings")?.let { warnings -> (0 until warnings.length()).forEach { Text(warnings.getString(it)) } }
                    review.optJSONArray("missingFields")?.let { missing -> if (missing.length() > 0) Text("Missing: " + (0 until missing.length()).joinToString { missing.getString(it) }) }
                    if (review.optBoolean("usedOcr")) Text("Text recognition was used. Check all dates and amounts carefully.")
                } },
                confirmButton = { TextButton(onClick = {
                    val fields = review.optJSONObject("fields") ?: JsonRecord()
                    if (review.text("kind") == "equity") equityEditor = fields else editor = fields
                    importReview = null
                }) { Text("Review details") } },
                dismissButton = { TextButton(onClick = { importReview = null }) { Text("Cancel") } })
        }
    }
    val colors = if (dark) darkColorScheme(primary = Mint, secondary = Mint, background = Color(0xFF0D1C19), surface = Color(0xFF142622))
        else lightColorScheme(primary = Pine, secondary = Pine, background = Color(0xFFF4F7F3), surface = Color.White, onSurface = Ink)
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        PlatformBackHandler(detail != null || editor != null || equityEditor != null || settings || simulator || education) { detail = null; editor = null; equityEditor = null; settings = false; simulator = false; education = false }
        Scaffold(
            topBar = {
                Surface(tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("G", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 23.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("GABO", fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Text("Your investment journal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { dark = !dark }) { Text(if (dark) "Light" else "Dark") }
                        TextButton(onClick = { settings = !settings }) { Text(if (settings) "Back" else "Account") }
                    }
                }
            },
            bottomBar = {
                if (model.user != null && editor == null && equityEditor == null) NavigationBar {
                    listOf("Portfolio" to "◈", "Calendar" to "▦", "Documents" to "▤", "Stocks" to "↗").forEach { (name, glyph) ->
                        NavigationBarItem(selected = tab == name, onClick = { tab = name; detail = null; settings = false; simulator = false; education = false },
                            icon = { Text(glyph, fontSize = 23.sp) }, label = { Text(name, maxLines = 1, fontSize = 11.sp) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                InvestmentEngineHost(engine)
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.error?.let { Notice(it, true) { model.error = null } }
                model.message?.let { Notice(it, false) { model.message = null } }
                when {
                    settings -> AccountScreen(model)
                    model.user == null -> SignInScreen(model)
                    editor != null -> PurchaseEditor(model, editor!!, { editor = null })
                    equityEditor != null -> EquityEditor(model, equityEditor!!) { equityEditor = null }
                    simulator -> SimulatorScreen(model, engine) { simulator = false }
                    education -> EducationScreen(model) { education = false }
                    detail != null -> PurchaseDetails(model, detail!!, { detail = null }, { editor = detail; detail = null })
                    tab == "Portfolio" -> PortfolioScreen(model, { detail = it }, { editor = JsonRecord() }, { equityEditor = JsonRecord() },
                        tools = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { simulator = true }, enabled = !model.busy, modifier = Modifier.weight(1f)) { Text("Simulator") }
                                OutlinedButton(onClick = { education = true }, modifier = Modifier.weight(1f)) { Text("Learn") }
                            }
                            PdfImportButton(model, engine) { importReview = it }
                        })
                    tab == "Calendar" -> CalendarScreen(model)
                    tab == "Documents" -> DocumentsScreen(model)
                    tab == "Stocks" -> StocksScreen(model)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = dismiss) { Text("Dismiss") }
        }
    }
}

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "—" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EmptyState(title: String, body: String) = Panel {
    Text(title, fontWeight = FontWeight.Bold)
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SignInScreen(model: PortfolioModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Spacer(Modifier.height(12.dp)); PageTitle("Private portfolio", "Build a clearer\nfinancial future.", "Privately record and track individual bond purchases, coupon schedules, maturity dates, prices, fees, and expected income.") }
        item { Panel {
            Text(if (register) "Create your account" else "Sign in to your portfolio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (register) "Your invitation works once. Your portfolio stays private to your account." else "Use your email and password to access your own investments.", style = MaterialTheme.typography.bodySmall)
            Field("Email", email, { email = it }, keyboard = KeyboardType.Email)
            OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (register) {
                OutlinedTextField(confirmation, { confirmation = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Field("Invitation token", invitation, { invitation = it })
                Text("Use at least 15 characters. A long, unique passphrase works well.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                if (register && (password.length < 15 || password != confirmation || invitation.isBlank())) model.error = "Enter an invitation and matching passwords of at least 15 characters."
                else model.login(email, password, if (register) invitation else "")
            }, enabled = !model.busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (register) "Create account" else "Open private portfolio") }
            TextButton(onClick = { register = !register }) { Text(if (register) "Already have an account? Sign in" else "Use an invitation") }
        } }
        item { Text("Connected to ${model.origin}\nChange the server in Account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, helper: String? = null) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), supportingText = helper?.let { { Text(it) } })
}

@Composable
private fun PortfolioScreen(model: PortfolioModel, detail: (JsonRecord) -> Unit, add: () -> Unit, addStock: () -> Unit, tools: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("all") }
    val currencies = model.purchases.groupBy { it.text("currency") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Overview", "Your portfolio", "A private record of what you own, what you earn, and what comes next.") }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Pine, contentColor = Color.White) {
                Box(Modifier.fillMaxWidth()) {
                    Canvas(Modifier.matchParentSize()) {
                        val path = Path().apply { moveTo(size.width * .65f, 0f); lineTo(size.width, size.height * .7f); lineTo(size.width * .8f, size.height); lineTo(size.width * .45f, 0f); close() }
                        drawPath(path, Color.White.copy(alpha = .06f))
                    }
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("RECORDED INVESTMENTS", letterSpacing = 2.sp, fontSize = 10.sp, color = Mint)
                        if (currencies.isEmpty()) Text("No purchases yet", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        currencies.forEach { (currency, rows) ->
                            Text(money(rows.filter { it.text("status") == "active" }.sumOf { it.optDouble("faceValue", 0.0) }, currency), fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Active bond face value • ${model.purchases.count { it.text("status") == "active" }} holdings", fontSize = 12.sp)
                        HorizontalDivider(color = Color.White.copy(alpha = .2f))
                        Text("${model.holdings.size} share purchases  /  ${model.documents.size} documents", fontSize = 12.sp)
                    }
                }
            }
        }
        items(currencies.entries.toList()) { (currency, rows) ->
            val active = rows.filter { it.text("status") == "active" }
            val tracked = active.mapNotNull { row -> row.terms()?.let { trackBond(it, Today) } }
            Panel {
                Text("Income · $currency", fontWeight = FontWeight.Bold)
                Fact("Net annual coupon income", money(active.filter { it.text("maturityDate") > Today.toString() && it.text("purchaseDate") <= Today.toString() }.sumOf { it.optDouble("faceValue", 0.0) * it.optDouble("couponRate", 0.0) * (1 - it.optDouble("withholdingTaxRate", 0.0)) }, currency))
                Fact("Scheduled coupons to date", money(tracked.sumOf { it.couponsPaid }, currency))
                Fact("Accrued coupon", money(tracked.sumOf { it.accruedCoupon }, currency))
                Text("Schedule estimates; confirm payments with your broker. Cash cost includes fees and accrued interest.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = add, enabled = !model.busy, modifier = Modifier.weight(1f)) { Text("+ Bond") }
            OutlinedButton(onClick = addStock, enabled = !model.busy, modifier = Modifier.weight(1f)) { Text("+ Shares") }
            TextButton(onClick = model::refresh, enabled = !model.busy) { Text("Sync") }
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { tools() } }
        item { Field("Search bonds, ISIN, broker", query, { query = it }) }
        item { ChoiceRow(listOf("all", "active", "submitted", "sold", "matured"), status) { status = it } }
        val filtered = model.purchases.filter { (status == "all" || it.text("status") == status) && listOf("bondName", "isin", "broker").any { key -> it.text(key).contains(query, true) } }
        if (filtered.isEmpty()) item { EmptyState("No bond purchases", "Add a purchase or change your filters. Saved records synchronize with your web portfolio.") }
        items(filtered, key = { it.text("id") }) { purchase -> BondCard(purchase) { detail(purchase) } }
        if (model.holdings.isNotEmpty()) item { Text("Share purchases", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(model.holdings) { holding -> Panel {
            Text("${holding.text("securityName")} · ${holding.text("ticker")}", fontWeight = FontWeight.Bold)
            Fact("Shares", holding.text("shares"))
            Fact("Cash cost", money(holding.optDouble("netAmountPayable", 0.0), holding.text("currency")))
            Fact("Trade date", holding.text("tradeDate"))
            Fact("Broker", holding.text("broker"))
        } }
    }
}

@Composable
fun ChoiceRow(choices: List<String>, selected: String, choose: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { item -> FilterChip(selected = item == selected, onClick = { choose(item) }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
    }
}

@Composable
fun BondCard(purchase: JsonRecord, click: () -> Unit) {
    Panel(Modifier.clickable(onClick = click)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(purchase.text("bondName"), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            SuggestionChip(onClick = click, label = { Text(purchase.text("status")) })
        }
        Text(money(purchase.optDouble("faceValue", 0.0), purchase.text("currency")), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Fact("Coupon / maturity", "${percent(purchase.optDouble("couponRate", 0.0))} · ${purchase.text("maturityDate")}")
        purchase.terms()?.let { terms ->
            val tracking = trackBond(terms, Today)
            LinearProgressIndicator(progress = { tracking.progress }, modifier = Modifier.fillMaxWidth())
            Fact("Next coupon", tracking.nextCoupon?.toString() ?: "No remaining coupons")
        }
        Text("View purchase →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PurchaseDetails(model: PortfolioModel, purchase: JsonRecord, back: () -> Unit, edit: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete purchase?") }, text = { Text("This removes ${purchase.text("bondName")} from your synchronized portfolio.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { model.deletePurchase(purchase.text("id"), back); confirmDelete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep purchase") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { TextButton(onClick = back) { Text("← Portfolio") }; PageTitle("Purchase details", purchase.text("bondName"), purchase.text("isin")) }
        item { Panel {
            listOf("issuer" to "Issuer", "instrumentType" to "Instrument", "currency" to "Currency", "market" to "Market", "status" to "Status", "purchaseDate" to "Purchase", "settlementDate" to "Settlement", "maturityDate" to "Maturity", "broker" to "Broker", "accountReference" to "Account", "scheduleConfidence" to "Schedule").forEach { (key, label) -> Fact(label, purchase.text(key)) }
            Fact("Face value", money(purchase.optDouble("faceValue"), purchase.text("currency")))
            Fact("Price", "${purchase.text("pricePercent")}%")
            Fact("Fees", money(purchase.optDouble("feesPaid"), purchase.text("currency")))
            Fact("Accrued interest paid", money(purchase.optDouble("accruedInterestPaid"), purchase.text("currency")))
            Fact("Total cash cost", money(purchase.optDouble("amountInvested"), purchase.text("currency")))
            Fact("Coupon rate", percent(purchase.optDouble("couponRate")))
            Fact("Withholding tax", percent(purchase.optDouble("withholdingTaxRate")))
            Text(purchase.text("notes"))
        } }
        purchase.terms()?.let { terms ->
            val tracking = trackBond(terms, Today)
            item { Panel {
                Text("Coupon tracking", fontWeight = FontWeight.Bold)
                Fact("Net per payment", money(tracking.netPerPayment, purchase.text("currency")))
                Fact("Scheduled to date", money(tracking.couponsPaid, purchase.text("currency")))
                Fact("Accrued", money(tracking.accruedCoupon, purchase.text("currency")))
                Fact("Remaining coupons", money(tracking.remainingCoupons, purchase.text("currency")))
                Text("${purchase.text("scheduleConfidence")} schedule · as of $Today", style = MaterialTheme.typography.bodySmall)
            } }
            items(tracking.schedule) { date -> Panel { Fact(date.toString(), "${if (date <= Today) "Scheduled past" else "Upcoming"} · ${money(tracking.netPerPayment, purchase.text("currency"))}") } }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = edit, enabled = !model.busy) { Text("Edit purchase") }
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !model.busy) { Text("Delete") }
        } }
    }
}
