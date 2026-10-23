package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.gabo_mark
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate

private val BrandOlive = Color(0xFF6D5E0F)
private val BrandCream = Color(0xFFF2E6A7)
private val Ink = Color(0xFF252317)
private data class Destination(val title: String, val icon: ImageVector, val selectedIcon: ImageVector)
private val destinations = listOf(
    Destination("Portfolio", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    Destination("Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Destination("Documents", Icons.Outlined.Description, Icons.Filled.Description),
    Destination("Stocks", Icons.AutoMirrored.Outlined.TrendingUp, Icons.AutoMirrored.Filled.TrendingUp),
    Destination("Account", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)
)

fun JsonRecord.terms(): BondTerms? = runCatching {
    BondTerms(LocalDate.parse(text("purchaseDate")), LocalDate.parse(text("maturityDate")), optDouble("faceValue", 0.0),
        optDouble("couponRate", 0.0), optDouble("withholdingTaxRate", 0.0), optInt("couponFrequency", 2),
        text("firstCouponDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        optJSONArray("couponDates")?.let { dates -> (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) } }.orEmpty())
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
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
    var newPurchase by rememberSaveable { mutableStateOf(false) }
    val preferences = rememberServerSettings()
    val systemDark = isSystemInDarkTheme()
    var theme by rememberSaveable { mutableStateOf(preferences.readTheme()?.takeIf { it in listOf("system", "light", "dark") } ?: "system") }
    val dark = when (theme) { "light" -> false; "dark" -> true; else -> systemDark }
    LaunchedEffect(model.user) {
        if (model.user == null) { tab = "Portfolio"; detail = null; editor = null; equityEditor = null; importReview = null; newPurchase = false; simulator = false; education = false }
    }
    fun imported(review: JsonRecord) {
        importReview = review
        val fields = review.optJSONObject("fields") ?: JsonRecord()
        if (review.text("kind") == "equity") { equityEditor = fields; editor = null } else { editor = fields; equityEditor = null }
        newPurchase = false
    }
    val colors = if (dark) darkColorScheme(primary = BrandCream, onPrimary = Ink, primaryContainer = Color(0xFF514716), onPrimaryContainer = BrandCream, secondary = BrandCream, secondaryContainer = Color(0xFF514716), onSecondaryContainer = BrandCream, background = Color(0xFF17160F), surface = Color(0xFF211F17))
        else lightColorScheme(primary = BrandOlive, onPrimary = Color.White, primaryContainer = BrandCream, onPrimaryContainer = Ink, secondary = BrandOlive, secondaryContainer = BrandCream, onSecondaryContainer = Ink, background = Color(0xFFFAF8F2), surface = Color.White, onSurface = Ink)
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        val nested = detail != null || editor != null || equityEditor != null || newPurchase || simulator || education
        val back = { detail = null; editor = null; equityEditor = null; newPurchase = false; importReview = null; simulator = false; education = false }
        val screenTitle = when {
            newPurchase -> "Add purchase"
            editor != null -> if (importReview != null) "Review bond" else if (editor!!.text("id").isBlank()) "Add bond" else "Edit bond"
            equityEditor != null -> if (importReview != null) "Review shares" else "Add shares"
            simulator -> "Simulator"
            education -> "Learn"
            detail != null -> "Bond details"
            model.user == null -> "Gabo"
            else -> tab
        }
        PlatformBackHandler(nested, back)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(screenTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (model.user == null && !nested) Text("Investment journal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        if (nested) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        else Image(painterResource(Res.drawable.gabo_mark), "Gabo logo", Modifier.padding(start = 16.dp, end = 4.dp).size(40.dp))
                    },
                    actions = {
                        if (model.user != null && !nested && tab == "Portfolio") IconButton(onClick = model::refresh, enabled = !model.busy) { Icon(Icons.Outlined.Sync, "Sync portfolio") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            floatingActionButton = {
                if (model.user != null && !nested && tab == "Portfolio") ExtendedFloatingActionButton(
                    onClick = { importReview = null; newPurchase = true },
                    modifier = Modifier.semantics { contentDescription = "Add purchase" },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add purchase") }
                )
            },
            bottomBar = {
                if (model.user != null && !nested) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEach { destination ->
                        val selected = tab == destination.title
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tab = destination.title },
                            icon = { Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = { Text(destination.title, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                        )
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
                    model.user == null -> SignInScreen(model)
                    newPurchase -> PurchaseEntryScreen(model, engine, ::imported,
                        manualBond = { importReview = null; newPurchase = false; editor = JsonRecord() },
                        manualShares = { importReview = null; newPurchase = false; equityEditor = JsonRecord() })
                    tab == "Account" && !nested -> AccountScreen(model, theme) { value -> theme = value; preferences.writeTheme(value) }
                    editor != null -> PurchaseEditor(model, editor!!, { editor = null; importReview = null }, importReview)
                    equityEditor != null -> EquityEditor(model, equityEditor!!, { equityEditor = null; importReview = null }, importReview)
                    simulator -> SimulatorScreen(model, engine) { simulator = false }
                    education -> EducationScreen(model) { education = false }
                    detail != null -> PurchaseDetails(model, detail!!, { detail = null }, { editor = detail; detail = null })
                    tab == "Portfolio" -> PortfolioScreen(model, { importReview = null; detail = it },
                        tools = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { simulator = true }, enabled = !model.busy, modifier = Modifier.weight(1f)) { Text("Simulator") }
                                OutlinedButton(onClick = { education = true }, modifier = Modifier.weight(1f)) { Text("Learn") }
                            }
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
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        item { PageTitle("Your investment journal", if (register) "Get started" else "Welcome back", "Track your investments, coupon income, and what comes next.") }
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
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)); Text("Your portfolio stays private", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, helper: String? = null) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), supportingText = helper?.let { { Text(it) } })
}

@Composable
private fun PortfolioScreen(model: PortfolioModel, detail: (JsonRecord) -> Unit, tools: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("all") }
    val currencies = model.purchases.groupBy { it.text("currency") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Overview", "Your portfolio", "A private record of what you own, what you earn, and what comes next.") }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = BrandOlive, contentColor = Color.White) {
                Box(Modifier.fillMaxWidth()) {
                    Canvas(Modifier.matchParentSize()) {
                        val path = Path().apply { moveTo(size.width * .65f, 0f); lineTo(size.width, size.height * .7f); lineTo(size.width * .8f, size.height); lineTo(size.width * .45f, 0f); close() }
                        drawPath(path, Color.White.copy(alpha = .06f))
                    }
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("RECORDED INVESTMENTS", letterSpacing = 2.sp, fontSize = 10.sp, color = BrandCream)
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
