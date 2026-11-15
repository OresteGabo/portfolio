package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.features.documents.PortfolioStatements
import dev.orestegabo.portfolio.features.shares.PortfolioShareOrders
import dev.orestegabo.portfolio.features.transactions.PortfolioCashLedger
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.LEGACY_WEBSITE_API_ORIGIN
import dev.orestegabo.portfolio.network.PortfolioApi
import dev.orestegabo.portfolio.network.isLegacyWebsiteApiOrigin
import dev.orestegabo.portfolio.network.nativeBackendOrigin

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

class PortfolioModel(
    private val settings: ServerSettings,
    private val assets: suspend (String) -> ByteArray = ::readSharedAsset,
    private val vault: SecureSignIn? = null,
    private val apiFactory: (String) -> PortfolioApi = { PortfolioApi(it) },
    private val captureMaskRequired: Boolean? = null,
    private val clock: TimeSource = TimeSource.Monotonic,
    initialOrigin: String? = null,
    private val importWorkDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val reportWorkDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val deferAccountStartup: Boolean = false,
    private val guestRepository: GuestPortfolioRepository? = null,
    private val guestMarketData: GuestMarketData? = null,
) : ViewModel()
{
    private val savedOrigin = settings.readOrigin()
    private val savedEntryChoice = restoredSessionChoice(runCatching { settings.readSessionChoice() }.getOrDefault(MANUAL_ACCOUNT_ENTRY))
    private val backendOriginMigrated = initialOrigin == null && (isLegacyWebsiteApiOrigin(savedOrigin) ||
        savedOrigin == null && GuestPath.restore(savedEntryChoice) == null && vault?.hasSaved(LEGACY_WEBSITE_API_ORIGIN) == true)
    var origin by mutableStateOf(initialOrigin ?: nativeBackendOrigin(savedOrigin))
        private set
    // Never restore an old-host password or cookie into the new backend origin.
    private var entryChoice by mutableStateOf(if (backendOriginMigrated && GuestPath.restore(savedEntryChoice) == null) MANUAL_ACCOUNT_ENTRY else savedEntryChoice)
    private var accountEntry by mutableStateOf(false)
    val guestPath get() = GuestPath.restore(entryChoice)
    val isGuest get() = guestPath != null && !accountEntry
    val sessionState: SessionState get() = user?.let { SessionState.Authenticated(it.text("id"), origin) }
        ?: if (isGuest) SessionState.Guest(requireNotNull(guestPath)) else SessionState.SignedOut
    var guestPortfolio by mutableStateOf<GuestPortfolio?>(null)
        private set
    var guestStorageLoading by mutableStateOf(false)
        private set
    var guestStorageError by mutableStateOf<String?>(null)
        private set
    private var samplePortfolio by mutableStateOf(guestSamplePortfolio())
    var guestViewRevision by mutableIntStateOf(0); private set
    val sampleGuest get() = isGuest && guestPath == GuestPath.SAMPLE
    val canEditInvestments get() = !sampleGuest && !busy
    val canPublishPublicData get() = !isGuest && mayPublishSharedData(user)
    fun resetSample() { if (sampleGuest) { samplePortfolio = guestSamplePortfolio(); guestViewRevision++ } }
    fun deleteGuestData() {
        if (!isGuest || !canEditInvestments) return
        guestImport?.clear(); closeDocumentPreview(); documentThumbnails.clear()
        guestWrite({ guestPortfolio = null; requireNotNull(guestRepository).deleteAll() }) { guestViewRevision++ }
    }
    private var guestJob: Job? = null
    var guestQuotes by mutableStateOf<GuestQuoteSnapshot?>(null); private set
    var guestQuotesRefreshing by mutableStateOf(false); private set
    var guestQuotesFailed by mutableStateOf(false); private set
    val guestQuotesEnabled get() = guestMarketData?.enabled == true
    private var guestQuoteJob: Job? = null
    fun refreshGuestQuotes() {
        if (!isGuest || guestPath != GuestPath.PERSONAL || !guestQuotesEnabled || guestQuoteJob?.isActive == true) return
        val token = generation
        guestQuotesRefreshing = true
        guestQuoteJob = viewModelScope.launch {
            fun current() = token == generation && isGuest && guestPath == GuestPath.PERSONAL
            try {
                val saved = guestMarketData?.load()
                if (current()) guestQuotes = saved
                val latest = guestMarketData?.refresh()
                if (current()) { guestQuotes = latest; guestQuotesFailed = false }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current()) guestQuotesFailed = true }
            finally { if (current()) { guestQuotesRefreshing = false; guestQuoteJob = null } }
        }
    }
    private fun stopGuestQuotes() {
        guestQuoteJob?.cancel(); guestQuoteJob = null; guestQuotes = null
        guestQuotesRefreshing = false; guestQuotesFailed = false
    }
    internal var guestMigration by mutableStateOf<GuestMigration?>(null); private set
    private var guestMigrationJob: Job? = null
    internal fun reviewGuestMigration() {
        val signedIn = user ?: return
        if (isGuest || locked || busy || guestRepository == null || guestMigrationJob?.isActive == true) return
        val token = generation
        val binding = MigrationAccount(origin, signedIn.text("id"), signedIn.text("email"))
        val coordinator = GuestMigration(guestRepository, ApiGuestMigrationRemote(api), binding) {
            token == generation && !isGuest && !locked && user?.text("id") == binding.id && origin == binding.origin
        }
        guestMigration = coordinator
        guestMigrationJob = viewModelScope.launch { coordinator.prepare() }
    }
    internal fun transferGuestMigration() {
        val coordinator = guestMigration ?: return
        if (guestMigrationJob?.isActive == true || user == null || isGuest || locked) return
        guestMigrationJob = viewModelScope.launch {
            try {
                coordinator.transfer()
                if (guestMigration === coordinator && !isGuest && !locked && user?.text("id") == coordinator.account.id && origin == coordinator.account.origin && coordinator.results.values.any { it.target.isNotBlank() }) refreshPortfolio()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Review the selected guest data and confirm again." }
        }
    }
    internal fun previewGuestMigrationDocument(row: MigrationRow) {
        val coordinator = guestMigration ?: return
        if (guestMigrationJob?.isActive == true || coordinator.running) return
        guestMigrationJob = viewModelScope.launch {
            try { coordinator.showOriginal(row) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not open the protected local PDF. Unlock this device and retry." }
        }
    }
    internal fun closeGuestMigration() {
        guestMigrationJob?.cancel(); guestMigrationJob = null
        guestMigration?.clear(); guestMigration = null
    }
    val guestImport = guestRepository?.let { GuestPdfImport(it, importWorkDispatcher) }
    fun selectGuestPdf(actions: NativeActions, engine: InvestmentEngine) = guestAction {
        requireNotNull(guestImport).select(actions, EngineDocumentParser(engine))
        requireNotNull(guestRepository).load()
    }
    fun dismissGuestStorageError() { guestStorageError = null }
    fun finishGuestImport() { guestImport?.clear() }
    fun cancelGuestImport() { guestJob?.cancel(); guestImport?.clear() }
    fun previewGuestPdf(id: String? = null, fileName: String = "investment.pdf") = guestAction {
        requireNotNull(guestImport).preview(id, fileName); requireNotNull(guestRepository).load()
    }
    fun deleteGuestDocument(id: String) = guestWrite({ requireNotNull(guestRepository).deleteDocument(id) }) { documentThumbnails.clear() }
    fun loadGuestPortfolio() = guestAction { requireNotNull(guestRepository).load() }
    private fun guestAction(operation: suspend () -> GuestPortfolio) {
        if (!foreground || !isGuest || guestPath != GuestPath.PERSONAL || guestJob?.isActive == true || guestRepository == null) return
        val revision = generation
        guestStorageLoading = true; guestStorageError = null
        guestJob = viewModelScope.launch {
            try {
                val result = operation()
                if (revision == generation && foreground && isGuest && guestPath == GuestPath.PERSONAL) guestPortfolio = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (revision == generation) guestStorageError = if (failure is GuestPdfReviewException || failure is GuestDataDeletionException) failure.message else "Local portfolio could not be opened or saved. Your existing data was not reset. Unlock your device and retry."
            } finally { if (revision == generation) { guestStorageLoading = false; guestJob = null } }
        }
    }
    private fun guestWrite(operation: suspend () -> GuestPortfolio, completed: () -> Unit = {}) {
        val revision = generation
        guestAction { operation().also { if (revision == generation && isGuest && guestPath == GuestPath.PERSONAL) completed() } }
    }
    private var activeApi: PortfolioApi? = null
    var api: PortfolioApi
        get() { check(!isGuest) { "Account features are unavailable in guest mode." }; return activeApi ?: apiFactory(origin).also { activeApi = it } }
        private set(value) { activeApi = value }
    var screenCaptureAllowed by mutableStateOf(settings.readScreenCaptureAllowed())
        private set
    val captureProtectionEnabled get() = !screenCaptureAllowed
    val numbersRevealBlocked get() = captureProtectionEnabled && (captureMaskRequired ?: captureProtectionRequiresMaskedNumbers)
    val numbersConcealed get() = numbersHidden || numbersRevealBlocked
    fun updateScreenCapturePermission(allowed: Boolean) {
        settings.writeScreenCaptureAllowed(allowed)
        screenCaptureAllowed = allowed
    }
    var shakeToHideEnabled by mutableStateOf(settings.readShakeToHideEnabled())
        private set
    fun updateShakeToHide(enabled: Boolean) {
        settings.writeShakeToHideEnabled(enabled)
        shakeToHideEnabled = enabled
    }
    var numbersHidden by mutableStateOf(settings.readNumbersHidden())
        private set
    fun toggleNumberVisibility() {
        if (numbersRevealBlocked) {
            message = "Numbers are protected right now. Check Settings → Privacy."
            return
        }
        settings.writeNumbersHidden(!numbersHidden)
        numbersHidden = !numbersHidden
    }
    val secureSignInAvailable get() = vault?.available == true
    val unlockLabel get() = vault?.unlockLabel ?: "Device unlock"
    var secureSignInEnabled by mutableStateOf(!isGuest && entryChoice != MANUAL_ACCOUNT_ENTRY && vault?.hasSaved(origin) == true)
        private set
    var locked by mutableStateOf(secureSignInEnabled)
        private set
    var verifyingSession by mutableStateOf(false)
        private set
    // PDF imports own their journal and selected bytes; this model owns the action job.
    private val imports = PortfolioImports(settings, importWorkDispatcher, object : PortfolioImportHost {
        override val api get() = this@PortfolioModel.api
        override var portfolio: PortfolioSnapshot
            get() = this@PortfolioModel.portfolio
            set(value) { this@PortfolioModel.portfolio = value }
        override val documents get() = this@PortfolioModel.documents
        override val shareOrders get() = this@PortfolioModel.shareOrders
        override val statementItems get() = this@PortfolioModel.statementItems
        override val canPublishPublicData get() = this@PortfolioModel.canPublishPublicData
        override fun action(block: suspend () -> Unit) = this@PortfolioModel.action(block)
        override suspend fun refreshAfterWrite(success: String) = this@PortfolioModel.refreshAfterWrite(success)
    })
    internal val importHistory get() = imports.importHistory
    internal val importHistoryStorageFailed get() = imports.importHistoryStorageFailed
    internal val activeImportId get() = imports.activeImportId
    internal val importSelectionRevision get() = imports.importSelectionRevision
    internal fun activeImportReceipt() = imports.activeImportReceipt()
    internal fun currentImportReview() = imports.currentImportReview()
    private fun reconcileImportHistory() = imports.reconcileImportHistory()
    private var activeJob: Job? = null
    private var generation = 0
    private var foreground = true
    private var backgroundedAt: TimeMark? = null
    private var backgroundLockJob: Job? = null
    private var privateDataLoadedAt: TimeMark? = null
    private var privateDataAvailable by mutableStateOf(false)
    val portfolioReady get() = if (isGuest) guestPath == GuestPath.SAMPLE || guestPortfolio != null else privateDataAvailable
    // An initial snapshot is pending until a request actually fails. A not-yet-started
    // sync or blocking session/password restore must never look like a failed request.
    internal val portfolioLoading get() = if (isGuest) guestStorageLoading || (!portfolioReady && guestStorageError == null) else portfolioSyncing || (!portfolioReady && portfolioSyncError == null)
    var portfolioSyncing by mutableStateOf(false)
        private set
    var portfolioSyncError by mutableStateOf<String?>(null)
        private set
    var documentsLoading by mutableStateOf(false)
        private set
    var documentsSyncError by mutableStateOf<String?>(null)
        private set
    private var startupSyncJob: Job? = null
    private var startupSyncRevision = 0
    private val portfolioSyncMutex = Mutex()
    private var unlockToUi: UnlockPerformance.Span? = null
    private var deviceToUi: UnlockPerformance.Span? = null

    fun dashboardPresented() {
        unlockToUi?.end(); unlockToUi = null
        deviceToUi?.end(); deviceToUi = null
    }
    private var marketLoadedAt: TimeMark? = null
    private var automaticUnlockAttempted = false
    private var automaticUnlockPending = false
    var user by mutableStateOf<JsonRecord?>(null)
        private set
    // Invited accounts use random UUIDv4 IDs. Environment-managed friends use UUIDv8.
    val canDeleteAccount get() = user?.optBoolean("isOwner") == false &&
        user?.text("id")?.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")) == true
    var refreshPhase by mutableStateOf(PortfolioRefreshPhase.IDLE)
        private set
    private var accountBusy by mutableStateOf<Boolean>(false)
    var busy: Boolean
        get() = if (isGuest) guestStorageLoading else accountBusy
        private set(value) { accountBusy = value }
    val pdfImportProgress get() = imports.pdfImportProgress
    var errorRevision by mutableStateOf(0)
        private set
    var messageRevision by mutableStateOf(0)
        private set
    private var errorText by mutableStateOf<String?>(null)
    var error: String?
        get() = errorText
        set(value) { errorText = value?.let(::safeErrorMessage); errorRevision++ }
    private var messageText by mutableStateOf<String?>(null)
    var message: String?
        get() = messageText
        set(value) { messageText = value; messageRevision++ }
    internal var applicationBidForm by mutableStateOf<JsonRecord?>(null); private set
    internal fun startApplicationBid(form: JsonRecord) {
        val issue = calendar.firstOrNull { it.text("id") == form.optJSONObject("applicationForm")?.text("calendarId") } ?: return
        if (!isGuest && !busy && applicationBidAllowed(form,issue,bondSubscriptionDate(kotlin.time.Clock.System.now()))) {
            closeDocumentPreview(); applicationBidForm = form
        }
    }
    internal fun closeApplicationBid() { applicationBidForm = null }
    internal fun saveApplicationBid(form: JsonRecord, amount: Double, bidId: String, completed: () -> Unit) = action {
        val issue = calendar.firstOrNull { it.text("id") == form.optJSONObject("applicationForm")?.text("calendarId") }
        require(issue != null && applicationBidAllowed(form,issue,bondSubscriptionDate(kotlin.time.Clock.System.now()))) { "Bids are unavailable for this form or the subscription window has closed." }
        val response = api.request("/api/bonds/application-forms/${form.text("id")}/bids", "POST", JsonRecord().put("amount", amount).put("bidId",bidId))
        require(response.optJSONObject("purchase")?.text("id")?.isNotBlank() == true) { "The bid could not be verified. Refresh your portfolio before retrying." }
        loadPrivateData(force = true)
        applicationBidForm = null
        message = "Bid saved. Awaiting BNR results and allocation confirmation."
        completed()
    }
    var cashLedgerRequested by mutableStateOf(false)
    var backupRequested by mutableStateOf(false)
    var ledgerHoldingId by mutableStateOf<String?>(null)
    fun openCashLedger(holdingId: String? = null) { ledgerHoldingId = holdingId; cashLedgerRequested = true }
    private val cashLedger = PortfolioCashLedger({ api }, { loadPrivateData(force = true) })
    val ledgerAvailable get() = cashLedger.available
    private val guestLedger by derivedStateOf { guestPortfolio?.metadata?.rows("cashLedger").orEmpty() }
    val ledgerEntries get() = if (isGuest) guestLedger else cashLedger.entries
    val ledgerError get() = cashLedger.error
    suspend fun reloadLedger() { if (!isGuest) cashLedger.reload() }
    fun saveLedgerEntry(entry: JsonRecord, completed: () -> Unit) {
        if (sampleGuest) return
        if (isGuest) guestWrite({ requireNotNull(guestRepository).saveLedgerEntry(entry) }, completed)
        else action { cashLedger.save(entry); completed() }
    }
    fun deleteLedgerEntry(id: String) {
        if (sampleGuest) return
        if (isGuest) guestWrite({ requireNotNull(guestRepository).deleteLedgerEntry(id) })
        else action { cashLedger.delete(id) }
    }
    suspend fun exportGuestBackup(): ByteArray = requireNotNull(guestRepository).exportBackup()
    fun restoreGuestBackup(bytes: ByteArray, completed: () -> Unit) {
        check(isGuest && !sampleGuest)
        val owned = bytes.copyOf()
        guestWrite({ try { requireNotNull(guestRepository).restoreBackup(owned) } finally { owned.fill(0) } }, completed)
    }
    private var accountPortfolio by mutableStateOf<PortfolioSnapshot>(PortfolioSnapshot())
    var portfolio: PortfolioSnapshot
        get() = if (isGuest) guestViewPortfolio(guestPath, guestPortfolio, samplePortfolio) else accountPortfolio
        private set(value) { accountPortfolio = value.copy(shareOrders = shareOrders, couponReferences = sharedCouponTerms) }
    // Account notification state is isolated; session invalidation still runs through this model.
    private val notificationCenter = PortfolioNotifications(viewModelScope, clock, object : PortfolioNotificationHost {
        override val api get() = this@PortfolioModel.api
        override val user get() = this@PortfolioModel.user
        override val locked get() = this@PortfolioModel.locked
        override val isGuest get() = this@PortfolioModel.isGuest
        override val monthlyInvestmentTarget get() = this@PortfolioModel.monthlyInvestmentTarget
        override var error: String?
            get() = this@PortfolioModel.error
            set(value) { this@PortfolioModel.error = value }
        override fun applyMonthlyTarget(amount: Long) {
            this@PortfolioModel.monthlyInvestmentTarget = amount
            targetAccount()?.let { settings.writeMonthlyTarget(it, amount) }
        }
        override suspend fun invalidateRejectedSession(failure: Exception) = this@PortfolioModel.invalidateRejectedSession(failure)
    })
    val bondReminderFeedback get() = notificationCenter.bondReminderFeedback
    val bondReminderMonths get() = notificationCenter.bondReminderMonths
    val bondRemindersLoaded get() = notificationCenter.bondRemindersLoaded
    val bondRemindersLoading get() = notificationCenter.bondRemindersLoading
    val bondReminderSaving get() = notificationCenter.bondReminderSaving
    val bondRemindersError get() = notificationCenter.bondRemindersError
    val notificationSettingsAvailable get() = notificationCenter.notificationSettingsAvailable
    val notificationSettingsSaving get() = notificationCenter.notificationSettingsSaving
    val notificationDate get() = notificationCenter.notificationDate
    val notifications get() = notificationCenter.notifications
    val unreadNotifications get() = notificationCenter.unreadNotifications
    val notificationNextBefore get() = notificationCenter.notificationNextBefore
    val notificationsLoading get() = notificationCenter.notificationsLoading
    val notificationsError get() = notificationCenter.notificationsError
    var notificationPreferences: JsonRecord
        get() = notificationCenter.notificationPreferences
        private set(value) { notificationCenter.notificationPreferences = value }
    private val notificationGeneration get() = notificationCenter.notificationGeneration
    private val notificationLoadedAt get() = notificationCenter.notificationLoadedAt
    fun dismissBondReminderFeedback() = notificationCenter.dismissBondReminderFeedback()
    fun loadBondReminders() = notificationCenter.loadBondReminders()
    fun setBondMonthReminder(month: String, enabled: Boolean) = notificationCenter.setBondMonthReminder(month, enabled)
    fun notificationPreferenceSaving(category: String? = null) = notificationCenter.notificationPreferenceSaving(category)
    fun notificationPreferenceEnabled(category: String? = null) = notificationCenter.notificationPreferenceEnabled(category)
    private suspend fun fetchNotifications(more: Boolean = false) = notificationCenter.fetchNotifications(more)
    fun updateNotificationPreference(category: String? = null, enabled: Boolean) = notificationCenter.updateNotificationPreference(category, enabled)
    fun refreshNotifications() = notificationCenter.refreshNotifications()
    fun moreNotifications() = notificationCenter.moreNotifications()
    fun readNotification(id: String) = notificationCenter.readNotification(id)
    suspend fun syncPushDevice() = notificationCenter.syncPushDevice()
    var couponReceipts: List<JsonRecord>
        get() = if (isGuest) if (guestPath == GuestPath.PERSONAL) guestPortfolio?.receipts.orEmpty() else emptyList() else notificationCenter.couponReceipts
        private set(value) { notificationCenter.couponReceipts = value }
    var couponTrackingAvailable: Boolean
        get() = if (isGuest) portfolioReady else notificationCenter.couponTrackingAvailable
        private set(value) { notificationCenter.couponTrackingAvailable = value }
    fun confirmCoupon(purchaseId: String,dueDate: String,received: Boolean=true) {
        if (isGuest) { guestWrite({ requireNotNull(guestRepository).setReceipt(purchaseId, dueDate, received) }); return }
        action {
            val result=api.request("/api/bonds/notifications/coupons","POST",JsonRecord().put("purchaseId",purchaseId).put("dueDate",dueDate).put("received",received))
            couponReceipts=result.rows("receipts");couponTrackingAvailable=true
            fetchNotifications()
        }
    }
    fun couponReceipt(purchaseId: String,dueDate: String)=couponReceipts.firstOrNull { it.text("purchaseId")==purchaseId && it.text("dueDate")==dueDate }
    private suspend fun invalidateRejectedSession(failure: Exception): Boolean {
        if (failure !is ApiException || failure.status != 401) return false
        api.clearSession()
        if (secureSignInEnabled) lock() else { ++generation; clearPrivateData() }
        return true
    }
    private val statements = PortfolioStatements({ api }, ::refreshAfterWrite)
    val statementItems get() = statements.items
    val statementReviewRows get() = statements.reviewRows
    fun closeStatementReview() = statements.closeReview()
    fun showStatementRecordReview(row: JsonRecord) = statements.showRecord(row)
    fun showStatementDocumentReview(id: String) = action { statements.showDocument(id) }
    fun resolveStatementRecord(item: JsonRecord, purchaseId: String?, reason: String, completed: () -> Unit = {}) =
        action { statements.resolve(item, purchaseId, reason, completed) }
    fun createStatementBond(item: JsonRecord, instrument: JsonRecord, figures: JsonRecord, completed: () -> Unit) =
        action { statements.createBond(item, instrument, figures, completed) }
    private var accountHistoryComplete by mutableStateOf<Boolean>(false)
    var historyComplete: Boolean
        get() = if (isGuest) portfolioReady else accountHistoryComplete
        private set(value) { accountHistoryComplete = value }
    private var compactOverviewSupported: Boolean? = null
    val purchases get() = portfolio.purchases
    val holdings get() = portfolio.holdings
    private var accountMonthlyInvestmentTarget by mutableStateOf<Long>(DEFAULT_MONTHLY_INVESTMENT_TARGET)
    var monthlyInvestmentTarget: Long
        get() = if (isGuest) guestPortfolio?.metadata?.text("monthlyTarget")?.toLongOrNull() ?: DEFAULT_MONTHLY_INVESTMENT_TARGET else accountMonthlyInvestmentTarget
        private set(value) { accountMonthlyInvestmentTarget = value }
    private fun targetAccount() = user?.text("id")?.takeIf { it.isNotBlank() }?.let { "$origin|$it" }
    private fun loadInvestmentTarget() {
        imports.attach(targetAccount())
        monthlyInvestmentTarget = targetAccount()?.let(settings::readMonthlyTarget)?.takeIf { validInvestmentTarget(it) }
            ?: DEFAULT_MONTHLY_INVESTMENT_TARGET
    }
    fun updateMonthlyInvestmentTarget(amount: Long) {
        require(validInvestmentTarget(amount)) { "Enter a positive whole RWF amount." }
        if (isGuest) { guestWrite({ requireNotNull(guestRepository).setMonthlyTarget(amount) }); return }
        val account = requireNotNull(targetAccount())
        settings.writeMonthlyTarget(account, amount)
        monthlyInvestmentTarget = amount
        val revision=notificationGeneration
        viewModelScope.launch {
            try {
                val saved=api.request("/api/bonds/notifications/preferences","PUT",JsonRecord().put("monthlyTarget",amount))
                if(revision==notificationGeneration) saved.text("monthlyTarget").toLongOrNull()?.let { target ->
                    notificationPreferences=JsonRecord(notificationPreferences.toString()).put("monthlyTarget",target)
                }
            }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(revision==notificationGeneration)error="Target saved on this device. Server sync failed." }
        }
    }
    fun monthlyTargetProgress(month: LocalDate = Today): MonthlyInvestmentProgress? =
        if (portfolioReady) monthlyInvestmentProgress(portfolio, month, monthlyInvestmentTarget, Today) else null
    val documents get() = if (isGuest) guestViewDocuments(portfolio) else portfolio.documents
    private val pendingShareOrders = PortfolioShareOrders({ api }) { orders ->
        accountPortfolio = accountPortfolio.copy(shareOrders = orders)
    }
    val shareOrders get() = pendingShareOrders.orders
    val shareOrdersAvailable get() = pendingShareOrders.available
    private suspend fun reloadShareOrders() = pendingShareOrders.reload()
    fun saveShareOrder(id: String, order: JsonRecord, completed: () -> Unit) = action {
        require(user != null && !isGuest) { "Sign in to save a pending share order." }
        pendingShareOrders.save(id, order, completed)
        message = "Pending share order saved."
    }
    fun cancelShareOrder(order: JsonRecord, completed: () -> Unit) = action {
        pendingShareOrders.cancel(order, completed)
        message = "Pending share order cancelled."
    }
    var market by mutableStateOf<JsonRecord?>(null)
        private set
    private var accountQuotes by mutableStateOf<JsonRecord?>(null)
    var quotes: JsonRecord?
        get() = if (isGuest) if (guestPath == GuestPath.PERSONAL) guestQuotes?.data else null else accountQuotes
        private set(value) { accountQuotes = value }
    private var accountQuoteRefreshFailed by mutableStateOf<Boolean>(false)
    var quoteRefreshFailed: Boolean
        get() = if (isGuest) guestQuotesFailed || guestQuotes?.stale(kotlin.time.Clock.System.now()) == true else accountQuoteRefreshFailed
        private set(value) { accountQuoteRefreshFailed = value }
    private var publishedCalendar by mutableStateOf<List<JsonRecord>>(emptyList())
    var sharedCouponTerms by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    val calendar get() = if (isGuest) emptyList() else mergeBondIssuances(publishedCalendar, documents, sharedCouponTerms)
    var education by mutableStateOf<List<JsonRecord>>(emptyList())
        private set


    private suspend fun loadPublicData() {
        publishedCalendar = JsonList(assets("issuance-calendar.json").decodeToString()).objects()
        education = JsonList(assets("education.json").decodeToString()).objects()
    }
    fun action(block: suspend () -> Unit) = runAction(false, block = block)
    private fun runAction(allowLocked: Boolean, failureMessage: String = GENERAL_ERROR_MESSAGE, block: suspend () -> Unit) {
        if (isGuest || busy || (locked && !allowLocked)) return
        busy = true
        error = null
        message = null
        val token = ++generation
        activeJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) {
                if (token == generation) imports.recordCancellation()
                throw cancelled
            }
            catch (failure: Exception) {
                if (token == generation) {
                    imports.recordFailure(userErrorMessage(failure, failureMessage))
                    if (failure is ApiException && failure.status == 401) {
                        api.clearSession(); clearPrivateData()
                        locked = secureSignInEnabled
                        if (locked) vault?.lock()
                    }
                    if (locked) { vault?.lock(); api.close(); api = apiFactory(origin); clearPrivateData() }
                    error = userErrorMessage(failure, failureMessage)
                }
            } finally { if (token == generation) { busy = false; verifyingSession = false; activeJob = null; tryAutomaticUnlock() } }
        }
    }
    fun foreground() {
        foreground = true
        if (isGuest && guestPath == GuestPath.PERSONAL && guestPortfolio == null) loadGuestPortfolio()
        if (isGuest) refreshGuestQuotes()
        val expired = backgroundGraceExpired()
        backgroundLockJob?.cancel(); backgroundLockJob = null
        backgroundedAt = null
        if (expired) lock()
        if (locked && !automaticUnlockAttempted) automaticUnlockPending = true
        tryAutomaticUnlock()
        if(user!=null && !locked && notificationLoadedAt?.elapsedNow()?.inWholeMilliseconds?.let { it<DATA_CACHE_MILLIS }!=true)refreshNotifications()
    }
    private fun tryAutomaticUnlock() {
        if (!automaticUnlockPending || !foreground || busy || !locked || !secureSignInEnabled || vault?.authenticating == true) return
        unlock()
    }
    fun background(systemInteraction: Boolean = false) {
        if (isGuest && !systemInteraction) guestPortfolio = null
        foreground = false
        if (!systemInteraction && vault?.authenticating != true) {
            automaticUnlockPending = false
            automaticUnlockAttempted = false
            if (backgroundedAt == null) backgroundedAt = clock.markNow()
            scheduleBackgroundLock()
        }
    }
    private fun scheduleBackgroundLock() {
        if (!secureSignInEnabled || locked || backgroundLockJob != null) return
        val elapsed = backgroundedAt?.elapsedNow()?.inWholeMilliseconds ?: return
        val remaining = SESSION_BACKGROUND_GRACE_MILLIS - elapsed
        if (remaining <= 0) { lock(); return }
        backgroundLockJob = viewModelScope.launch {
            delay(remaining)
            if (!foreground) lock()
        }
    }
    private fun backgroundGraceExpired() = backgroundedAt?.elapsedNow()?.inWholeMilliseconds
        ?.let { it >= SESSION_BACKGROUND_GRACE_MILLIS } == true
    fun lock() {
        if (!secureSignInEnabled) return
        backgroundLockJob?.cancel(); backgroundLockJob = null; backgroundedAt = null
        ++generation
        activeJob?.cancel(); activeJob = null; busy = false
        vault?.lock()
        api.close(); api = apiFactory(origin)
        // Keep the explicitly added home widget useful while the app locks in the background.
        // Its projection contains only public issuances and generic payment dates, never amounts.
        clearPrivateData(clearWidgets = false)
        locked = true
        error = null; message = null
    }
    fun usePassword() {
        // Leaving a slow verification cancels its response before changing authentication mode.
        if (locked && verifyingSession) {
            ++generation; activeJob?.cancel(); activeJob = null; busy = false; verifyingSession = false
        }
        runAction(allowLocked = true) {
            vault?.delete(origin)
            secureSignInEnabled = false; locked = false
            api.close(); api = apiFactory(origin)
            clearPrivateData(); error = null; message = null
        }
    }
    fun disableSecureSignIn() = action {
        vault?.delete(origin)
        secureSignInEnabled = false
        message = "Saved sign-in removed from this device."
    }
    /** Mode changes cancel account work before exposing any local guest view. No financial data is copied. */
    fun enterGuest(path: GuestPath): Boolean {
        try { settings.writeSessionChoice(path.stored) }
        catch (_: Exception) { error = "Guest mode could not be saved. Try again."; return false }
        closeDocumentPreview(); stopGuestQuotes()
        ++generation; guestJob?.cancel(); guestJob = null; guestStorageLoading = false; guestPortfolio = null; guestStorageError = null; guestImport?.clear()
        activeJob?.cancel(); activeJob = null; busy = false
        activeApi?.close(); activeApi = null
        vault?.lock(); clearPrivateData()
        secureSignInEnabled = false; locked = false
        automaticUnlockPending = false; automaticUnlockAttempted = true
        entryChoice = path.stored; accountEntry = false
        error = null; message = null
        loadGuestPortfolio()
        refreshGuestQuotes()
        return true
    }
    fun beginAccountSignIn() {
        if (!isGuest) return
        closeDocumentPreview(); stopGuestQuotes()
        NativePdfInbox.pending?.bytes?.fill(0); NativePdfInbox.dismiss() // Never forward a queued guest PDF into account ingestion.
        guestJob?.cancel(); guestJob = null; guestPortfolio = null; guestStorageLoading = false; guestStorageError = null; guestImport?.clear()
        accountEntry = true
        error = null; message = null
    }
    fun returnToGuest() { guestPath?.let(::enterGuest) }
    fun startAccountSession() {
        if (!isGuest && !locked && user == null && !busy) runAction(true, CONNECTION_ERROR_MESSAGE) { checkSession() }
    }

    private suspend fun saveSignIn(email: String, password: String): Boolean {
        val storage = requireNotNull(vault) { "Device authentication is unavailable." }
        val value = JsonRecord().put("origin", origin).put("email", email.trim()).put("password", password).put("session", api.sessionSnapshot()).toString()
        val saved = storage.save(origin, value)
        secureSignInEnabled = (entryChoice != MANUAL_ACCOUNT_ENTRY || saved) && storage.hasSaved(origin)
        if (saved) { settings.writeSessionChoice(null); entryChoice = null }
        if (!foreground && secureSignInEnabled) scheduleBackgroundLock()
        return saved
    }
    fun enableSecureSignIn(password: String) = action {
        require(secureSignInAvailable) { "Set up device authentication first." }
        val account = requireNotNull(user)
        val email = account.text("email")
        val response = try { api.request("/api/bonds/auth/login", "POST", JsonRecord().put("email", email).put("password", password)) }
        catch (failure: ApiException) {
            if (failure.status == 401 || failure.status == 403) error("Password did not match. Try again.")
            throw failure
        }
        require(response.getJSONObject("user").text("id") == account.text("id")) { "Sign-in account did not match." }
        if (saveSignIn(email, password)) message = "Secure sign-in enabled on this device."
    }
    fun unlock() {
        if (!locked || busy) return
        // One automatic prompt per app visit. Cancellation leaves a manual retry available.
        automaticUnlockPending = false
        automaticUnlockAttempted = true
        runAction(allowLocked = true) {
            val revision = generation
            val storage = requireNotNull(vault)
            unlockToUi = UnlockPerformance.begin("unlock_to_first_frame")
            val raw = UnlockPerformance.measure("protected_storage_read_including_prompt") { storage.read(origin) } ?: return@runAction
            if (revision != generation || isGuest) return@runAction
            verifyingSession = true
            deviceToUi = UnlockPerformance.begin("device_success_to_first_frame")
            require(raw.length <= 48_000) { "Saved sign-in is invalid." }
            val saved = runCatching { JsonRecord(raw) }.getOrElse { error("Saved sign-in is invalid. Use your password to sign in again.") }
            require(saved.text("origin") == origin) { "Saved sign-in belongs to another server." }
            val email = saved.text("email")
            val password = saved.text("password")
            require(email.isNotBlank() && password.length in 1..128) { "Saved sign-in is invalid." }
            if (!foreground && backgroundGraceExpired()) { lock(); return@runAction }
            try { UnlockPerformance.measure("cookie_restore") { api.restoreSession(saved.text("session")) } }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { api.clearSession() }
            val session = try {
                if (JsonRecord(api.sessionSnapshot()).rows("cookies").isEmpty()) JsonRecord().put("authenticated", false)
                else api.request("/api/bonds/auth/session")
            }
            catch (failure: ApiException) { if (failure.status == 401) JsonRecord().put("authenticated", false) else throw failure }
            var renewed = false
            var account = session.optJSONObject("user").takeIf { session.optBoolean("authenticated") && it?.text("email").equals(email, ignoreCase = true) }
            if (account == null) {
                api.clearSession()
                renewed = true
                try {
                    account = api.request("/api/bonds/auth/login", "POST", JsonRecord().put("email", email).put("password", password).withDeviceIdentity()).getJSONObject("user")
                } catch (failure: ApiException) {
                    if (failure.status == 401 || failure.status == 403) {
                        storage.delete(origin); secureSignInEnabled = false; locked = false
                        throw ApiException(failure.status, "Your saved sign-in no longer works. Sign in with your password.")
                    }
                    throw failure
                }
            }
            if (revision != generation || isGuest) return@runAction
            require(account.text("email").equals(email, ignoreCase = true)) { "Sign-in account did not match." }
            if (calendar.isEmpty() && education.isEmpty()) loadPublicData()
            // An unchanged opaque cookie does not need a Keychain/Keystore rewrite on every visit.
            if (renewed) UnlockPerformance.measure("protected_storage_rotation") {
                storage.save(origin, JsonRecord().put("origin", origin).put("email", email).put("password", password).put("session", api.sessionSnapshot()).toString())
            }
            if (!foreground && backgroundGraceExpired()) { lock(); return@runAction }
            user = account
            loadInvestmentTarget()
            locked = false
            startPortfolioSync()
            if (!foreground) scheduleBackgroundLock()
        }
    }

    fun connect(value: String) = action {
        val candidate = nativeBackendOrigin(value.trim().trimEnd('/'))
        val next = apiFactory(candidate)
        vault?.delete(origin)
        secureSignInEnabled = false; locked = false
        clearPrivateData()
        api.clearSession()
        api.close()
        api = next
        origin = candidate
        compactOverviewSupported = null
        settings.writeOrigin(origin)
        checkSession()
    }

    private suspend fun checkSession() {
        val revision = generation
        val session = api.request("/api/bonds/auth/session")
        if (revision != generation || isGuest) return
        user = if (session.optBoolean("authenticated")) session.optJSONObject("user") else null
        loadInvestmentTarget()
        if (user != null) startPortfolioSync()
    }

    fun login(email: String, password: String, invitation: String = "", savePassword: Boolean = false,
        onAuthenticated: suspend () -> Unit = {}) = action {
        val revision = generation
        val register = invitation.isNotBlank()
        val response = api.request("/api/bonds/auth/${if (register) "register" else "login"}", "POST",
            JsonRecord().put("email", email.trim()).put("password", password).withDeviceIdentity().apply { if (register) put("invite", invitation.trim()) })
        val account = response.getJSONObject("user")
        // Only submit the password-manager form after the server accepts these credentials.
        // Declining/failing a password save must not turn a successful login into a failed one.
        try { onAuthenticated() }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { message = "Signed in. Your password could not be saved to the password manager." }
        if (revision != generation || isGuest) return@action
        if (guestPath != null) {
            val choice = MANUAL_ACCOUNT_ENTRY
            settings.writeSessionChoice(choice); entryChoice = choice; accountEntry = false
        }
        user = account
        loadInvestmentTarget()
        startPortfolioSync()
        if (savePassword) saveSignIn(email, password)
    }

    suspend fun loadPrivateData(force: Boolean = false) = portfolioSyncMutex.withLock {
        portfolioSyncError = null
        try { loadPrivateSnapshot(force, deferSupplementary = false) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (!portfolioReady && !(failure is ApiException && failure.status == 401))
                portfolioSyncError = "Investments could not be loaded. Check your connection and try again."
            throw failure
        }
    }

    private suspend fun loadPrivateSnapshot(force: Boolean, deferSupplementary: Boolean) {
        if (!force && privateDataLoadedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < DATA_CACHE_MILLIS } == true) return
        privateDataLoadedAt = null
        val overview = if (compactOverviewSupported == false) null else try {
            val response = api.request("/api/bonds/mobile-overview")
            if (response.optInt("version") == 1 && response.has("recentPurchaseIds") && response.has("recentHoldingIds")) {
                compactOverviewSupported = true
                response
            } else { compactOverviewSupported = false; null }
        } catch (failure: ApiException) {
            if (failure.status == 404 || failure.status == 405) { compactOverviewSupported = false; null } else throw failure
        }
        val (purchases, holdings) = if (overview != null) overview.rows("purchases") to overview.rows("holdings")
        else coroutineScope {
            val bonds = async { api.request("/api/bonds/purchases").rows("purchases") }
            val shares = async { api.request("/api/bonds/equities").rows("holdings") }
            bonds.await() to shares.await()
        }
        val allBondsForPending = if (overview != null) try { api.request("/api/bonds/purchases").rows("purchases") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure; null }
            else purchases
        val completePendingBonds = (allBondsForPending ?: portfolio.purchases).filter { it.text("status") == "submitted" }
        val documents = if (deferSupplementary) portfolio.documents else api.request("/api/bonds/documents").rows("documents")
        val monthlyTotals = overview?.optJSONArray("monthlyInvestmentTotals")?.objects()
            ?: if (overview != null) try {
                // Old compact responses omit contribution totals. Fetch their source records once
                // during this sync, derive the rolling summary, then discard those extra records.
                monthlyGoalTotalsFromHistory(requireNotNull(allBondsForPending),
                    api.request("/api/bonds/equities").rows("holdings"), Today)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is ApiException && failure.status == 401) throw failure
                null // An unavailable secondary summary must not hide the rest of the portfolio.
            } else null
        fun ids(key: String): Set<String>? = overview?.optJSONArray(key)?.let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() }
        portfolio = PortfolioSnapshot((purchases + completePendingBonds).distinctBy { it.text("id") }, holdings, if (deferSupplementary) portfolio.documents else documents, ids("recentPurchaseIds"), ids("recentHoldingIds"),
            monthlyTotals)
        reloadLedger()
        reloadShareOrders()
        privateDataAvailable = true
        reconcileImportHistory()
        historyComplete = overview == null
        if (!deferSupplementary) {
            refreshSharedCouponTerms()
            loadStatementItems()
            if (portfolio.investmentShares(Today).isNotEmpty()) refreshEquityQuotes()
            fetchNotifications()
        }
        privateDataLoadedAt = clock.markNow()
    }

    private suspend fun refreshSharedCouponTerms() {
        try {
            sharedCouponTerms = api.request("/api/bonds/coupon-terms").rows("terms")
            accountPortfolio = accountPortfolio.copy(couponReferences = sharedCouponTerms)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure }
    }

    private suspend fun loadStatementItems() = statements.load()

    fun retryPortfolioSync() {
        if (isGuest) { loadGuestPortfolio(); return }
        if (locked || user == null) return
        if (!portfolioReady) error = null
        startupSyncJob?.cancel(); startupSyncJob = null
        privateDataLoadedAt = null
        startPortfolioSync()
    }

    /** Only the server-validated identity gates navigation; enrichment never owns the action lock. */
    fun startPortfolioSync() {
        if (locked || user == null || startupSyncJob?.isActive == true) return
        val revision = notificationGeneration
        val syncRevision = ++startupSyncRevision
        fun currentSync() = revision == notificationGeneration && syncRevision == startupSyncRevision
        portfolioSyncing = true; portfolioSyncError = null
        documentsLoading = true; documentsSyncError = null
        startupSyncJob = viewModelScope.launch {
            suspend fun section(failureMessage: String, block: suspend () -> Unit) {
                try { block() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (!currentSync()) return
                    if (failure is ApiException && failure.status == 401) {
                        // A local biometric result is never accepted as server authorization.
                        api.clearSession(); lock()
                        if (!secureSignInEnabled) clearPrivateData()
                    } else if (failureMessage == "documents") documentsSyncError = "Documents could not be loaded. Try again."
                    else if (failureMessage == "portfolio") portfolioSyncError = "Investments could not be loaded. Check your connection and try again."
                }
            }
            try {
                supervisorScope {
                    launch {
                        section("portfolio") {
                            UnlockPerformance.measure("portfolio_snapshot") {
                                portfolioSyncMutex.withLock { loadPrivateSnapshot(force = false, deferSupplementary = true) }
                            }
                        }
                        if (currentSync()) portfolioSyncing = false
                    }
                    launch {
                        section("documents") {
                            val rows = api.request("/api/bonds/documents").rows("documents")
                            if (currentSync()) { portfolio = portfolio.copy(documents = rows); reconcileImportHistory() }
                        }
                        if (currentSync()) documentsLoading = false
                    }
                    launch { section("coupon-terms") { refreshSharedCouponTerms() } }
                    launch { section("market") { prefetchMarket() } }
                    launch { section("statements") { loadStatementItems() } }
                    launch { section("notifications") { fetchNotifications() } }
                }
            } finally {
                if (currentSync()) { portfolioSyncing = false; documentsLoading = false }
            }
        }
    }

    var applicationFormsError by mutableStateOf<String?>(null)
        private set

    private suspend fun fetchBondApplicationForms() {
        val forms = api.request("/api/bonds/application-forms").rows("forms")
        portfolio = portfolio.copy(documents = portfolio.documents.filterNot { it.text("category") == BOND_APPLICATION_CATEGORY } + forms)
        applicationFormsError = null
    }

    fun refreshBondApplicationForms() = action {
        try { fetchBondApplicationForms() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            applicationFormsError = "Application forms could not be loaded. Try again."
        }
    }

    fun publishBondApplicationForm(issue: JsonRecord, bytes: ByteArray, completed: () -> Unit) = action {
        require(mayUploadBondApplication(user)) { "Only the owner can upload bond application forms." }
        validatePdfEnvelope(bytes)
        val metadata = JsonRecord().put("category", BOND_APPLICATION_CATEGORY).put("visibility", "shared")
            .put("calendarId", issue.text("id")).put("documentDate", issue.text("closingBookDate"))
            .put("label", "Bond application form").put("instrumentName", issue.text("title")).put("issuer", "BNR")
        val uploadId = Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val chunkSize = 512 * 1024
        val count = (bytes.size + chunkSize - 1) / chunkSize
        var saved = false
        for (index in 0 until count) {
            val response = api.request("/api/bonds/documents", "POST", JsonRecord()
                .put("uploadId", uploadId).put("index", index).put("count", count).put("metadata", metadata)
                .put("content", Base64.encode(bytes.copyOfRange(index * chunkSize, minOf(bytes.size, (index + 1) * chunkSize)))))
            if (response.text("documentId").isNotBlank()) { saved = true; break }
        }
        require(saved) { "The application form could not be saved. Try again." }
        completed()
        message = "Shared application form saved."
        try { fetchBondApplicationForms() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            applicationFormsError = "The form was saved. Reload application forms to see it."
        }
    }

    fun loadInvestmentHistory(force: Boolean = false) {
        if (isGuest) { if (!sampleGuest && (!portfolioReady || force)) loadGuestPortfolio(); return }
        if (historyComplete && !force) return
        action {
            val purchases = api.request("/api/bonds/purchases").rows("purchases")
            val holdings = api.request("/api/bonds/equities").rows("holdings")
            portfolio = PortfolioSnapshot(purchases, holdings, portfolio.documents)
            privateDataAvailable = true
            historyComplete = true
            reconcileImportHistory()
        }
    }

    // Prices enrich the saved snapshot. An optional quote failure must not hide saved purchases.
    private suspend fun refreshEquityQuotes(refresh: Boolean = false) {
        try {
            val latest = api.request("/api/bonds/rse/quotes?kind=equities" + if (refresh) "&refresh=1" else "")
            if (latest.rows("quotes").isNotEmpty()) {
                quotes = latest
                quoteRefreshFailed = latest.optJSONObject("source")?.text("status") == "error"
            } else {
                quoteRefreshFailed = true
            }
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            quoteRefreshFailed = true
        }
    }

    fun refresh() { if (isGuest) { loadGuestPortfolio(); refreshGuestQuotes() } else refreshWithFeedback(showMessage = true) }
    fun refreshPortfolio() { if (isGuest) { loadGuestPortfolio(); refreshGuestQuotes() } else refreshWithFeedback(showMessage = false) }
    private fun refreshWithFeedback(showMessage: Boolean) = action {
        val token = generation
        val started = TimeSource.Monotonic.markNow()
        refreshPhase = PortfolioRefreshPhase.LOADING
        try {
            val failure = try { loadPrivateData(force = true); null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { error }
            // Count network time toward the minimum; never delay a slow request further.
            delay((MIN_REFRESH_LOADING_MILLIS - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
            refreshPhase = if (failure == null) PortfolioRefreshPhase.SUCCESS else PortfolioRefreshPhase.ERROR
            delay(if (failure == null) REFRESH_SUCCESS_MILLIS else REFRESH_ERROR_MILLIS)
            if (failure != null) throw failure
            if (showMessage) message = "Portfolio updated from your server."
        } finally { if (token == generation) refreshPhase = PortfolioRefreshPhase.IDLE }
    }
    private val marketMutex = Mutex()
    var marketLoading by mutableStateOf(false)
        private set

    private suspend fun prefetchMarket(refresh: Boolean = false) = marketMutex.withLock {
        if (!refresh && marketLoadedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < DATA_CACHE_MILLIS } == true) return@withLock
        marketLoading = true
        try {
            // Independent upstream resources: equity prices never wait for the multi-page bond board.
            supervisorScope {
                val prices = async { refreshEquityQuotes(refresh) }
                val board = async { market = api.request("/api/bonds/rse/market" + if (refresh) "?refresh=1" else "") }
                prices.await(); board.await()
            }
            marketLoadedAt = clock.markNow()
        } finally { marketLoading = false }
    }

    fun loadMarket(refresh: Boolean = false) {
        if (isGuest) { refreshGuestQuotes(); return }
        if (!refresh && marketLoading) return
        action {
            if (refresh) marketLoadedAt = null
            prefetchMarket(refresh)
        }
    }

    fun importPurchasePdf(actions: NativeActions, engine: InvestmentEngine, bondOnly: Boolean = false,
        expectedStatement: JsonRecord? = null, targetPurchaseId: String? = null, retryEntryId: String? = null,
        completed: (JsonRecord) -> Unit) = imports.importPurchasePdf(actions, engine, bondOnly,
            expectedStatement, targetPurchaseId, retryEntryId, completed)
    internal fun retryImport(id: String, actions: NativeActions, engine: InvestmentEngine, completed: (JsonRecord) -> Unit) =
        imports.retryImport(id, actions, engine, completed)
    internal fun cancelImportReview(review: JsonRecord) = imports.cancelImportReview(review)
    fun discardImportedPdf(review: JsonRecord) = imports.discardImportedPdf(review)
    fun cancelPdfImport() { if (pdfImportProgress != null) activeJob?.cancel() }

    fun checkStatement(review: JsonRecord, completed: (List<JsonRecord>) -> Unit) =
        action { statements.check(review, completed) }

    fun saveImportedPurchase(review: JsonRecord, purchase: JsonRecord, id: String?, completed: () -> Unit) =
        imports.saveImportedPurchase(review, purchase, id, completed)
    internal fun saveImportedStatement(review: JsonRecord, completed: () -> Unit) = imports.saveImportedStatement(review, completed)

    fun confirmPendingPurchase(purchase: JsonRecord,completed: () -> Unit = {}) = action {
        require(purchase.optBoolean("canConfirmWithoutPdf")) { "Use your personal PDF to confirm the final allocation and price." }
        api.request("/api/bonds/purchases/${purchase.text("id")}/confirm","POST",JsonRecord().put("confirmed",true).put("issuanceDocumentId",purchase.text("issuanceDocumentId")))
        completed()
        refreshAfterWrite("Purchase confirmed. No personal PDF was added.")
    }

    fun saveImportedIssuance(review: JsonRecord, confirmationTarget: JsonRecord? = null, sharingConfirmed: Boolean = false, completed: () -> Unit) =
        imports.saveImportedIssuance(review, confirmationTarget, sharingConfirmed, completed)

    fun savePurchase(purchase: JsonRecord, id: String?, completed: () -> Unit) {
        if (isGuest) { guestWrite({ if (guestImport?.review != null) guestImport.save(purchase) else requireNotNull(guestRepository).saveBond(purchase, id) }, completed); return }
        action {
            api.request("/api/bonds/purchases" + (id?.let { "/$it" } ?: ""), if (id == null) "POST" else "PUT", purchase)
            completed()
            refreshAfterWrite("Purchase saved to your portfolio.")
        }
    }

    fun deletePurchase(id: String, completed: () -> Unit) {
        if (isGuest) { guestWrite({ requireNotNull(guestRepository).deleteBond(id) }, completed); return }
        action {
            api.request("/api/bonds/purchases/$id", "DELETE")
            completed()
            refreshAfterWrite("Purchase deleted.")
        }
    }

    fun deleteGuestShare(id: String, completed: () -> Unit) {
        if (isGuest) guestWrite({ requireNotNull(guestRepository).deleteShare(id) }, completed)
    }
    fun saveEquity(holding: JsonRecord, completed: () -> Unit) = saveEquity(holding, null, completed)
    fun saveEquity(holding: JsonRecord, id: String?, completed: () -> Unit) {
        if (isGuest) { guestWrite({ if (guestImport?.review != null) guestImport.save(holding) else requireNotNull(guestRepository).saveShare(holding, id) }, completed); return }
        action {
            if (holding.text("pendingOrderId").isNotBlank()) {
                api.request("/api/bonds/share-orders/${holding.text("pendingOrderId")}/confirm", "POST",
                    JsonRecord().put("confirmed", true).put("version", holding.optInt("pendingOrderVersion")).put("purchase", holding))
                reloadShareOrders()
            } else api.request("/api/bonds/equities", "POST", holding)
            completed()
            refreshAfterWrite("Share purchase saved.")
        }
    }

    private suspend fun refreshAfterWrite(success: String) {
        message = success
        try { loadPrivateData(force = true) }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            error("$success Could not refresh the portfolio. Pull down on Home to update; do not submit it again.")
        }
    }

    // Reports fetch complete inputs independently of the dashboard snapshot.
    private val reports = PortfolioReports(
        currentApi = { api },
        loadLedger = { reloadLedger(); ledgerEntries },
        loadCouponTerms = { refreshSharedCouponTerms(); sharedCouponTerms },
        reportWorkDispatcher = reportWorkDispatcher,
    )
    val reportProgress get() = reports.progress
    val annualReportYear get() = reports.annualReportYear

    fun exportPortfolioReport(actions: NativeActions) = action {
        val account = requireNotNull(user)
        val language = AppLanguage.code
        val target = monthlyInvestmentTarget
        val date = Today
        try {
            val bytes = reports.portfolioPdf(account.text("email"), target, date, language)
            reports.sharing()
            if (actions.sharePdf("Inzira-$date.pdf", bytes)) {
                message = "Portfolio report ready to share."
                try {
                    api.request("/api/bonds/notifications/report", "POST", JsonRecord().put("date", date.toString())
                        .put("exportId", Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }))
                    fetchNotifications()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* An optional notice cannot fail a completed export. */ }
            }
        } finally { reports.clearProgress() }
    }

    private suspend fun annualReportPdf(year: Int): ByteArray {
        val account = requireNotNull(user)
        val date = bondSubscriptionDate(kotlin.time.Clock.System.now())
        val language = AppLanguage.code
        return reports.annualPdf(year, account.text("email"), { monthlyInvestmentTarget }, date, language)
    }
    fun exportAnnualReport(year: Int, actions: NativeActions) = action {
        val bytes = annualReportPdf(year)
        if (actions.sharePdf("Inzira-Annual-$year.pdf", bytes)) message = "Annual report ready to share."
    }

    internal val documentThumbnails = DocumentThumbnailCache()
    internal var documentPreview by mutableStateOf<DocumentPreview?>(null)
        private set
    private var loadingDocumentPreview = false
    private var documentPreviewRequest = 0L
    suspend fun documentBytes(document: JsonRecord): ByteArray = if (isGuest || document.text("id").startsWith("guest:document:")) {
        check(isGuest && !sampleGuest); requireNotNull(guestRepository).documentBytes(document.text("id"))
    } else api.bytes(document.text("downloadUrl").also { require(it.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+"))) })
    fun documentAction(operation: suspend () -> Unit) {
        if (isGuest) guestWrite({ operation(); requireNotNull(guestRepository).load() }) else action { operation() }
    }
    internal fun openDocumentPreview(document: JsonRecord) {
        if (isGuest) {
            if (sampleGuest || busy) return
            val request = ++documentPreviewRequest
            val preview = DocumentPreview(document, document.text("label").ifBlank { "Local PDF" }, document.text("originalFileName"))
            documentPreview = preview
            guestAction {
                val bytes = requireNotNull(guestRepository).documentBytes(document.text("id"))
                if (isGuest && documentPreviewRequest == request && documentPreview != null) documentPreview = preview.copy(bytes = bytes)
                else bytes.fill(0)
                guestRepository.load()
            }
            return
        }
        openAccountDocumentPreview(document)
    }
    private fun openAccountDocumentPreview(document: JsonRecord) = action {
        val year = document.optInt("annualReportYear")
        val title = if (year > 0) "${localized("Annual report")} · $year" else document.text("label").ifBlank { document.text("originalFileName").ifBlank { "PDF" } }
        val preview = DocumentPreview(document, title, importDisplayName(document.text("originalFileName").ifBlank { "investment.pdf" }))
        val request = ++documentPreviewRequest
        documentPreview = preview
        loadingDocumentPreview = true
        try {
            val data = if (year > 0) annualReportPdf(year) else {
                val path = document.text("downloadUrl")
                require(path.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+"))) { "Invalid document URL." }
                api.bytes(path)
            }
            require(data.size in 5..24 * 1024 * 1024 && data.copyOfRange(0, 5).decodeToString() == "%PDF-") { "The server did not return a PDF." }
            if (documentPreviewRequest == request && documentPreview != null) documentPreview = preview.copy(bytes = data)
        } finally { loadingDocumentPreview = false }
    }
    internal fun closeDocumentPreview() {
        documentPreviewRequest++
        if (isGuest) { documentPreview?.bytes?.fill(0); if (documentPreview != null && documentPreview?.bytes == null) guestJob?.cancel() }
        if (loadingDocumentPreview) activeJob?.cancel()
        documentPreview = null
    }

    fun deleteAccount(password: String) = action {
        check(canDeleteAccount) { "Only invited accounts can be deleted here." }
        val response = api.request("/api/bonds/auth/account", "DELETE", JsonRecord().put("password", password))
        check(response.optBoolean("deleted")) { "Account deletion was not confirmed. Please try again." }
        var credentialRemoved = true
        try { targetAccount()?.let { settings.forgetMonthlyTarget(it); settings.writeImportHistory(it, "") }; vault?.delete(origin) }
        catch (_: Exception) { credentialRemoved = false }
        finally {
            secureSignInEnabled = false; locked = false
            api.clearSession(); clearPrivateData()
        }
        message = if (credentialRemoved) "Your account and its data have been deleted."
            else "Your account has been deleted. Saved sign-in could not be cleared from this device."
    }

    fun logout() = action {
        try { api.request("/api/bonds/auth/logout", "POST", JsonRecord().apply { NativePushBridge.registration?.let { put("deviceId",it.text("deviceId")) } }) }
        finally {
            try { vault?.delete(origin) }
            finally { NativePdfInbox.dismiss(); NativeAppActions.clear(); secureSignInEnabled = false; locked = false; api.clearSession(); clearPrivateData() }
        }
    }

    override fun onCleared() {
        closeGuestMigration()
        stopGuestQuotes()
        guestJob?.cancel()
        guestImport?.clear()
        guestPortfolio = null
        documentThumbnails.clear()
        backgroundLockJob?.cancel()
        documentPreview = null
        imports.release()
        vault?.lock()
        activeApi?.close()
    }

    private fun clearPrivateData(clearWidgets: Boolean = true) {
        if (clearWidgets) HomeWidgetBridge.clear()
        NativePushBridge.reconcileReminders("")
        applicationFormsError = null
        closeGuestMigration()
        ++startupSyncRevision
        startupSyncJob?.cancel(); startupSyncJob = null
        verifyingSession = false
        portfolioSyncing = false; documentsLoading = false
        portfolioSyncError = null; documentsSyncError = null
        unlockToUi = null; deviceToUi = null
        notificationCenter.clear()
        backgroundLockJob?.cancel(); backgroundLockJob = null; backgroundedAt = null
        privateDataLoadedAt = null; marketLoadedAt = null
        privateDataAvailable = false
        documentThumbnails.clear()
        documentPreviewRequest++; documentPreview = null; loadingDocumentPreview = false
        imports.clear()
        refreshPhase = PortfolioRefreshPhase.IDLE
        monthlyInvestmentTarget = DEFAULT_MONTHLY_INVESTMENT_TARGET
        user = null
        applicationBidForm = null
        cashLedgerRequested = false; backupRequested = false; ledgerHoldingId = null
        cashLedger.clear()
        portfolio = PortfolioSnapshot()
        sharedCouponTerms = emptyList(); accountPortfolio = accountPortfolio.copy(couponReferences = emptyList())
        statements.clear()
        reports.clearProgress()
        historyComplete = false
        pendingShareOrders.clear()
        market = null
        quotes = null
        quoteRefreshFailed = false
    }
    // Main.immediate may execute startup (and its failure cleanup) inside this constructor.
    // Keep startup after every state delegate and cache has been initialized.
    init {
        if (backendOriginMigrated) {
            runCatching {
                settings.writeOrigin(origin)
                if (!isGuest) settings.writeSessionChoice(MANUAL_ACCOUNT_ENTRY)
            }
        }
        if (isGuest) { loadGuestPortfolio(); refreshGuestQuotes() } else runAction(allowLocked = true, failureMessage = CONNECTION_ERROR_MESSAGE) {
            UnlockPerformance.measure("public_assets") { loadPublicData() }
            if (!locked && !deferAccountStartup && entryChoice != MANUAL_ACCOUNT_ENTRY) checkSession()
            if (backendOriginMigrated) message = "Please sign in again to reconnect your account."
        }
    }

}


internal const val SESSION_BACKGROUND_GRACE_MILLIS = 30_000L
internal const val DATA_CACHE_MILLIS = 60_000L
