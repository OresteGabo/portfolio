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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
fun structCapitalColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(primary = BrandCream, onPrimary = Ink, primaryContainer = Color(0xFF514716), onPrimaryContainer = BrandCream, secondary = BrandCream, secondaryContainer = Color(0xFF514716), onSecondaryContainer = BrandCream, background = Color(0xFF11150F), surface = Color(0xFF1D2219), onSurface = Color(0xFFF3F1E8), onSurfaceVariant = Color(0xFFC5C7BA), outlineVariant = Color(0xFF414735))
        else lightColorScheme(primary = BrandOlive, onPrimary = Color.White, primaryContainer = BrandCream, onPrimaryContainer = Ink, secondary = BrandOlive, secondaryContainer = BrandCream, onSecondaryContainer = Ink, background = Color(0xFFFAF8F2), surface = Color.White, onSurface = Ink)

// Keep saved route keys independent of user-facing labels.
private data class Destination(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)
private val destinations = listOf(
    Destination("Portfolio", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Destination("Calendar", "Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Destination("Documents", "Documents", Icons.Outlined.Description, Icons.Filled.Description),
    Destination("Stocks", "Markets", Icons.AutoMirrored.Outlined.TrendingUp, Icons.AutoMirrored.Filled.TrendingUp),
    Destination("Settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentApp(model: PortfolioModel) {
    val selectionFeedback = LocalSelectionFeedback.current
    var notificationPurchase by remember { mutableStateOf<JsonRecord?>(null) }
    var notificationPanel by rememberSaveable { mutableStateOf<String?>(null) }
    var securityNotificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var handledInboxRequest by rememberSaveable { mutableIntStateOf(0) }
    var showGuestChoice by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("Portfolio") }
    var documentSection by rememberSaveable(model.user?.text("id")) { mutableStateOf("Library") }
    var couponList by rememberSaveable { mutableStateOf(false) }
    var calendarFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingIssueId by rememberSaveable { mutableStateOf<String?>(null) }
    var missingDocuments by rememberSaveable { mutableStateOf(false) }
    var pendingEntry by rememberSaveable { mutableStateOf(false) }
    var confirmationTarget by remember { mutableStateOf<JsonRecord?>(null) }
    var investmentHistory by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JsonRecord?>(null) }
    var documentDetail by remember { mutableStateOf<JsonRecord?>(null) }
    var shareDetail by remember { mutableStateOf<JsonRecord?>(null) }
    var editor by remember { mutableStateOf<JsonRecord?>(null) }
    var equityEditor by remember { mutableStateOf<JsonRecord?>(null) }
    var investmentTools by rememberSaveable { mutableStateOf(false) }
    var simulator by rememberSaveable { mutableStateOf(false) }
    var legalRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var legalHistory by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var education by rememberSaveable { mutableStateOf(false) }
    var importReview by remember(model) { mutableStateOf(model.currentImportReview()) }
    val actions = LocalNativeActions.current
    val engine = LocalImportEngine.current ?: if (model.isGuest) remember { GuestUnavailableEngine() } else rememberInvestmentEngine()
    DisposableEffect(engine) { onDispose { engine.destroy() } }
    val documentUi = rememberSaveableStateHolder()
    val calendarUi = rememberSaveableStateHolder()
    val calendarStateKey = "calendar:${model.origin}|${model.user?.text("id")}"
    val couponsStateKey = "coupons:${model.origin}|${model.user?.text("id")}"
    fun openCalendarAt(date: kotlinx.datetime.LocalDate?) {
        calendarUi.removeState(calendarStateKey)
        calendarFocus = date?.toString(); tab = "Calendar"
    }
    val preferences = rememberServerSettings()
    var showImportHint by rememberSaveable { mutableStateOf(!preferences.readImportHintDismissed()) }
    var introductionRequested by rememberSaveable { mutableStateOf(!preferences.readOnboardingCompleted() && !model.locked && !model.isGuest) }
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
    fun openNotificationInbox() {
        tab = "Notifications"; notificationPanel = null; securityNotificationId = null
        legalRoute = null; legalHistory = emptyList(); missingDocuments = false; pendingEntry = false
        couponList = false; investmentHistory = false; documentDetail = null; detail = null; shareDetail = null; editor = null; equityEditor = null
        simulator = false; investmentTools = false; education = false
        model.closeDocumentPreview()
    }
    val systemDark = isSystemInDarkTheme()
    var theme by rememberSaveable { mutableStateOf(preferences.readTheme()?.takeIf { it in listOf("system", "light", "dark") } ?: "system") }
    var chartStyle by rememberSaveable { mutableStateOf(preferences.readChartStyle()?.takeIf { it in listOf("area", "line", "bars") } ?: "area") }
    RefreshCapturedUi(tab, AppLanguage.code, theme, chartStyle, legalRoute)
    val dark = when (theme) { "light" -> false; "dark" -> true; else -> systemDark }
    LaunchedEffect(model.user, model.locked) {
        if (model.user != null && !model.locked) {
            withFrameNanos { }
            model.dashboardPresented()
        }
    }
    LaunchedEffect(model.user, model.locked, NativePushBridge.registration, NativePushBridge.allowed, AppLanguage.code) {
        model.syncPushDevice()
    }
    LaunchedEffect(NativePushBridge.inboxRequest, model.user, model.locked) {
        if(NativePushBridge.inboxRequest>handledInboxRequest && model.user!=null && !model.locked) {
            openNotificationInbox();handledInboxRequest=NativePushBridge.inboxRequest
        }
    }
    LaunchedEffect(NativePushBridge.updateRevision) { if(model.user!=null && !model.locked)model.refreshNotifications() }
    LaunchedEffect(tab,model.user,model.locked) { if(tab=="Notifications" && model.user!=null && !model.locked)model.refreshNotifications() }
    LaunchedEffect(model.user, model.sessionState) {
        if (model.user == null) { couponList=false; documentDetail=null; notificationPurchase=null;notificationPanel=null;securityNotificationId=null; missingDocuments = false; pendingIssueId = null; pendingEntry = false; confirmationTarget = null; calendarFocus = null; investmentHistory = false; tab = "Portfolio"; detail = null; shareDetail = null; editor = null; equityEditor = null; importReview = null; simulator = false; investmentTools = false; education = false }
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
    fun showImportConversation() {
        tab = "Documents"; documentSection = "Import"
        notificationPanel = null; missingDocuments = false; investmentHistory = false
        couponList = false; documentDetail = null; editor = null; equityEditor = null; detail = null; shareDetail = null; pendingEntry = false
        simulator = false; education = false
    }
    fun imported(review: JsonRecord) {
        importReview = review
        showImportConversation()
    }
    fun beginDocumentImport() {
        confirmationTarget = null
        model.importPurchasePdf(actions, engine, completed = ::imported)
    }
    var handledImportSelection by remember(model) { mutableIntStateOf(model.importSelectionRevision) }
    LaunchedEffect(model.importSelectionRevision) {
        if (model.importSelectionRevision != handledImportSelection) {
            handledImportSelection = model.importSelectionRevision
            showImportConversation()
        }
    }
    LaunchedEffect(model.activeImportReceipt()?.state, importReview) {
        if (model.activeImportReceipt()?.state in listOf(ImportState.SAVED, ImportState.EXISTS) && importReview != null) {
            model.discardImportedPdf(importReview!!); importReview = null; confirmationTarget = null
        }
    }
    fun uploadPendingPurchase(purchase: JsonRecord) = model.importPurchasePdf(actions, engine, bondOnly = true, targetPurchaseId = purchase.text("id")) { review ->
        confirmationTarget = purchase.takeUnless { review.text("kind") == "statement" }
        imported(review)
    }
    val incomingPdf = NativePdfInbox.pending
    LaunchedEffect(incomingPdf?.id, model.user?.text("id"), model.locked, model.busy,
        introductionRequested, importReview, editor, equityEditor, pendingEntry, legalRoute, model.documentPreview) {
        if (incomingPdf != null && model.user != null && !model.locked && !model.busy &&
            !introductionRequested && model.documentPreview == null && importReview == null && editor == null && equityEditor == null && !pendingEntry && legalRoute == null) {
            NativePdfInbox.take(incomingPdf.id)?.let { received ->
                confirmationTarget = null
                model.importPurchasePdf(ReceivedPdfActions(actions, received.bytes, received.fileName), engine) { review ->
                    notificationPanel = null
                    imported(review)
                }
            }
        }
    }
    LaunchedEffect(notificationPurchase,model.busy) {
        val purchase=notificationPurchase
        if(purchase!=null && !model.busy) {
            notificationPurchase=null
            if (purchase.text("status") == "submitted") uploadPendingPurchase(purchase) else detail = purchase
        }
    }
    fun uploadMissing(item: JsonRecord) = model.importPurchasePdf(actions, engine, bondOnly = item.text("kind") == "bond", expectedStatement = item) { review ->
        confirmationTarget = null
        imported(review)
    }
    val colors = structCapitalColorScheme(dark)
    CompositionLocalProvider(LocalChartStyle provides chartStyle, LocalNumbersHidden provides model.numbersConcealed) {
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        if (showGuestChoice) GuestEntryDialog(close = { showGuestChoice = false }, choose = { path ->
            if (model.enterGuest(path)) { showGuestChoice = false; finishIntroduction(); tab = "Portfolio" }
        })
        if (shouldShowIntroduction(introductionRequested, model.locked)) {
            PlatformSystemBarAppearance(dark)
            OnboardingScreen(replay = replayIntroduction,
                continueAsGuest = if (model.user == null) ({ showGuestChoice = true }) else null,
                finish = { finishIntroduction(); model.startAccountSession() })
            return@MaterialTheme
        }
        val childScreen = couponList || documentDetail != null || model.documentPreview != null || notificationPanel == "security" || legalRoute != null || missingDocuments || pendingEntry || investmentHistory || detail != null || shareDetail != null || editor != null || equityEditor != null || simulator || investmentTools || education
        val nested = childScreen || tab == "Notifications"
        val documentsHome = model.user != null && !model.locked && !nested && tab == "Documents"
        val importChat = documentsHome && documentSection == "Import"
        val back = {
            if (model.documentPreview != null) { model.closeDocumentPreview() } else if (importChat) { documentSection = "Library" } else if (notificationPanel == "security") { notificationPanel=null;securityNotificationId=null } else if (legalRoute != null) {
                legalRoute = legalHistory.lastOrNull()
                legalHistory = legalHistory.dropLast(1)
            } else if (couponList && detail == null && shareDetail == null && editor == null && equityEditor == null && model.documentPreview == null) { couponList = false
            } else if (documentDetail != null && detail == null && shareDetail == null && editor == null && equityEditor == null) { documentDetail = null
            } else if (tab == "Notifications" && !childScreen) { tab = "Portfolio" } else {
                if (detail == null && shareDetail == null && editor == null && equityEditor == null && importReview == null && !pendingEntry && !simulator && !investmentTools && !education) investmentHistory = false
                missingDocuments = false; detail = null; shareDetail = null; editor = null; equityEditor = null; pendingEntry = false; simulator = false; investmentTools = false; education = false
            }
        }
        if (model.user != null && !model.locked) {
            StatementReconciliationDialog(model, ::uploadMissing)
            when (notificationPanel) {
                "report" -> PortfolioReportDialog(model) { notificationPanel = null }
                "preferences" -> NotificationPreferencesDialog(model) { notificationPanel = null }
            }
            if (tab != "Settings") PortfolioReportProgressDialog(model)
        }
        val screenTitle = when {
            model.documentPreview != null -> model.documentPreview!!.title
            notificationPanel == "security" -> "Sign-in activity"
            legalRoute != null -> legalTitle(legalRoute!!)
            missingDocuments -> "Missing documents"
            pendingEntry -> "Pending purchase"
            editor != null -> if (editor!!.text("id").isBlank()) "Add bond" else "Edit bond"
            equityEditor != null -> "Add shares"
            investmentTools -> "Investment outlook"
            simulator -> "Simulator"
            education -> "Learn"
            detail != null -> "Bond details"
            shareDetail != null -> "Share details"
            documentDetail != null -> "Document details"
            couponList -> "Coupons"
            investmentHistory -> "All investments"
            model.user == null && !model.isGuest -> "Struct Capital"
            tab == "Settings" -> "Settings"
            else -> destinations.firstOrNull { it.key == tab }?.label ?: tab
        }
        val portfolioHome = model.user != null && !model.locked && !nested && tab == "Portfolio"
        model.pdfImportProgress?.takeIf { model.user != null && !model.locked && model.activeImportId == null }?.let { progress ->
            PdfImportProgressDialog(progress, model::cancelPdfImport)
        }
        PlatformSystemBarAppearance(dark)
        PlatformBackHandler(nested || importChat, back)
        // Financial editors stay in this protected scene and must draw after the scaffold.
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = if (portfolioHome) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (!portfolioHome && (model.user != null || model.isGuest || model.locked || nested)) TopAppBar(
                    title = {
                        if (documentsHome) DocumentHeader(documentSection, model.user?.text("email").orEmpty())
                        else if (model.documentPreview != null) FinancialText(screenTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else AppText(screenTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        if (nested || importChat) IconButton(onClick = back) { AppIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        else StructMark(Modifier.padding(start = 16.dp, end = 4.dp).size(40.dp))
                    },
                    actions = {
                        if (model.documentPreview?.source?.text("category") == "Statement" && !model.locked) IconButton(enabled = !model.busy, onClick = {
                            model.showStatementDocumentReview(model.documentPreview!!.source.text("id"))
                        }) { AppIcon(Icons.Outlined.FactCheck, "Review statement records") }
                        if (model.documentPreview?.bytes != null) IconButton(enabled = !model.busy, onClick = {
                            val preview = model.documentPreview!!
                            model.action { actions.sharePdf(preview.fileName, preview.bytes!!) }
                        }) { AppIcon(Icons.Outlined.Share, "Share PDF") }
                        else if (documentsHome) {
                            TextButton(onClick = {
                                documentSection = if (documentSection == "Import") "Library" else "Import"
                                selectionFeedback()
                            }) { AppText(if (documentSection == "Import") "Library" else "Chat", style = MaterialTheme.typography.labelLarge) }
                        }
                        else if (tab == "Calendar" && !childScreen && model.user != null && !model.locked) {
                            TextButton(modifier = Modifier.semantics { contentDescription = localized("View yearly coupons") }, onClick = { calendarUi.removeState(couponsStateKey); couponList = true }) { AppText("Coupons", style = MaterialTheme.typography.labelLarge) }
                        }
                        else if (tab == "Notifications" && !childScreen && model.user != null && !model.locked) {
                            IconButton(onClick = { notificationPanel = "preferences" }) { AppIcon(Icons.Outlined.Tune, "Notification preferences") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            floatingActionButton = {
                if (documentsHome && documentSection == "Library") ExtendedFloatingActionButton(
                    onClick = {
                        if (importReview != null || model.activeImportReceipt()?.state in listOf(ImportState.READING, ImportState.SAVING)) {
                            showImportConversation()
                        } else beginDocumentImport()
                    },
                    modifier = Modifier.semantics { contentDescription = localized("Add document") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { AppIcon(Icons.Filled.Add, null) }, text = { AppText("Add document") }
                )
                else if (model.user != null && !model.locked && !nested && tab == "Portfolio") ExtendedFloatingActionButton(
                    onClick = {
                        if (importReview != null || model.activeImportReceipt()?.state in listOf(ImportState.READING, ImportState.SAVING)) {
                            tab = "Documents"; documentSection = "Import"
                        } else beginDocumentImport()
                    },
                    modifier = Modifier.semantics { contentDescription = localized("Add purchase") },
                    icon = { AppIcon(Icons.Filled.Add, null) }, text = { AppText("Add purchase") }
                )
            },
            bottomBar = {
                if ((model.user != null || model.isGuest) && !model.locked && !nested && !importChat) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEach { destination ->
                        val selected = tab == destination.key
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) {
                                tab = destination.key
                                if (destination.key == "Documents") documentSection = "Library"
                                if (destination.key == "Calendar") openCalendarAt(null)
                                selectionFeedback()
                            } },
                            icon = { AppIcon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = { AppText(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().portfolioStatusBarFade(portfolioHome)) {
                if (!model.locked && !model.isGuest) InvestmentEngineHost(engine)
                if (!portfolioHome) {
                    AppLoadingLine(model.busy)
                    if (model.documentPreview == null || model.documentPreview?.bytes != null) model.error?.let { Notice(it, true) { model.error = null } }
                    model.message?.let { Notice(it, false) { model.message = null } }
                }
                NativePdfInbox.error?.let { Notice(it, true, NativePdfInbox::dismissError) }
                if (NativePdfInbox.pending != null) Notice(
                    if (model.user == null || model.locked) "PDF received. Sign in or unlock to review and import it."
                    else "PDF received. Finish your current action to review and import it.", false, NativePdfInbox::dismiss)
                when {
                    model.documentPreview != null && model.user != null && !model.locked -> PdfViewerScreen(model, model.documentPreview!!)
                    legalRoute != null -> LegalScreen(legalRoute!!) { next ->
                        legalHistory = legalHistory + legalRoute!!
                        legalRoute = next
                    }
                    model.isGuest -> GuestContent(model, tab, choosePath = { showGuestChoice = true }, signIn = { model.beginAccountSignIn() })
                    model.locked -> UnlockScreen(model, continueAsGuest = { showGuestChoice = true }) { legalRoute = "hub" }
                    model.user == null -> SignInScreen(model, continueAsGuest = { showGuestChoice = true }) { legalRoute = "hub" }
                    notificationPanel == "security" -> SignInActivityScreen(model, securityNotificationId)
                    tab == "Notifications" && !childScreen -> NotificationsScreen(model, openPortfolio = { tab = "Portfolio" }, openNotification = { item ->
                        when (notificationDestination(item)) {
                            NotificationDestination.CALENDAR -> openCalendarAt(calendarDate(item.text("dueDate")))
                            NotificationDestination.PURCHASE -> model.action {
                                val result = model.api.request("/api/bonds/purchases/${item.text("purchaseId")}")
                                notificationPurchase = result.getJSONObject("purchase")
                            }
                            NotificationDestination.DOCUMENT -> model.action {
                                val row = model.api.request("/api/bonds/statement-items").rows("items").firstOrNull { it.text("id") == item.text("statementId") }
                                if (row == null) model.message = "This document reminder has already been resolved."
                                else model.showStatementRecordReview(row)
                            }
                            NotificationDestination.ISSUANCE -> model.openDocumentPreview(JsonRecord().put("id",item.text("documentId"))
                                .put("downloadUrl","/api/bonds/documents/${item.text("documentId")}").put("label","Bond issuance results"))
                            NotificationDestination.SECURITY -> { securityNotificationId = item.text("id"); notificationPanel = "security" }
                            NotificationDestination.TARGET -> notificationPanel = "target"
                            NotificationDestination.REPORT -> notificationPanel = "report"
                            null -> Unit
                        }
                    })
                    missingDocuments -> MissingDocumentsScreen(model, ::uploadMissing)
                    pendingEntry -> PendingBondScreen(model, pendingIssueId) { pendingEntry = false; pendingIssueId = null }
                    tab == "Settings" && !childScreen -> SettingsScreen(model, theme, changeTheme = { value -> theme = value; preferences.writeTheme(value) },
                        chartStyle = chartStyle, changeChartStyle = { value -> chartStyle = value; preferences.writeChartStyle(value) },
                        openSimulator = { simulator = true }, openEducation = { education = true }, openBond = { detail = it }, openLegal = { legalRoute = "hub" }, openAbout = { legalRoute = "about" }, openIntroduction = { replayIntroduction = true; introductionRequested = true }, openInvestmentTools = { investmentTools = true }, openSignInActivity = { securityNotificationId = null; notificationPanel = "security" }, openGuest = { showGuestChoice = true })
                    editor != null -> PurchaseEditor(model, editor!!, { editor = null })
                    equityEditor != null -> EquityEditor(model, equityEditor!!, { equityEditor = null })
                    investmentTools -> InvestmentToolsScreen(model, openCalendar = { date -> investmentTools = false; openCalendarAt(date) },
                        openHistory = { investmentTools = false; investmentHistory = true })
                    simulator -> SimulatorScreen(model, engine) { simulator = false }
                    education -> EducationScreen(model) { education = false }
                    detail != null -> {
                        val purchase = model.purchases.firstOrNull { it.text("id") == detail!!.text("id") } ?: detail!!
                        BondDetailsScreen(model, purchase, engine, { confirmationTarget = purchase; imported(it) },
                            { detail = null }, { editor = purchase; detail = null })
                    }
                    shareDetail != null -> ShareDetailsScreen(model, shareDetail!!)
                    documentDetail != null -> DocumentDetailsScreen(model, documentDetail!!, openBond = { detail = it }, openShare = { shareDetail = it })
                    couponList -> calendarUi.SaveableStateProvider(couponsStateKey) { YearlyCouponsScreen(model, openBond = { detail = it }) }
                    investmentHistory -> InvestmentHistoryScreen(model, openShare = { shareDetail = it }) { detail = it }
                    tab == "Portfolio" -> PortfolioScreen(model, openNotifications = ::openNotificationInbox, unreadNotifications = model.unreadNotifications, openShare = { shareDetail = it }, uploadPending = ::uploadPendingPurchase, openMissing = ::uploadMissing, allMissing = { missingDocuments = true }, seeAll = { investmentHistory = true },
                        openCalendar = ::openCalendarAt, detail = { detail = it })
                    tab == "Calendar" -> calendarUi.SaveableStateProvider(calendarStateKey) { CalendarScreen(model, initialDate = calendarFocus?.let(::calendarDate), addPending = { issue, date ->
                        pendingIssueId = issue.text("id"); calendarFocus = date.toString(); confirmationTarget = null; pendingEntry = true
                    }) }
                    tab == "Documents" -> documentUi.SaveableStateProvider("documents:${model.origin}|${model.user?.text("id")}") { DocumentsHub(model, engine, importReview, confirmationTarget,
                        section = documentSection, changeSection = { documentSection = it },
                        showHint = showImportHint, dismissHint = { showImportHint = false; preferences.writeImportHintDismissed(true) },
                        beginImport = ::beginDocumentImport,
                        reviewed = ::imported, done = { importReview = null; confirmationTarget = null; detail = null },
                        openPurchase = { id -> model.action {
                            require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Check the purchase details." }
                            val row = model.purchases.firstOrNull { it.text("id") == id }
                                ?: model.api.request("/api/bonds/purchases/$id").optJSONObject("purchase")
                            require(row != null) { "Check the purchase details." }; detail = row
                        } }, openMissing = { missingDocuments = true }, openBond = { detail = it }, openShare = { shareDetail = it }, openDetails = { documentDetail = it }) }
                    tab == "Stocks" -> StocksScreen(model, openShare = { shareDetail = it })
                }
            }
        }
        if (model.user != null && !model.locked && notificationPanel == "target") {
            InvestmentTargetDialog(model) { notificationPanel = null }
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
