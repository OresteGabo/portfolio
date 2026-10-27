package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    Destination("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
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
    val selectionFeedback = LocalSelectionFeedback.current
    var notificationPurchase by remember { mutableStateOf<JsonRecord?>(null) }
    var notificationScreen by rememberSaveable { mutableStateOf(false) }
    var handledInboxRequest by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf("Portfolio") }
    var calendarFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingIssueId by rememberSaveable { mutableStateOf<String?>(null) }
    var missingDocuments by rememberSaveable { mutableStateOf(false) }
    var pendingEntry by rememberSaveable { mutableStateOf(false) }
    var confirmationTarget by remember { mutableStateOf<JsonRecord?>(null) }
    var investmentHistory by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JsonRecord?>(null) }
    var editor by remember { mutableStateOf<JsonRecord?>(null) }
    var equityEditor by remember { mutableStateOf<JsonRecord?>(null) }
    var simulator by rememberSaveable { mutableStateOf(false) }
    var legalRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var legalHistory by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var education by rememberSaveable { mutableStateOf(false) }
    var importReview by remember { mutableStateOf<JsonRecord?>(null) }
    val actions = LocalNativeActions.current
    val engine = rememberInvestmentEngine()
    DisposableEffect(engine) { onDispose { engine.destroy() } }
    var newPurchase by rememberSaveable { mutableStateOf(false) }
    val preferences = rememberServerSettings()
    var introductionRequested by rememberSaveable { mutableStateOf(!preferences.readOnboardingCompleted() && !model.locked) }
    var replayIntroduction by rememberSaveable { mutableStateOf(false) }
    fun finishIntroduction() {
        preferences.writeOnboardingCompleted(true)
        introductionRequested = false
        replayIntroduction = false
    }
    LaunchedEffect(model.user) {
        // Existing device-protected sessions can bypass the tour at unlock. A session arriving
        // while the user is reading it must not interrupt the tour or mark it completed.
        if (model.user != null && !introductionRequested && !replayIntroduction) finishIntroduction()
    }
    val systemDark = isSystemInDarkTheme()
    var theme by rememberSaveable { mutableStateOf(preferences.readTheme()?.takeIf { it in listOf("system", "light", "dark") } ?: "system") }
    var chartStyle by rememberSaveable { mutableStateOf(preferences.readChartStyle()?.takeIf { it in listOf("area", "line", "bars") } ?: "area") }
    RefreshCapturedUi(tab, AppLanguage.code, theme, chartStyle, legalRoute)
    val dark = when (theme) { "light" -> false; "dark" -> true; else -> systemDark }
    LaunchedEffect(model.user, model.locked, NativePushBridge.registration, NativePushBridge.allowed, AppLanguage.code) {
        model.syncPushDevice()
    }
    LaunchedEffect(NativePushBridge.inboxRequest, model.user, model.locked) {
        if(NativePushBridge.inboxRequest>handledInboxRequest && model.user!=null && !model.locked) {
            tab="Stocks";notificationScreen=true;handledInboxRequest=NativePushBridge.inboxRequest
        }
    }
    LaunchedEffect(NativePushBridge.updateRevision) { if(model.user!=null && !model.locked)model.refreshNotifications() }
    LaunchedEffect(tab,model.user,model.locked) { if(tab=="Stocks" && model.user!=null && !model.locked)model.refreshNotifications() }
    LaunchedEffect(model.user) {
        if (model.user == null) { notificationPurchase=null;notificationScreen=false; missingDocuments = false; pendingIssueId = null; pendingEntry = false; confirmationTarget = null; calendarFocus = null; investmentHistory = false; tab = "Portfolio"; detail = null; editor = null; equityEditor = null; importReview = null; newPurchase = false; simulator = false; education = false }
    }
    LaunchedEffect(model, model.messageRevision) {
        val revision = model.messageRevision
        if (model.message != null) {
            kotlinx.coroutines.delay(3_000)
            if (model.messageRevision == revision) model.message = null
        }
    }
    LaunchedEffect(model, model.errorRevision) {
        val revision = model.errorRevision
        if (model.error != null) {
            kotlinx.coroutines.delay(5_000)
            if (model.errorRevision == revision) model.error = null
        }
    }
    fun imported(review: JsonRecord) {
        importReview = review
        missingDocuments = false
        editor = null; equityEditor = null; newPurchase = false; pendingEntry = false
    }
    fun uploadPendingPurchase(purchase: JsonRecord) = model.importPurchasePdf(actions, engine, bondOnly = true) { review ->
        confirmationTarget = purchase
        imported(review)
    }
    LaunchedEffect(notificationPurchase,model.busy) {
        val purchase=notificationPurchase
        if(purchase!=null && !model.busy) { notificationPurchase=null;uploadPendingPurchase(purchase) }
    }
    fun uploadMissing(item: JsonRecord) = model.importPurchasePdf(actions, engine, bondOnly = item.text("kind") == "bond", expectedStatement = item) { review ->
        confirmationTarget = null
        imported(review)
    }
    val colors = if (dark) darkColorScheme(primary = BrandCream, onPrimary = Ink, primaryContainer = Color(0xFF514716), onPrimaryContainer = BrandCream, secondary = BrandCream, secondaryContainer = Color(0xFF514716), onSecondaryContainer = BrandCream, background = Color(0xFF11150F), surface = Color(0xFF1D2219), onSurface = Color(0xFFF3F1E8), onSurfaceVariant = Color(0xFFC5C7BA), outlineVariant = Color(0xFF414735))
        else lightColorScheme(primary = BrandOlive, onPrimary = Color.White, primaryContainer = BrandCream, onPrimaryContainer = Ink, secondary = BrandOlive, secondaryContainer = BrandCream, onSecondaryContainer = Ink, background = Color(0xFFFAF8F2), surface = Color.White, onSurface = Ink)
    CompositionLocalProvider(LocalChartStyle provides chartStyle, LocalNumbersHidden provides model.numbersConcealed) {
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        if (shouldShowIntroduction(introductionRequested, model.locked)) {
            PlatformSystemBarAppearance(dark)
            OnboardingScreen(replayIntroduction, ::finishIntroduction)
            return@MaterialTheme
        }
        val nested = notificationScreen || legalRoute != null || missingDocuments || pendingEntry || importReview != null || investmentHistory || detail != null || editor != null || equityEditor != null || newPurchase || simulator || education
        val back = {
            if (notificationScreen) { notificationScreen=false } else if (legalRoute != null) {
                legalRoute = legalHistory.lastOrNull()
                legalHistory = legalHistory.dropLast(1)
            } else {
                if (detail == null && editor == null && equityEditor == null && importReview == null && !pendingEntry && !newPurchase && !simulator && !education) investmentHistory = false
                missingDocuments = false; detail = null; editor = null; equityEditor = null; newPurchase = false; pendingEntry = false; confirmationTarget = null; importReview = null; simulator = false; education = false
            }
        }
        val screenTitle = when {
            notificationScreen -> "Notifications"
            legalRoute != null -> legalTitle(legalRoute!!)
            missingDocuments -> "Missing documents"
            importReview?.text("kind") == "bond-issuance" -> "Review issuance"
            importReview?.text("kind") == "statement" -> "Statement review"
            pendingEntry -> "Pending purchase"
            importReview != null -> "Review purchase"
            newPurchase -> "Add purchase"
            editor != null -> if (importReview != null) "Review bond" else if (editor!!.text("id").isBlank()) "Add bond" else "Edit bond"
            equityEditor != null -> if (importReview != null) "Review shares" else "Add shares"
            simulator -> "Simulator"
            education -> "Learn"
            detail != null -> "Bond details"
            investmentHistory -> "All investments"
            model.user == null -> "Struct Capital"
            tab == "Settings" -> "Settings"
            else -> tab
        }
        val portfolioHome = model.user != null && !model.locked && !nested && tab == "Portfolio"
        model.pdfImportProgress?.takeIf { model.user != null && !model.locked }?.let { progress ->
            PdfImportProgressDialog(progress, model::cancelPdfImport)
        }
        PlatformSystemBarAppearance(dark)
        PlatformBackHandler(nested, back)
        Scaffold(
            contentWindowInsets = if (portfolioHome) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (!portfolioHome && (model.user != null || model.locked || nested)) TopAppBar(
                    title = {
                        Column {
                            AppText(screenTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        if (nested) IconButton(onClick = back) { AppIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        else StructMark(Modifier.padding(start = 16.dp, end = 4.dp).size(40.dp))
                    },
                    actions = { if(tab=="Stocks" && !nested && model.user!=null && !model.locked)
                        NotificationBell(model.unreadNotifications) { notificationScreen=true } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            floatingActionButton = {
                if (model.user != null && !model.locked && !nested && tab == "Portfolio") ExtendedFloatingActionButton(
                    onClick = { importReview = null; confirmationTarget = null; newPurchase = true },
                    modifier = Modifier.semantics { contentDescription = localized("Add purchase") },
                    icon = { AppIcon(Icons.Filled.Add, null) }, text = { AppText("Add purchase") }
                )
            },
            bottomBar = {
                if (model.user != null && !model.locked && !nested) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEach { destination ->
                        val selected = tab == destination.title
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) { tab = destination.title; selectionFeedback() } },
                            icon = { BadgedBox(badge={ if(destination.title=="Stocks" && model.unreadNotifications>0) Badge {
                                AppText(if(model.unreadNotifications>99) "99+" else model.unreadNotifications.toString())
                            } }) { AppIcon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null, modifier = Modifier.size(24.dp)) } },
                            label = { AppText(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().portfolioStatusBarFade(portfolioHome)) {
                InvestmentEngineHost(engine)
                if (!portfolioHome) {
                    AppLoadingLine(model.busy)
                    model.error?.let { Notice(it, true) { model.error = null } }
                    model.message?.let { Notice(it, false) { model.message = null } }
                }
                when {
                    legalRoute != null -> LegalScreen(legalRoute!!) { next ->
                        legalHistory = legalHistory + legalRoute!!
                        legalRoute = next
                    }
                    model.locked -> UnlockScreen(model) { legalRoute = "hub" }
                    model.user == null -> SignInScreen(model) { legalRoute = "hub" }
                    notificationScreen -> NotificationsScreen(model,openStocks={ notificationScreen=false;tab="Stocks" },openNotification={ item ->
                        when(item.text("action")) {
                            "calendar","coupon" -> { calendarFocus=item.text("dueDate");notificationScreen=false;tab="Calendar" }
                            "purchase" -> model.action { val result=model.api.request("/api/bonds/purchases/${item.text("purchaseId")}");notificationScreen=false;notificationPurchase=result.getJSONObject("purchase") }
                            "documents" -> { notificationScreen=false;missingDocuments=true }
                            else -> { notificationScreen=false;tab="Settings" }
                        }
                    })
                    missingDocuments && importReview == null -> MissingDocumentsScreen(model, ::uploadMissing)
                    pendingEntry -> PendingBondScreen(model, pendingIssueId) { pendingEntry = false; pendingIssueId = null }
                    importReview?.text("kind") == "statement" -> StatementReviewScreen(model, importReview!!, ::uploadMissing)
                    importReview?.text("kind") == "bond-issuance" -> BondIssuanceReview(model, importReview!!, confirmationTarget) { importReview = null; confirmationTarget = null; detail = null }
                    importReview != null -> PurchaseImportReview(model, importReview!!, confirmationTarget) { importReview = null; confirmationTarget = null; detail = null }
                    newPurchase -> PurchaseEntryScreen(model, engine, ::imported) { legalRoute = "privacy" }
                    tab == "Settings" && !nested -> SettingsScreen(model, theme, changeTheme = { value -> theme = value; preferences.writeTheme(value) },
                        chartStyle = chartStyle, changeChartStyle = { value -> chartStyle = value; preferences.writeChartStyle(value) },
                        openSimulator = { simulator = true }, openEducation = { education = true }, openBond = { detail = it }, openLegal = { legalRoute = "hub" }, openAbout = { legalRoute = "about" }, openIntroduction = { replayIntroduction = true; introductionRequested = true })
                    editor != null -> PurchaseEditor(model, editor!!, { editor = null; importReview = null }, importReview)
                    equityEditor != null -> EquityEditor(model, equityEditor!!, { equityEditor = null; importReview = null }, importReview)
                    simulator -> SimulatorScreen(model, engine) { simulator = false }
                    education -> EducationScreen(model) { education = false }
                    detail != null -> PurchaseDetails(model, detail!!, engine, { confirmationTarget = detail; imported(it) }, { detail = null }, { editor = detail; detail = null })
                    investmentHistory -> InvestmentHistoryScreen(model, uploadPending = ::uploadPendingPurchase) { detail = it }
                    tab == "Portfolio" -> PortfolioScreen(model, uploadPending = ::uploadPendingPurchase, openMissing = ::uploadMissing, allMissing = { missingDocuments = true }, seeAll = { investmentHistory = true },
                        openCalendar = { date -> calendarFocus = date.toString(); tab = "Calendar" }, detail = { importReview = null; detail = it })
                    tab == "Calendar" -> CalendarScreen(model, initialDate = calendarFocus?.let(::calendarDate), addPending = { issue, date ->
                        pendingIssueId = issue.text("id"); calendarFocus = date.toString(); confirmationTarget = null; pendingEntry = true
                    })
                    tab == "Documents" -> DocumentsScreen(model) { model.importPurchasePdf(actions, engine, completed = ::imported) }
                    tab == "Stocks" -> StocksScreen(model)
                }
            }
        }
    }
    }
}

@Composable
internal fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Surface(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FinancialText(text, Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = dismiss) { AppText("Dismiss") }
        }
    }
}

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        AppText(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AppText(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
        AppText(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FinancialText(displayRecordValue(value).ifBlank { "—" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}


@Composable
private fun UnlockScreen(model: PortfolioModel, openLegal: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        StructMark(Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        AppText("Portfolio locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        AppText("Your saved sign-in is protected on this device.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = model::unlock, enabled = !model.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            AppIcon(when (model.unlockLabel) { "Face ID" -> Icons.Outlined.Face; "Touch ID" -> Icons.Outlined.Fingerprint; else -> Icons.Outlined.Lock }, null); Spacer(Modifier.width(8.dp))
            AppText(if (model.busy) "Unlocking…" else "Unlock with ${localized(model.unlockLabel)}")
        }
        TextButton(onClick = model::usePassword, enabled = !model.busy) { AppText("Use password instead") }
        TextButton(onClick = openLegal) { AppText("Legal & privacy") }
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, helper: String? = null, autofill: ContentType? = null, enabled: Boolean = true) {
    if (keyboard in listOf(KeyboardType.Number, KeyboardType.Decimal) && LocalCaptureBackdropRegistry.current != null && !LocalNumbersHidden.current) {
        CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = {
            OutlinedTextField("••••", {}, Modifier.fillMaxWidth(), enabled = false, singleLine = true,
                label = { AppText(label) }, supportingText = helper?.let { { AppText(it) } },
                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline))
        }) { FieldContent(label, value, change, keyboard, helper, autofill, enabled) }
    } else FieldContent(label, value, change, keyboard, helper, autofill, enabled)
}

@Composable
private fun FieldContent(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType, helper: String?, autofill: ContentType?, enabled: Boolean) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, change, Modifier.fillMaxWidth().then(if (autofill != null) Modifier.semantics { contentType = autofill } else Modifier), label = { AppText(label) }, singleLine = true, enabled = enabled,
        visualTransformation = if (LocalNumbersHidden.current && keyboard in listOf(KeyboardType.Number, KeyboardType.Decimal)) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }, onNext = { focus.moveFocus(FocusDirection.Next) }),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (autofill == ContentType.Username) ImeAction.Next else ImeAction.Done, platformImeOptions = if (autofill == ContentType.Username) credentialImeOptions(CredentialInput.USERNAME) else null), supportingText = helper?.let { { AppText(it) } })
}

