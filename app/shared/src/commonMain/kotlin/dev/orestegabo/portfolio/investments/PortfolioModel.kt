package dev.orestegabo.portfolio.investments

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
import kotlinx.coroutines.withContext
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
) : ViewModel() {
    var origin by mutableStateOf(initialOrigin ?: settings.readOrigin() ?: DEFAULT_ORIGIN)
        private set
    var api = apiFactory(origin)
        private set
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
    var secureSignInEnabled by mutableStateOf(vault?.hasSaved(origin) == true)
        private set
    var locked by mutableStateOf(secureSignInEnabled)
        private set
    var verifyingSession by mutableStateOf(false)
        private set
    private val importJournal = ImportJournal(settings)
    internal val importHistory get() = importJournal.entries
    internal val importHistoryStorageFailed get() = importJournal.persistenceFailed
    internal var activeImportId by mutableStateOf<String?>(null)
        private set
    // Selection events, rather than receipt IDs, route every upload source into chat.
    // A dismissed picker emits nothing; retrying an existing receipt emits a new event.
    internal var importSelectionRevision by mutableStateOf(0)
        private set
    private var retryPdf: ByteArray? = null
    private var resumingImport = false
    private val savedPdfDigests = mutableMapOf<String, String>()
    private var retryBondOnly = false
    private var retryStatement: JsonRecord? = null
    internal fun activeImportReceipt() = importJournal.find(activeImportId)
    private fun updateImport(change: (ImportReceipt) -> ImportReceipt) { activeImportId?.let { importJournal.update(it, change) } }
    private fun startSavingImport() { updateImport { it.copy(state = ImportState.SAVING, error = "") } }
    private fun finishImport(outcome: ImportOutcome, purchaseId: String = "", recorded: Int = 0, missing: Int = 0) {
        updateImport { it.copy(state = ImportState.SAVED, error = "", outcome = outcome,
            documentId = uploadedDocument ?: it.documentId, purchaseId = purchaseId, recorded = recorded, missing = missing) }
    }
    private fun reconcileImportHistory() {
        importHistory.filter { it.state !in listOf(ImportState.SAVED, ImportState.EXISTS) && it.documentId.isNotBlank() }.forEach { entry ->
            val purchase = portfolio.purchases.firstOrNull {
                it.text("status") != "submitted" && (entry.kind == "bond" && it.text("confirmationDocumentId") == entry.documentId ||
                    entry.kind == "bond-issuance" && entry.targetId.isNotBlank() && it.text("id") == entry.targetId &&
                    it.text("issuanceDocumentId") == entry.documentId && it.text("confirmationMethod") == "manual")
            }
            if (purchase != null) importJournal.update(entry.id) { it.copy(state = ImportState.SAVED, outcome = ImportOutcome.PURCHASE, error = "", purchaseId = purchase.text("id")) }
            else if (entry.kind == "bond-issuance" && entry.targetId.isBlank() && portfolio.documents.any { it.text("id") == entry.documentId })
                importJournal.update(entry.id) { it.copy(state = ImportState.SAVED, outcome = ImportOutcome.ISSUANCE, error = "") }
        }
    }
    private var importedPdf: ByteArray? = null
    private var importedReview: JsonRecord? = null
    internal fun currentImportReview(): JsonRecord? = importedReview
    private var pdfUploadId = ""
    private var uploadedDocument: String? = null
    private var activeJob: Job? = null
    private var generation = 0
    private var foreground = true
    private var backgroundedAt: TimeMark? = null
    private var backgroundLockJob: Job? = null
    private var privateDataLoadedAt: TimeMark? = null
    private var privateDataAvailable by mutableStateOf(false)
    val portfolioReady get() = privateDataAvailable
    // An initial snapshot is pending until a request actually fails. A not-yet-started
    // sync or blocking session/password restore must never look like a failed request.
    internal val portfolioLoading get() = portfolioSyncing || (!portfolioReady && portfolioSyncError == null)
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
    var busy by mutableStateOf(false)
        private set
    var pdfImportProgress by mutableStateOf<PdfImportProgress?>(null)
        private set
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
    var portfolio by mutableStateOf(PortfolioSnapshot())
        private set
    var notificationPreferences by mutableStateOf(JsonRecord())
        private set
    var notificationSettingsAvailable by mutableStateOf(false)
        private set
    private var pendingNotificationPreferences by mutableStateOf<Map<String, Boolean>>(emptyMap())
    val notificationSettingsSaving get() = pendingNotificationPreferences.isNotEmpty()
    private val notificationSettingsMutex = Mutex()
    fun notificationPreferenceSaving(category: String? = null) = pendingNotificationPreferences.containsKey(category ?: "autoCoupon")
    fun notificationPreferenceEnabled(category: String? = null): Boolean = pendingNotificationPreferences[category ?: "autoCoupon"]
        ?: if (category == null) notificationPreferences.optBoolean("autoCoupon")
        else notificationPreferences.optJSONObject("categories")?.optBoolean(category, true) ?: true
    var notificationDate by mutableStateOf(Today)
        private set
    var couponReceipts by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    var couponTrackingAvailable by mutableStateOf(false)
        private set
    private suspend fun fetchNotificationSettings() = notificationSettingsMutex.withLock {
        val revision=notificationGeneration;val account=user?.text("id")
        try {
            var value=api.request("/api/bonds/notifications/preferences")
            if(revision!=notificationGeneration || account!=user?.text("id") || !value.has("categories"))return@withLock
            if(!value.optBoolean("configured")) value=api.request("/api/bonds/notifications/preferences","PUT",JsonRecord().put("monthlyTarget",monthlyInvestmentTarget))
            if(revision!=notificationGeneration || account!=user?.text("id"))return@withLock
            notificationPreferences=value;notificationSettingsAvailable=true
            value.text("monthlyTarget").toLongOrNull()?.takeIf(::validInvestmentTarget)?.let {
                monthlyInvestmentTarget=it;targetAccount()?.let { account -> settings.writeMonthlyTarget(account,it) }
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) { invalidateRejectedSession(failure) }
    }
    fun updateNotificationPreference(category: String? = null, enabled: Boolean) {
        if (!notificationSettingsAvailable || notificationPreferenceSaving(category) || user == null || locked) return
        val revision = notificationGeneration
        val account = user?.text("id")
        val key = category ?: "autoCoupon"
        // Pending values are presentation-only; unrelated switches retain their acknowledged values.
        pendingNotificationPreferences = pendingNotificationPreferences + (key to enabled)
        viewModelScope.launch {
            var updated = false
            try {
                notificationSettingsMutex.withLock {
                    if (revision != notificationGeneration || account != user?.text("id") || locked) return@withLock
                    val change = JsonRecord()
                    if (category == null) change.put("autoCoupon", enabled)
                    else {
                        // Build inside the serialized write, after preceding changes were acknowledged.
                        val categories = JsonRecord(notificationPreferences.optJSONObject("categories")?.toString() ?: "{}")
                        categories.put(category, enabled); change.put("categories", categories)
                    }
                    val saved = api.request("/api/bonds/notifications/preferences", "PUT", change)
                    if (revision != notificationGeneration || account != user?.text("id")) return@withLock
                    val next = JsonRecord(notificationPreferences.toString())
                    if (category == null) {
                        check(saved.has("autoCoupon") && saved.optBoolean("autoCoupon", !enabled) == enabled)
                        next.put("autoCoupon", enabled)
                    } else {
                        val acknowledged = saved.optJSONObject("categories")
                        check(acknowledged?.has(category) == true && acknowledged.optBoolean(category, !enabled) == enabled)
                        val categories = JsonRecord(notificationPreferences.optJSONObject("categories")?.toString() ?: "{}")
                        categories.put(category, enabled); next.put("categories", categories)
                    }
                    notificationPreferences = next
                    updated = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == notificationGeneration) error = "Notification settings could not be saved." }
            finally {
                if (revision == notificationGeneration) pendingNotificationPreferences = pendingNotificationPreferences - key
            }
            if (updated && revision == notificationGeneration && account == user?.text("id")) fetchNotifications()
        }
    }
    fun confirmCoupon(purchaseId: String,dueDate: String,received: Boolean=true) = action {
        val result=api.request("/api/bonds/notifications/coupons","POST",JsonRecord().put("purchaseId",purchaseId).put("dueDate",dueDate).put("received",received))
        couponReceipts=result.rows("receipts");couponTrackingAvailable=true
        fetchNotifications()
    }
    fun couponReceipt(purchaseId: String,dueDate: String)=couponReceipts.firstOrNull { it.text("purchaseId")==purchaseId && it.text("dueDate")==dueDate }
    var notifications by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    var unreadNotifications by mutableIntStateOf(0)
        private set
    var notificationNextBefore by mutableStateOf<String?>(null)
        private set
    var notificationsLoading by mutableStateOf(false)
        private set
    var notificationsError by mutableStateOf<String?>(null)
        private set
    private var notificationLoadedAt: TimeMark? = null
    private var notificationGeneration = 0
    private suspend fun fetchNotifications(more: Boolean = false) {
        if(user==null || notificationsLoading)return
        val account=user?.text("id");val revision=notificationGeneration
        notificationsLoading=true
        try {
            if(!more)fetchNotificationSettings()
            if (user == null || locked) return
            val response=api.request("/api/bonds/notifications"+(if(more) "?before=${notificationNextBefore ?: return}" else ""))
            if(revision!=notificationGeneration || account!=user?.text("id"))return
            notificationDate=calendarDate(response.text("serverDate")) ?: Today
            try {
                val receipts=api.request("/api/bonds/notifications/coupons")
                if(revision==notificationGeneration && receipts.has("receipts")) { couponReceipts=receipts.rows("receipts");couponTrackingAvailable=true }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { }
            if(revision!=notificationGeneration || account!=user?.text("id"))return
            val rows=response.rows("notifications")
            notifications=if(more)(notifications+rows).distinctBy { it.text("id") } else rows
            unreadNotifications=response.optInt("unreadCount")
            notificationNextBefore=response.text("nextBefore").takeIf { it.isNotBlank() && it!="null" }
            notificationsError=null;notificationLoadedAt=clock.markNow()
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) {
            if (invalidateRejectedSession(failure)) return
            if(revision==notificationGeneration) notificationsError=if(failure is ApiException && failure.status==404)
                "Notifications need the updated server." else "Notifications could not be loaded. Try again."
        } finally { if(revision==notificationGeneration)notificationsLoading=false }
    }
    private suspend fun invalidateRejectedSession(failure: Exception): Boolean {
        if (failure !is ApiException || failure.status != 401) return false
        api.clearSession()
        if (secureSignInEnabled) lock() else { ++generation; clearPrivateData() }
        return true
    }
    fun refreshNotifications() { if(user!=null && !locked)viewModelScope.launch { fetchNotifications() } }
    fun moreNotifications() { if(user!=null && !locked)viewModelScope.launch { fetchNotifications(true) } }
    fun readNotification(id: String) { viewModelScope.launch {
        val revision=notificationGeneration
        try {
            api.request("/api/bonds/notifications/read","POST",JsonRecord().put("ids",JsonList(listOf(id))))
            if(revision==notificationGeneration) {
                val wasUnread=notifications.any { it.text("id")==id && !it.optBoolean("read") }
                notifications=notifications.map { if(it.text("id")==id)JsonRecord(it.toString()).put("read",true) else it }
                if(wasUnread)unreadNotifications=(unreadNotifications-1).coerceAtLeast(0)
                notificationsError=null
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { if(revision==notificationGeneration)notificationsError="Could not mark this notification as read." }
    } }
    suspend fun syncPushDevice() {
        if(user==null || locked)return
        try {
            if(NativePushBridge.deviceId.isNotBlank())api.request("/api/bonds/notifications/session-device","POST",JsonRecord().withDeviceIdentity())
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { }
        val registration=NativePushBridge.registration ?: return
        try {
            if(NativePushBridge.allowed) api.request("/api/bonds/notifications/devices","POST",JsonRecord(registration.toString()).put("language",AppLanguage.code))
            else api.request("/api/bonds/notifications/devices/${registration.text("deviceId")}","DELETE")
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { /* A provider or older API must not block the portfolio. */ }
    }
    var statementItems by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    var statementReviewRows by mutableStateOf<List<JsonRecord>?>(null)
        private set
    fun closeStatementReview() { statementReviewRows = null }
    fun showStatementRecordReview(row: JsonRecord) { statementReviewRows = listOf(row) }
    fun showStatementDocumentReview(id: String) = action {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid statement document." }
        statementReviewRows = api.request("/api/bonds/statement-items?documentId=$id").rows("items")
    }
    fun resolveStatementRecord(item: JsonRecord, purchaseId: String?, reason: String, completed: () -> Unit = {}) = action {
        val id = item.text("id")
        require(id.matches(Regex("[A-Za-z0-9_-]{1,120}")) && reason.trim().length in 3..500) { "Explain the reconciliation correction." }
        val updated = api.request("/api/bonds/statement-items/$id/resolve", "POST", JsonRecord()
            .put("action", if (purchaseId == null) "clear" else "link").put("purchaseId", purchaseId.orEmpty()).put("reason", reason.trim()).put("expectedDecisionAt", item.rows("decisions").lastOrNull()?.text("at").orEmpty())).rows("rows").single()
        statementReviewRows = statementReviewRows?.map { if (it.text("id") == id) updated else it }
        statementItems = api.request("/api/bonds/statement-items").rows("items")
        completed()
    }
    var historyComplete by mutableStateOf(false)
        private set
    private var compactOverviewSupported: Boolean? = null
    val purchases get() = portfolio.purchases
    val holdings get() = portfolio.holdings
    var monthlyInvestmentTarget by mutableStateOf(DEFAULT_MONTHLY_INVESTMENT_TARGET)
        private set
    private fun targetAccount() = user?.text("id")?.takeIf { it.isNotBlank() }?.let { "$origin|$it" }
    private fun loadInvestmentTarget() {
        importJournal.attach(targetAccount())
        monthlyInvestmentTarget = targetAccount()?.let(settings::readMonthlyTarget)?.takeIf { validInvestmentTarget(it) }
            ?: DEFAULT_MONTHLY_INVESTMENT_TARGET
    }
    fun updateMonthlyInvestmentTarget(amount: Long) {
        require(validInvestmentTarget(amount)) { "Enter a positive whole RWF amount." }
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
        if (privateDataAvailable) monthlyInvestmentProgress(portfolio, month, monthlyInvestmentTarget, Today) else null
    val documents get() = portfolio.documents
    var market by mutableStateOf<JsonRecord?>(null)
        private set
    var quotes by mutableStateOf<JsonRecord?>(null)
        private set
    var quoteRefreshFailed by mutableStateOf(false)
        private set
    private var publishedCalendar by mutableStateOf<List<JsonRecord>>(emptyList())
    val calendar get() = mergeBondIssuances(publishedCalendar, documents)
    var education by mutableStateOf<List<JsonRecord>>(emptyList())
        private set

    init { runAction(allowLocked = true, failureMessage = CONNECTION_ERROR_MESSAGE) {
        UnlockPerformance.measure("public_assets") { loadPublicData() }
        if (!locked) checkSession()
    } }

    private suspend fun loadPublicData() {
        publishedCalendar = JsonList(assets("issuance-calendar.json").decodeToString()).objects()
        education = JsonList(assets("education.json").decodeToString()).objects()
    }
    fun action(block: suspend () -> Unit) = runAction(false, block = block)
    private fun runAction(allowLocked: Boolean, failureMessage: String = GENERAL_ERROR_MESSAGE, block: suspend () -> Unit) {
        if (busy || (locked && !allowLocked)) return
        busy = true
        error = null
        message = null
        val token = ++generation
        activeJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) {
                if (token == generation && activeImportReceipt()?.state in listOf(ImportState.READING, ImportState.SAVING))
                    updateImport { it.copy(state = ImportState.CANCELLED, error = "") }
                throw cancelled
            }
            catch (failure: Exception) {
                if (token == generation) {
                    if (activeImportReceipt()?.state in listOf(ImportState.READING, ImportState.SAVING))
                        updateImport { it.copy(state = ImportState.FAILED, error = userErrorMessage(failure, failureMessage)) }
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
        val expired = backgroundGraceExpired()
        backgroundLockJob?.cancel(); backgroundLockJob = null
        backgroundedAt = null
        foreground = true
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
        clearPrivateData()
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
    private suspend fun saveSignIn(email: String, password: String): Boolean {
        val storage = requireNotNull(vault) { "Device authentication is unavailable." }
        val value = JsonRecord().put("origin", origin).put("email", email.trim()).put("password", password).put("session", api.sessionSnapshot()).toString()
        val saved = storage.save(origin, value)
        secureSignInEnabled = storage.hasSaved(origin)
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
            val storage = requireNotNull(vault)
            unlockToUi = UnlockPerformance.begin("unlock_to_first_frame")
            val raw = UnlockPerformance.measure("protected_storage_read_including_prompt") { storage.read(origin) } ?: return@runAction
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
        val candidate = value.trim().trimEnd('/')
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
        val session = api.request("/api/bonds/auth/session")
        user = if (session.optBoolean("authenticated")) session.optJSONObject("user") else null
        loadInvestmentTarget()
        if (user != null) loadPrivateData()
    }

    fun login(email: String, password: String, invitation: String = "", savePassword: Boolean = false,
        onAuthenticated: suspend () -> Unit = {}) = action {
        val register = invitation.isNotBlank()
        val response = api.request("/api/bonds/auth/${if (register) "register" else "login"}", "POST",
            JsonRecord().put("email", email.trim()).put("password", password).withDeviceIdentity().apply { if (register) put("invite", invitation.trim()) })
        val account = response.getJSONObject("user")
        // Only submit the password-manager form after the server accepts these credentials.
        // Declining/failing a password save must not turn a successful login into a failed one.
        try { onAuthenticated() }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { message = "Signed in. Your password could not be saved to the password manager." }
        user = account
        loadInvestmentTarget()
        loadPrivateData(force = true)
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
        val documents = if (deferSupplementary) portfolio.documents else api.request("/api/bonds/documents").rows("documents")
        val monthlyTotals = overview?.optJSONArray("monthlyInvestmentTotals")?.objects()
            ?: if (overview != null) try {
                // Old compact responses omit contribution totals. Fetch their source records once
                // during this sync, derive the rolling summary, then discard those extra records.
                monthlyGoalTotalsFromHistory(api.request("/api/bonds/purchases").rows("purchases"),
                    api.request("/api/bonds/equities").rows("holdings"), Today)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is ApiException && failure.status == 401) throw failure
                null // An unavailable secondary summary must not hide the rest of the portfolio.
            } else null
        fun ids(key: String): Set<String>? = overview?.optJSONArray(key)?.let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() }
        portfolio = PortfolioSnapshot(purchases, holdings, if (deferSupplementary) portfolio.documents else documents, ids("recentPurchaseIds"), ids("recentHoldingIds"),
            monthlyTotals)
        privateDataAvailable = true
        reconcileImportHistory()
        historyComplete = overview == null
        if (!deferSupplementary) {
            loadStatementItems()
            if (portfolio.activeShares(Today).isNotEmpty()) refreshEquityQuotes()
            fetchNotifications()
        }
        privateDataLoadedAt = clock.markNow()
    }

    private suspend fun loadStatementItems() {
        statementItems = try { api.request("/api/bonds/statement-items").rows("items") }
        catch (failure: ApiException) {
            if (failure.status == 401) throw failure
            if (failure.status == 404 || failure.status == 405) emptyList() else statementItems
        }
    }

    fun retryPortfolioSync() {
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
                        if (currentSync() && portfolioReady && portfolio.activeShares(Today).isNotEmpty())
                            section("quotes") { refreshEquityQuotes() }
                    }
                    launch {
                        section("documents") {
                            val rows = api.request("/api/bonds/documents").rows("documents")
                            if (currentSync()) { portfolio = portfolio.copy(documents = rows); reconcileImportHistory() }
                        }
                        if (currentSync()) documentsLoading = false
                    }
                    launch { section("statements") { loadStatementItems() } }
                    launch { section("notifications") { fetchNotifications() } }
                }
            } finally {
                if (currentSync()) { portfolioSyncing = false; documentsLoading = false }
            }
        }
    }

    fun loadInvestmentHistory(force: Boolean = false) {
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
    private suspend fun refreshEquityQuotes() {
        try {
            val latest = api.request("/api/bonds/rse/quotes?kind=equities")
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

    fun refresh() = refreshWithFeedback(showMessage = true)
    fun refreshPortfolio() = refreshWithFeedback(showMessage = false)
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
    fun loadMarket(refresh: Boolean = false) = action {
        if (!refresh && market != null && marketLoadedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < DATA_CACHE_MILLIS } == true) return@action
        marketLoadedAt = null
        market = api.request("/api/bonds/rse/market" + if (refresh) "?refresh=1" else "")
        val latest = api.request("/api/bonds/rse/quotes?kind=equities" + if (refresh) "&refresh=1" else "")
        if (latest.rows("quotes").isNotEmpty()) { quotes = latest; quoteRefreshFailed = false }
        else quoteRefreshFailed = true
        marketLoadedAt = clock.markNow()
    }

    fun importPurchasePdf(actions: NativeActions, engine: InvestmentEngine, bondOnly: Boolean = false,
        expectedStatement: JsonRecord? = null, targetPurchaseId: String? = null, retryEntryId: String? = null,
        completed: (JsonRecord) -> Unit) = action {
        require(importedReview == null || retryEntryId == activeImportId) { "Finish or cancel the current import first." }
        val previous = retryEntryId?.let { importJournal.find(it) }
        if (retryEntryId != activeImportId) retryPdf = null
        require(previous?.state !in listOf(ImportState.SAVED, ImportState.EXISTS)) { "This import is already complete." }
        importedPdf = null; importedReview = null; uploadedDocument = null
        resumingImport = previous?.documentId?.isNotBlank() == true
        retryBondOnly = bondOnly; retryStatement = expectedStatement
        var selected = false
        try {
            readPurchasePdf(actions, engine, bondOnly, expectedStatement,
                openedPdf = { bytes ->
                    val digest = withContext(importWorkDispatcher) { importPdfDigest(bytes) }
                    require(previous == null || previous.digest.isBlank() || previous.digest == digest) {
                        "This is a different PDF. Start a new import for this file."
                    }
                    retryPdf = bytes
                    updateImport { it.copy(fileName = actions.pickedPdfName?.let(::importDisplayName) ?: it.fileName, digest = digest) }
                }, selectedPdf = { importedPdf = it }, progress = { progress ->
                    if (!selected && progress?.stage == PdfImportStage.OPENING) {
                        selected = true
                        val entry = previous ?: importJournal.begin(actions.pickedPdfName ?: "Investment PDF", targetPurchaseId.orEmpty(), expectedStatement?.text("id").orEmpty())
                        activeImportId = entry.id
                        importSelectionRevision++
                        pdfUploadId = entry.id
                        uploadedDocument = entry.documentId.takeIf(String::isNotBlank)
                        updateImport { it.copy(state = ImportState.READING, error = "") }
                    }
                    pdfImportProgress = progress
                })?.let { review ->
                updateImport { it.copy(kind = review.text("kind")) }
                pdfImportProgress = PdfImportProgress(PdfImportStage.CHECKING)
                val existing = findExistingImportPdf(review)
                val alreadySaved = existing != null && showExistingImport(existing, review,
                    requireComplete = resumingImport || !targetPurchaseId.isNullOrBlank(), targetId = targetPurchaseId)
                if (!alreadySaved) {
                    if (existing != null) {
                        uploadedDocument = existing.text("id")
                        updateImport { it.copy(documentId = existing.text("id")) }
                    }
                    importedReview = review
                    updateImport { it.copy(state = ImportState.REVIEW, error = "") }
                    completed(review)
                }
            }
        } finally { pdfImportProgress = null }
    }

    private suspend fun findExistingImportPdf(review: JsonRecord): JsonRecord? {
        val bytes = importedPdf ?: retryPdf ?: return null
        val digest = activeImportReceipt()?.digest?.takeIf(String::isNotBlank)
            ?: withContext(importWorkDispatcher) { importPdfDigest(bytes) }
        // A fresh account-authorized list prevents a stale local receipt from reviving a deleted file.
        val current = api.request("/api/bonds/documents").rows("documents")
        portfolio = portfolio.copy(documents = current)
        val knownIds = importHistory.filter { it.digest == digest && it.documentId.isNotBlank() }.map { it.documentId }.toSet()
        current.firstOrNull { it.text("id") in knownIds }?.let { return it }
        val candidates = current.filter { duplicatePdfCandidate(it, review, activeImportReceipt()?.fileName.orEmpty()) }
        for (document in candidates) {
            val id = document.text("id")
            require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid document URL." }
            val existingDigest = savedPdfDigests[id] ?: run {
                val data = try { api.bytes("/api/bonds/documents/$id") }
                catch (failure: ApiException) { if (failure.status == 404) continue else throw failure }
                require(data.size in 1..MAX_PDF_BYTES && data.take(5).toByteArray().decodeToString() == "%PDF-") { "The server did not return a PDF." }
                withContext(importWorkDispatcher) { importPdfDigest(data) }.also { savedPdfDigests[id] = it }
            }
            if (existingDigest == digest) return document
        }
        return null
    }

    private suspend fun showExistingImport(document: JsonRecord, review: JsonRecord, requireComplete: Boolean = false, targetId: String? = null): Boolean {
        val id = document.text("id")
        val kind = review.text("kind")
        val prior = importHistory.firstOrNull { it.documentId == id && it.kind == kind && it.state == ImportState.SAVED }
        val records = try {
            when (kind) {
                "bond", "bond-issuance" -> api.request("/api/bonds/purchases").rows("purchases").also {
                    portfolio = portfolio.copy(purchases = it)
                }
                "equity" -> api.request("/api/bonds/equities").rows("holdings").also {
                    portfolio = portfolio.copy(holdings = it)
                }
                else -> emptyList()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (requireComplete || failure is ApiException && failure.status == 401) throw failure
            emptyList()
        }
        val purchase = completedImportPurchase(document, review, records, targetId)
        val statementRows = if (kind == "statement") try {
            api.request("/api/bonds/statement-items?documentId=$id").rows("items").filter { row -> row.rows("sources").any { it.text("documentId") == id } }
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: ApiException) {
            if (failure.status in listOf(404, 405)) emptyList() else throw failure
        } else emptyList()
        val outcome = when {
            purchase != null -> ImportOutcome.PURCHASE
            kind == "bond-issuance" && targetId.isNullOrBlank() && (document.has("issuance") || prior?.outcome == ImportOutcome.ISSUANCE) -> ImportOutcome.ISSUANCE
            kind == "statement" && (statementRows.isNotEmpty() || prior?.outcome == ImportOutcome.STATEMENT) -> ImportOutcome.STATEMENT
            else -> null
        }
        if ((requireComplete || kind == "statement") && outcome == null) return false
        updateImport { it.copy(state = ImportState.EXISTS, kind = kind, error = "",
            documentId = id, purchaseId = purchase?.text("id").orEmpty(), outcome = outcome,
            recorded = if (outcome == ImportOutcome.STATEMENT) if (statementRows.isEmpty()) prior?.recorded ?: 0 else statementRows.count { it.text("state") == "recorded" } else 0,
            missing = if (outcome == ImportOutcome.STATEMENT) if (statementRows.isEmpty()) prior?.missing ?: 0 else statementRows.count { it.text("state") in listOf("missing", "review-required") } else 0) }
        importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
        return true
    }

    /** A concurrent import may have saved this file since the review was opened. */
    private suspend fun checkExistingBeforeSave(review: JsonRecord, continueConfirmation: Boolean, completed: () -> Unit, targetId: String? = null): Boolean {
        val requestedTarget = targetId ?: activeImportReceipt()?.targetId?.takeIf(String::isNotBlank)
        if (uploadedDocument != null) {
            val existing = documents.firstOrNull { it.text("id") == uploadedDocument }
            if (existing != null && showExistingImport(existing, review, requireComplete = true,
                    targetId = requestedTarget)) {
                completed(); return true
            }
            return false
        }
        pdfImportProgress = PdfImportProgress(PdfImportStage.CHECKING)
        try {
            val existing = findExistingImportPdf(review) ?: return false
            if (continueConfirmation || resumingImport) {
                if (showExistingImport(existing, review, requireComplete = true,
                        targetId = requestedTarget)) {
                    completed(); return true
                }
                uploadedDocument = existing.text("id")
                updateImport { it.copy(documentId = existing.text("id")) }
                return false
            }
            showExistingImport(existing, review)
            completed()
            return true
        } finally { pdfImportProgress = null }
    }

    internal fun retryImport(id: String, actions: NativeActions, engine: InvestmentEngine, completed: (JsonRecord) -> Unit) {
        val entry = importJournal.find(id) ?: return
        val same = id == activeImportId && retryPdf != null
        val target = entry.targetId.takeIf(String::isNotBlank)
        val statement = if (same) retryStatement else statementItems.firstOrNull { it.text("id") == entry.statementId }
        val picker = if (same) ReceivedPdfActions(actions, retryPdf!!, entry.fileName) else actions
        importPurchasePdf(picker, engine, bondOnly = if (same) retryBondOnly else target != null,
            expectedStatement = statement, targetPurchaseId = target, retryEntryId = id, completed = completed)
    }

    internal fun cancelImportReview(review: JsonRecord) { discardImportedPdf(review) }

    fun discardImportedPdf(review: JsonRecord) {
        if (importedReview === review) {
            if (activeImportReceipt()?.state !in listOf(ImportState.SAVED, ImportState.EXISTS)) updateImport { it.copy(state = ImportState.CANCELLED, error = "") }
            importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
        }
    }

    fun cancelPdfImport() { if (pdfImportProgress != null) activeJob?.cancel() }

    fun checkStatement(review: JsonRecord, completed: (List<JsonRecord>) -> Unit) = action {
        val rows = review.rows("rows").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        val movements = review.rows("movements").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        require(rows.size + movements.size in 1..500) { "No records were found in this statement." }
        val response = api.request("/api/bonds/statement-items", "POST", JsonRecord().put("items", JsonList(rows)).put("movements", JsonList(movements)))
        statementItems = api.request("/api/bonds/statement-items").rows("items")
        completed(response.rows("rows"))
    }

    fun saveImportedPurchase(review: JsonRecord, purchase: JsonRecord, id: String?, completed: () -> Unit) = action {
        require(importedReview === review && importedPdf != null) { "Choose the PDF again before confirming." }
        startSavingImport()
        if (checkExistingBeforeSave(review, id != null, completed, targetId = id)) return@action
        val bond = review.text("kind") == "bond"
        val reusingDocument = uploadedDocument != null
        try {
            if (uploadedDocument == null) {
                val pdf = importedPdf!!
                val chunkSize = 512 * 1024
                val count = (pdf.size + chunkSize - 1) / chunkSize
                for (index in 0 until count) {
                    pdfImportProgress = PdfImportProgress(PdfImportStage.UPLOADING, "${index + 1} / $count")
                    val metadata = JsonRecord().put("label", if (bond) "Bond confirmation" else "Share purchase contract")
                        .put("instrumentName", purchase.text(if (bond) "bondName" else "securityName"))
                        .put("issuer", purchase.text(if (bond) "issuer" else "broker"))
                        .put("documentDate", purchase.text(if (bond) "purchaseDate" else "tradeDate"))
                        .put("category", if (bond) "Bond confirmation" else "Share contract")
                    val result = api.request("/api/bonds/documents", "POST", JsonRecord()
                        .put("uploadId", pdfUploadId).put("index", index).put("count", count)
                        .put("content", Base64.encode(pdf.copyOfRange(index * chunkSize, minOf(pdf.size, (index + 1) * chunkSize))))
                        .put("metadata", metadata))
                    if (index == count - 1) {
                        uploadedDocument = result.text("documentId").takeIf { it.isNotBlank() }
                        updateImport { it.copy(documentId = uploadedDocument.orEmpty()) }
                    }
                }
                require(uploadedDocument != null) { "The PDF could not be saved. Try again." }
            }
            pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
            require(bond || !reusingDocument || purchase.text("contractNote").isNotBlank()) {
                "This import may already be saved. Check your portfolio before trying again."
            }
            val storedDocument = uploadedDocument.orEmpty()
            // Resolve a completed write before retrying a new-record POST after a lost response.
            val existingMatches = if (bond && id == null) api.request("/api/bonds/purchases").rows("purchases")
                .filter { it.text("confirmationDocumentId") == storedDocument && it.text("status") != "submitted" }
                else if (!bond && purchase.text("contractNote").isNotBlank()) api.request("/api/bonds/equities").rows("holdings")
                    .filter { it.text("contractNote") == purchase.text("contractNote") && it.text("ticker") == purchase.text("ticker") && it.text("tradeDate") == purchase.text("tradeDate") }
                else emptyList()
            require(existingMatches.size <= 1) { "This import may already be saved. Check your portfolio before trying again." }
            val existing = existingMatches.singleOrNull()
            require(bond || existing == null || existing.text("broker").equals(purchase.text("broker"), true) &&
                listOf("shares", "pricePerShare", "netAmountPayable").all { existing.optDouble(it) == purchase.optDouble(it) }) {
                "The saved purchase differs from this PDF. Review your portfolio before importing again."
            }
            val response = if (existing != null) JsonRecord().put("id", existing.text("id")) else api.request(if (bond) "/api/bonds/purchases" + (id?.let { "/$it" } ?: "") else "/api/bonds/equities",
                if (bond && id != null) "PUT" else "POST", JsonRecord(purchase.toString()).apply { if(bond)put("confirmationDocumentId",uploadedDocument) else put("contractDocumentId",uploadedDocument) })
            val purchaseId = id ?: response.optJSONObject(if (bond) "purchase" else "holding")?.text("id")
                ?: response.text("id")
            finishImport(ImportOutcome.PURCHASE, purchaseId)
            importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
            completed()
            refreshAfterWrite("Purchase and PDF saved.")
        } finally { pdfImportProgress = null }
    }

    internal fun saveImportedStatement(review: JsonRecord, completed: () -> Unit) = action {
        require(importedReview === review && importedPdf != null && review.text("kind") == "statement") { "Choose the PDF again before saving." }
        val rows = review.rows("rows").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        val movements = review.rows("movements").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        require(rows.size + movements.size in 1..500) { "No records were found in this statement." }
        startSavingImport()
        if (checkExistingBeforeSave(review, false, completed)) return@action
        try {
            if (uploadedDocument == null) {
                val pdf = importedPdf!!
                val chunkSize = 512 * 1024
                val count = (pdf.size + chunkSize - 1) / chunkSize
                for (index in 0 until count) {
                    pdfImportProgress = PdfImportProgress(PdfImportStage.UPLOADING, "${index + 1} / $count")
                    val result = api.request("/api/bonds/documents", "POST", JsonRecord().put("uploadId", pdfUploadId)
                        .put("index", index).put("count", count)
                        .put("content", Base64.encode(pdf.copyOfRange(index * chunkSize, minOf(pdf.size, (index + 1) * chunkSize))))
                        .put("metadata", JsonRecord().put("label", "BK Capital statement").put("category", "Statement")
                            .put("instrumentName", "Investment account statement").put("issuer", "BK Capital").put("documentDate", review.text("toDate"))))
                    if (index == count - 1) {
                        uploadedDocument = result.text("documentId").takeIf(String::isNotBlank)
                        updateImport { it.copy(documentId = uploadedDocument.orEmpty()) }
                    }
                }
                require(uploadedDocument != null) { "The PDF could not be saved. Try again." }
            }
            pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
            val result = api.request("/api/bonds/statement-items", "POST", JsonRecord().put("items", JsonList(rows)).put("movements", JsonList(movements)).put("documentId", uploadedDocument))
            val checked = result.rows("rows")
            require(checked.isNotEmpty()) { "Statement results could not be verified. Try again." }
            finishImport(ImportOutcome.STATEMENT, recorded = checked.count { it.text("state") == "recorded" }, missing = checked.count { it.text("state") in listOf("missing", "review-required") })
            discardImportedPdf(review)
            completed()
            refreshAfterWrite("Statement PDF saved and records checked.")
        } finally { pdfImportProgress = null }
    }

    fun confirmPendingPurchase(purchase: JsonRecord,completed: () -> Unit = {}) = action {
        require(purchase.optBoolean("canConfirmWithoutPdf")) { "Use your personal PDF to confirm the final allocation and price." }
        api.request("/api/bonds/purchases/${purchase.text("id")}/confirm","POST",JsonRecord().put("confirmed",true).put("issuanceDocumentId",purchase.text("issuanceDocumentId")))
        completed()
        refreshAfterWrite("Purchase confirmed. No personal PDF was added.")
    }

    fun saveImportedIssuance(review: JsonRecord, confirmationTarget: JsonRecord? = null, completed: () -> Unit) = action {
        require(importedReview === review && importedPdf != null) { "Choose the PDF again before saving." }
        val issuance = importedIssuanceInput(review)
        val expectedConfirmation = confirmationTarget?.let { pendingIssuanceConfirmation(review, it) }
        startSavingImport()
        if (checkExistingBeforeSave(review, confirmationTarget != null, completed, targetId = confirmationTarget?.text("id"))) return@action
        try {
            val pdf = importedPdf!!
            val chunkSize = 512 * 1024
            val count = (pdf.size + chunkSize - 1) / chunkSize
            val metadata = JsonRecord().put("label", "Bond issuance results").put("category", "Bond issuance")
                .put("instrumentName", issuance.text("bondName")).put("issuer", issuance.text("issuer"))
                .put("documentDate", issuance.text("issuanceDate")).put("issuance", issuance)
            var duplicate = false
            if (uploadedDocument == null) for (index in 0 until count) {
                pdfImportProgress = PdfImportProgress(PdfImportStage.UPLOADING, "${index + 1} / $count")
                val response = api.request("/api/bonds/documents", "POST", JsonRecord()
                    .put("uploadId", pdfUploadId).put("index", index).put("count", count)
                    .put("content", Base64.encode(pdf.copyOfRange(index * chunkSize, minOf(pdf.size, (index + 1) * chunkSize))))
                    .put("metadata", metadata))
                if (index == count - 1) {
                    uploadedDocument = response.text("documentId").takeIf { it.isNotBlank() }
                    require(uploadedDocument != null) { "The issuance report could not be saved. Try again." }
                    updateImport { it.copy(documentId = uploadedDocument.orEmpty()) }
                    duplicate = response.optBoolean("duplicate")
                }
            }
            if (confirmationTarget != null && expectedConfirmation != null) {
                pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
                val current = api.request("/api/bonds/purchases").rows("purchases")
                    .singleOrNull { it.text("id") == confirmationTarget.text("id") }
                    ?: error("This pending purchase is no longer available. Refresh your portfolio.")
                require(current.text("issuanceDocumentId") == uploadedDocument) { "This PDF does not match the pending bond or applied amount." }
                // A retry after a lost success response must not apply the purchase twice.
                if (current.text("status") == "active") {
                    require(current.text("confirmationMethod") == "manual" &&
                        listOf("faceValue", "feesPaid", "pricePercent", "couponRate").all {
                            current.optDouble(it, Double.NaN) == expectedConfirmation.optDouble(it, Double.NaN)
                        }) { "The purchase changed. Review its latest details before confirming." }
                } else {
                    val latest = pendingIssuanceConfirmation(review, current)
                    require(current.optBoolean("canConfirmWithoutPdf")) { "Use your personal PDF to confirm the final allocation and price." }
                    require(listOf("faceValue", "feesPaid", "accruedInterestPaid", "amountInvested").all {
                            latest.optDouble(it) == expectedConfirmation.optDouble(it)
                        }) { "The purchase changed. Review its latest details before confirming." }
                    api.request("/api/bonds/purchases/${current.text("id")}/confirm", "POST",
                        JsonRecord().put("confirmed", true).put("issuanceDocumentId", uploadedDocument))
                }
            }
            finishImport(if (confirmationTarget != null) ImportOutcome.PURCHASE else ImportOutcome.ISSUANCE, confirmationTarget?.text("id").orEmpty())
            discardImportedPdf(review)
            completed()
            refreshAfterWrite(if (confirmationTarget != null) "PDF saved and purchase confirmed." else if (duplicate) "This issuance is already saved. No extra copy was stored." else "Bond issuance and PDF saved.")
        } finally { pdfImportProgress = null }
    }

    fun savePurchase(purchase: JsonRecord, id: String?, completed: () -> Unit) = action {
        api.request("/api/bonds/purchases" + (id?.let { "/$it" } ?: ""), if (id == null) "POST" else "PUT", purchase)
        completed()
        refreshAfterWrite("Purchase saved to your portfolio.")
    }

    fun deletePurchase(id: String, completed: () -> Unit) = action {
        api.request("/api/bonds/purchases/$id", "DELETE")
        completed()
        refreshAfterWrite("Purchase deleted.")
    }

    fun saveEquity(holding: JsonRecord, completed: () -> Unit) = action {
        api.request("/api/bonds/equities", "POST", holding)
        completed()
        refreshAfterWrite("Share purchase saved.")
    }

    private suspend fun refreshAfterWrite(success: String) {
        message = success
        try { loadPrivateData(force = true) }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            error("$success Could not refresh the portfolio. Pull down on Portfolio to update; do not submit it again.")
        }
    }

    var reportProgress by mutableStateOf<String?>(null)
        private set
    fun exportPortfolioReport(actions: NativeActions) = action {
        val account = requireNotNull(user)
        val language = AppLanguage.code
        val target = monthlyInvestmentTarget
        val date = Today
        try {
            reportProgress = "Loading all investment years…"
            // Export requires complete, fresh records. Do not inflate the dashboard's compact cache.
            val bonds = api.request("/api/bonds/purchases").rows("purchases")
            val shares = api.request("/api/bonds/equities").rows("holdings")
            val files = api.request("/api/bonds/documents").rows("documents")
            val missing = try { api.request("/api/bonds/statement-items").rows("items") }
            catch (failure: ApiException) {
                if (failure.status == 404 || failure.status == 405) emptyList() else throw failure
            }
            val latestQuotes = if (shares.isEmpty()) null else try { api.request("/api/bonds/rse/quotes?kind=equities") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure; null }
            val receipts=try { api.request("/api/bonds/notifications/coupons").let { if(it.has("receipts")) it.rows("receipts") else null } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { null }
            reportProgress = "Building your report…"
            val bytes = withContext(Dispatchers.Default) {
                generatePortfolioReport(PortfolioReportData(PortfolioSnapshot(bonds, shares, files), latestQuotes,
                    missing, account.text("email"), target, date, language, receipts))
            }
            reportProgress = "Choose where to share the PDF"
            if (actions.sharePdf("Struct-Capital-$date.pdf", bytes)) {
                message = "Portfolio report ready to share."
                try {
                    api.request("/api/bonds/notifications/report","POST",JsonRecord().put("date",date.toString())
                        .put("exportId",Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }))
                    fetchNotifications()
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { /* Exporting the report remains successful if its optional notice fails. */ }
            }
        } finally { reportProgress = null }
    }

    var annualReportYear by mutableStateOf<Int?>(null)
        private set
    private suspend fun annualReportPdf(year: Int): ByteArray {
        val account = requireNotNull(user)
        val date = bondSubscriptionDate(kotlin.time.Clock.System.now())
        val language = AppLanguage.code
        annualReportYear = year
        try {
            reportProgress = "Loading annual investment records…"
            val bonds = api.request("/api/bonds/purchases").rows("purchases")
            val shares = api.request("/api/bonds/equities").rows("holdings")
            val data = PortfolioReportData(PortfolioSnapshot(bonds, shares), null, emptyList(), account.text("email"),
                monthlyInvestmentTarget, date, language)
            val scope = annualReportScope(data, year)
            val histories = mutableMapOf<String, JsonRecord>()
            scope.closingShares.filter { it.text("currency") == "RWF" }.map { it.text("ticker") }.distinct().forEach { ticker ->
                require(ticker.matches(Regex("[A-Za-z0-9._-]{1,24}"))) { "Invalid security ticker." }
                try { histories[ticker] = api.request("/api/bonds/rse/equity-history?tickers=$ticker&period=all") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure }
            }
            val receipts = try { api.request("/api/bonds/notifications/coupons").let { if (it.has("receipts")) it.rows("receipts") else null } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure; null }
            reportProgress = "Building your report…"
            return withContext(reportWorkDispatcher) { generateAnnualReport(data.copy(couponReceipts = receipts), year, histories) }
        } finally { reportProgress = null; annualReportYear = null }
    }
    fun exportAnnualReport(year: Int, actions: NativeActions) = action {
        val bytes = annualReportPdf(year)
        if (actions.sharePdf("Struct-Capital-Annual-$year.pdf", bytes)) message = "Annual report ready to share."
    }

    internal var documentPreview by mutableStateOf<DocumentPreview?>(null)
        private set
    private var loadingDocumentPreview = false
    private var documentPreviewRequest = 0L
    internal fun openDocumentPreview(document: JsonRecord) = action {
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
            finally { NativePdfInbox.dismiss(); secureSignInEnabled = false; locked = false; api.clearSession(); clearPrivateData() }
        }
    }

    override fun onCleared() { backgroundLockJob?.cancel(); documentPreview = null; importedPdf = null; importedReview = null; retryPdf = null; vault?.lock(); api.close() }

    private fun clearPrivateData() {
        ++startupSyncRevision
        startupSyncJob?.cancel(); startupSyncJob = null
        verifyingSession = false
        portfolioSyncing = false; documentsLoading = false
        portfolioSyncError = null; documentsSyncError = null
        unlockToUi = null; deviceToUi = null
        notificationPreferences=JsonRecord();notificationSettingsAvailable=false;pendingNotificationPreferences=emptyMap()
        couponReceipts=emptyList();couponTrackingAvailable=false;notificationDate=Today
        notificationGeneration++;notifications=emptyList();unreadNotifications=0;notificationNextBefore=null
        notificationsError=null;notificationsLoading=false;notificationLoadedAt=null
        backgroundLockJob?.cancel(); backgroundLockJob = null; backgroundedAt = null
        privateDataLoadedAt = null; marketLoadedAt = null
        privateDataAvailable = false
        importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
        documentPreviewRequest++; documentPreview = null; loadingDocumentPreview = false
        activeImportId = null; importJournal.detach(); savedPdfDigests.clear(); resumingImport = false
        refreshPhase = PortfolioRefreshPhase.IDLE
        monthlyInvestmentTarget = DEFAULT_MONTHLY_INVESTMENT_TARGET
        user = null
        portfolio = PortfolioSnapshot()
        statementItems = emptyList()
        statementReviewRows = null
        pdfImportProgress = null
        reportProgress = null
        historyComplete = false
        market = null
        quotes = null
        quoteRefreshFailed = false
    }
}

internal const val SESSION_BACKGROUND_GRACE_MILLIS = 30_000L
internal const val DATA_CACHE_MILLIS = 60_000L
