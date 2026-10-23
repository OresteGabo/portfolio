package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
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
    var calendarFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var investmentHistory by rememberSaveable { mutableStateOf(false) }
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
        if (model.user == null) { calendarFocus = null; investmentHistory = false; tab = "Portfolio"; detail = null; editor = null; equityEditor = null; importReview = null; newPurchase = false; simulator = false; education = false }
    }
    fun imported(review: JsonRecord) {
        importReview = review
        val fields = review.optJSONObject("fields") ?: JsonRecord()
        if (review.text("kind") == "equity") { equityEditor = fields; editor = null } else { editor = fields; equityEditor = null }
        newPurchase = false
    }
    val colors = if (dark) darkColorScheme(primary = BrandCream, onPrimary = Ink, primaryContainer = Color(0xFF514716), onPrimaryContainer = BrandCream, secondary = BrandCream, secondaryContainer = Color(0xFF514716), onSecondaryContainer = BrandCream, background = Color(0xFF11150F), surface = Color(0xFF1D2219), onSurface = Color(0xFFF3F1E8), onSurfaceVariant = Color(0xFFC5C7BA), outlineVariant = Color(0xFF414735))
        else lightColorScheme(primary = BrandOlive, onPrimary = Color.White, primaryContainer = BrandCream, onPrimaryContainer = Ink, secondary = BrandOlive, secondaryContainer = BrandCream, onSecondaryContainer = Ink, background = Color(0xFFFAF8F2), surface = Color.White, onSurface = Ink)
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        val nested = investmentHistory || detail != null || editor != null || equityEditor != null || newPurchase || simulator || education
        val back = {
            if (detail == null && editor == null && equityEditor == null && !newPurchase && !simulator && !education) investmentHistory = false
            detail = null; editor = null; equityEditor = null; newPurchase = false; importReview = null; simulator = false; education = false
        }
        val screenTitle = when {
            newPurchase -> "Add purchase"
            editor != null -> if (importReview != null) "Review bond" else if (editor!!.text("id").isBlank()) "Add bond" else "Edit bond"
            equityEditor != null -> if (importReview != null) "Review shares" else "Add shares"
            simulator -> "Simulator"
            education -> "Learn"
            detail != null -> "Bond details"
            investmentHistory -> "All investments"
            model.user == null -> "Gabo"
            tab == "Account" -> "Settings"
            else -> tab
        }
        val portfolioHome = model.user != null && !model.locked && !nested && tab == "Portfolio"
        PlatformBackHandler(nested, back)
        Scaffold(
            topBar = {
                if (!portfolioHome) TopAppBar(
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
                        if (model.user != null && !model.locked && !nested && tab == "Portfolio") IconButton(onClick = model::refresh, enabled = !model.busy) { Icon(Icons.Outlined.Sync, "Sync portfolio") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            floatingActionButton = {
                if (model.user != null && !model.locked && !nested && tab == "Portfolio") ExtendedFloatingActionButton(
                    onClick = { importReview = null; newPurchase = true },
                    modifier = Modifier.semantics { contentDescription = "Add purchase" },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add purchase") }
                )
            },
            bottomBar = {
                if (model.user != null && !model.locked && !nested) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
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
                    model.locked -> UnlockScreen(model)
                    model.user == null -> SignInScreen(model)
                    newPurchase -> PurchaseEntryScreen(model, engine, ::imported,
                        manualBond = { importReview = null; newPurchase = false; editor = JsonRecord() },
                        manualShares = { importReview = null; newPurchase = false; equityEditor = JsonRecord() })
                    tab == "Account" && !nested -> AccountScreen(model, theme, changeTheme = { value -> theme = value; preferences.writeTheme(value) },
                        openSimulator = { simulator = true }, openEducation = { education = true }, openBond = { detail = it })
                    editor != null -> PurchaseEditor(model, editor!!, { editor = null; importReview = null }, importReview)
                    equityEditor != null -> EquityEditor(model, equityEditor!!, { equityEditor = null; importReview = null }, importReview)
                    simulator -> SimulatorScreen(model, engine) { simulator = false }
                    education -> EducationScreen(model) { education = false }
                    detail != null -> PurchaseDetails(model, detail!!, { detail = null }, { editor = detail; detail = null })
                    investmentHistory -> InvestmentHistoryScreen(model) { detail = it }
                    tab == "Portfolio" -> PortfolioScreen(model, seeAll = { investmentHistory = true },
                        openCalendar = { date -> calendarFocus = date.toString(); tab = "Calendar" }) { importReview = null; detail = it }
                    tab == "Calendar" -> CalendarScreen(model, initialDate = calendarFocus?.let(::calendarDate))
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
private fun SignInScreen(model: PortfolioModel) {
    val actions = LocalNativeActions.current
    val autofill = LocalAutofillManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var savePassword by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { PageTitle("Your investment journal", if (register) "Get started" else "Welcome back", "Track your investments, coupon income, and what comes next.") }
        item { Panel {
            Text(if (register) "Create your account" else "Sign in to your portfolio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (register) "Your invitation works once. Your portfolio stays private to your account." else "Use your email and password to access your own investments.", style = MaterialTheme.typography.bodySmall)
            Field("Email", email, { email = it }, keyboard = KeyboardType.Email, autofill = ContentType.Username, enabled = !model.busy)
            OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth().semantics { contentType = if (register) ContentType.NewPassword else ContentType.Password }, label = { Text("Password") }, singleLine = true, enabled = !model.busy,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                    platformImeOptions = credentialImeOptions(if (register) CredentialInput.NEW_PASSWORD else CredentialInput.PASSWORD)))
            if (register) {
                OutlinedTextField(confirmation, { confirmation = it.take(128) }, Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
                    label = { Text("Confirm password") }, singleLine = true, enabled = !model.busy, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, platformImeOptions = credentialImeOptions(CredentialInput.NEW_PASSWORD)))
                Field("Invitation token", invitation, { invitation = it }, enabled = !model.busy)
                Text("Use at least 15 characters. A long, unique passphrase works well.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().toggleable(value = savePassword, enabled = model.secureSignInAvailable && !model.busy, role = Role.Checkbox, onValueChange = { savePassword = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(savePassword, null, enabled = model.secureSignInAvailable && !model.busy)
                Column {
                    Text("Save sign-in with ${model.unlockLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(if (model.secureSignInAvailable) "Securely saved on this device" else "Set up device authentication first", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = {
                if (register && (password.length < 15 || password != confirmation || invitation.isBlank())) model.error = "Enter an invitation and matching passwords of at least 15 characters."
                else {
                    val submittedEmail = email.trim()
                    val submittedPassword = password
                    val submittedOrigin = model.origin
                    model.login(submittedEmail, submittedPassword, if (register) invitation else "", savePassword = savePassword) {
                        autofill?.commit()
                        actions.offerPasswordSave(submittedOrigin, submittedEmail, submittedPassword)
                    }
                }
            }, enabled = !model.busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (register) "Create account" else "Open private portfolio") }
            TextButton(onClick = { register = !register }, enabled = !model.busy) { Text(if (register) "Already have an account? Sign in" else "Use an invitation") }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)); Text("Your portfolio stays private", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
private fun UnlockScreen(model: PortfolioModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(Res.drawable.gabo_mark), "Gabo", Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("Portfolio locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Your saved sign-in is protected on this device.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = model::unlock, enabled = !model.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(when (model.unlockLabel) { "Face ID" -> Icons.Outlined.Face; "Touch ID" -> Icons.Outlined.Fingerprint; else -> Icons.Outlined.Lock }, null); Spacer(Modifier.width(8.dp))
            Text(if (model.busy) "Unlocking…" else "Unlock with ${model.unlockLabel}")
        }
        TextButton(onClick = model::usePassword, enabled = !model.busy) { Text("Use password instead") }
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, helper: String? = null, autofill: ContentType? = null, enabled: Boolean = true) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth().then(if (autofill != null) Modifier.semantics { contentType = autofill } else Modifier), label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, platformImeOptions = if (autofill == ContentType.Username) credentialImeOptions(CredentialInput.USERNAME) else null), supportingText = helper?.let { { Text(it) } })
}

@Composable
fun ChoiceRow(choices: List<String>, selected: String, choose: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { item -> FilterChip(selected = item == selected, onClick = { choose(item) }, label = { Text(item.replaceFirstChar { it.uppercase() }) }) }
    }
}

@Composable
private fun PurchaseDetails(model: PortfolioModel, purchase: JsonRecord, back: () -> Unit, edit: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete purchase?") }, text = { Text("This removes ${purchase.text("bondName")} from your synchronized portfolio.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { model.deletePurchase(purchase.text("id"), back); confirmDelete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep purchase") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { TextButton(onClick = back) { Text("← Back") }; PageTitle("Purchase details", purchase.text("bondName"), purchase.text("isin")) }
        item { Panel {
            BondPurchaseFacts(purchase)
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