@Composable
fun ChoiceRow(choices: List<String>, selected: String, choose: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { item -> FilterChip(selected = item == selected, onClick = { choose(item) }, label = { AppText(item.replaceFirstChar { it.uppercase() }) }) }
    }
}

@Composable
private fun PurchaseDetails(model: PortfolioModel, purchase: JsonRecord, engine: InvestmentEngine, imported: (JsonRecord) -> Unit, back: () -> Unit, edit: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { AppText("Delete purchase?") }, text = { AppText("This removes ${purchase.text("bondName")} from your synchronized portfolio.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { model.deletePurchase(purchase.text("id"), back); confirmDelete = false }) { AppText("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { AppText("Keep purchase") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { TextButton(onClick = back) { AppText("← Back") }; PageTitle("Purchase details", purchase.text("bondName"), purchase.text("isin")) }
        item { Panel {
            if (purchase.text("status") == "submitted") PendingPurchaseFacts(purchase) else BondPurchaseFacts(purchase)
        } }
        if (needsPersonalConfirmation(purchase)) item {
            PendingConfirmationAction(purchase,!model.busy) { model.confirmPendingPurchase(purchase,back) }
            PdfImportButton(model, engine, imported, bondOnly = true)
        }
        if (purchase.text("status") != "submitted") purchase.terms()?.let { terms ->
            val tracking = trackBond(terms, Today)
            item { Panel {
                AppText("Coupon tracking", fontWeight = FontWeight.Bold)
                Fact("Net per payment", money(tracking.netPerPayment, purchase.text("currency")))
                Fact("Scheduled to date", money(tracking.couponsPaid, purchase.text("currency")))
                Fact("Confirmed received",if(model.couponTrackingAvailable)money(model.couponReceipts.filter { it.text("purchaseId")==purchase.text("id") && it.optBoolean("received") }.sumOf { it.optDouble("amount") },purchase.text("currency")) else "—")
                Fact("Accrued", money(tracking.accruedCoupon, purchase.text("currency")))
                Fact("Remaining coupons", money(tracking.remainingCoupons, purchase.text("currency")))
                AppText("${purchase.text("scheduleConfidence")} schedule · as of $Today", style = MaterialTheme.typography.bodySmall)
            } }
            items(tracking.schedule) { date -> Panel {
                val receipt=model.couponReceipt(purchase.text("id"),date.toString())
                val received=receipt?.optBoolean("received")==true
                Fact(date.toString(),money(tracking.netPerPayment,purchase.text("currency")))
                AppText(if(received) { if(receipt?.text("method")=="automatic") "Marked automatically" else "Confirmed by you" }
                    else if(date<=model.notificationDate) "Not confirmed received" else "Upcoming",style=MaterialTheme.typography.bodySmall)
                if(date<=model.notificationDate && model.couponTrackingAvailable) TextButton(enabled=!model.busy,onClick={ model.confirmCoupon(purchase.text("id"),date.toString(),!received) }) {
                    AppText(if(received) "Mark not received" else "Confirm received")
                }
            } }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (purchase.text("status") != "submitted") Button(onClick = edit, enabled = !model.busy) { AppText("Edit purchase") }
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !model.busy) { AppText("Delete") }
        } }
    }
}
