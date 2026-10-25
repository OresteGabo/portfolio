package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

class PortfolioModel(
    private val settings: ServerSettings,
    private val assets: suspend (String) -> ByteArray = ::readSharedAsset,
    private val vault: SecureSignIn? = null,
    private val apiFactory: (String) -> PortfolioApi = { PortfolioApi(it) },
    private val captureMaskRequired: Boolean? = null,
    private val clock: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    var origin by mutableStateOf(settings.readOrigin() ?: DEFAULT_ORIGIN)
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
    private var importedPdf: ByteArray? = null
    private var importedReview: JsonRecord? = null
    private var pdfUploadId = ""
    private var uploadedDocument: String? = null
    private var activeJob: Job? = null
    private var generation = 0
    private var foreground = true
    private var backgroundedAt: TimeMark? = null
    private var backgroundLockJob: Job? = null
    private var privateDataLoadedAt: TimeMark? = null
    private var privateDataAvailable by mutableStateOf(false)
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
    var statementItems by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    var historyComplete by mutableStateOf(false)
        private set
    private var compactOverviewSupported: Boolean? = null
    val purchases get() = portfolio.purchases
    val holdings get() = portfolio.holdings
    var monthlyInvestmentTarget by mutableStateOf(DEFAULT_MONTHLY_INVESTMENT_TARGET)
        private set
    private fun targetAccount() = user?.text("id")?.takeIf { it.isNotBlank() }?.let { "$origin|$it" }
    private fun loadInvestmentTarget() {
        monthlyInvestmentTarget = targetAccount()?.let(settings::readMonthlyTarget)?.takeIf { validInvestmentTarget(it) }
            ?: DEFAULT_MONTHLY_INVESTMENT_TARGET
    }
    fun updateMonthlyInvestmentTarget(amount: Long) {
        require(validInvestmentTarget(amount)) { "Enter a positive whole RWF amount." }
        val account = requireNotNull(targetAccount())
        settings.writeMonthlyTarget(account, amount)
        monthlyInvestmentTarget = amount
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
    var calendar by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    var education by mutableStateOf<List<JsonRecord>>(emptyList())
        private set

    init { runAction(allowLocked = true, failureMessage = CONNECTION_ERROR_MESSAGE) {
        loadPublicData()
        if (!locked) checkSession()
    } }

    private suspend fun loadPublicData() {
        calendar = JsonList(assets("issuance-calendar.json").decodeToString()).objects()
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
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (token == generation) {
                    if (failure is ApiException && failure.status == 401) {
                        api.clearSession(); clearPrivateData()
                        locked = secureSignInEnabled
                        if (locked) vault?.lock()
                    }
                    if (locked) { vault?.lock(); api.close(); api = apiFactory(origin); clearPrivateData() }
                    error = userErrorMessage(failure, failureMessage)
                }
            } finally { if (token == generation) { busy = false; activeJob = null; tryAutomaticUnlock() } }
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
    fun usePassword() = runAction(allowLocked = true) {
        vault?.delete(origin)
        secureSignInEnabled = false; locked = false
        api.close(); api = apiFactory(origin)
        clearPrivateData(); error = null; message = null
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
            val raw = storage.read(origin) ?: return@runAction
            require(raw.length <= 48_000) { "Saved sign-in is invalid." }
            val saved = runCatching { JsonRecord(raw) }.getOrElse { error("Saved sign-in is invalid. Use your password to sign in again.") }
            require(saved.text("origin") == origin) { "Saved sign-in belongs to another server." }
            val email = saved.text("email")
            val password = saved.text("password")
            require(email.isNotBlank() && password.length in 1..128) { "Saved sign-in is invalid." }
            if (!foreground && backgroundGraceExpired()) { lock(); return@runAction }
            try { api.restoreSession(saved.text("session")) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { api.clearSession() }
            val session = try { api.request("/api/bonds/auth/session") }
            catch (failure: ApiException) { if (failure.status == 401) JsonRecord().put("authenticated", false) else throw failure }
            var account = session.optJSONObject("user").takeIf { session.optBoolean("authenticated") && it?.text("email").equals(email, ignoreCase = true) }
            if (account == null) {
                api.clearSession()
                try {
                    account = api.request("/api/bonds/auth/login", "POST", JsonRecord().put("email", email).put("password", password)).getJSONObject("user")
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
            loadPrivateData()
            storage.save(origin, JsonRecord().put("origin", origin).put("email", email).put("password", password).put("session", api.sessionSnapshot()).toString())
            if (!foreground && backgroundGraceExpired()) { lock(); return@runAction }
            user = account
            loadInvestmentTarget()
            locked = false
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
            JsonRecord().put("email", email.trim()).put("password", password).apply { if (register) put("invite", invitation.trim()) })
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

    suspend fun loadPrivateData(force: Boolean = false) {
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
        val purchases = overview?.rows("purchases") ?: api.request("/api/bonds/purchases").rows("purchases")
        val holdings = overview?.rows("holdings") ?: api.request("/api/bonds/equities").rows("holdings")
        val documents = api.request("/api/bonds/documents").rows("documents")
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
        portfolio = PortfolioSnapshot(purchases, holdings, documents, ids("recentPurchaseIds"), ids("recentHoldingIds"),
            monthlyTotals)
        privateDataAvailable = true
        statementItems = try { api.request("/api/bonds/statement-items").rows("items") }
        catch (failure: ApiException) {
            if (failure.status == 401) throw failure
            if (failure.status == 404 || failure.status == 405) emptyList() else statementItems
        }
        historyComplete = overview == null
        if (portfolio.activeShares(Today).isNotEmpty()) refreshEquityQuotes()
        privateDataLoadedAt = clock.markNow()
    }

    fun loadInvestmentHistory(force: Boolean = false) {
        if (historyComplete && !force) return
        action {
            val purchases = api.request("/api/bonds/purchases").rows("purchases")
            val holdings = api.request("/api/bonds/equities").rows("holdings")
            portfolio = PortfolioSnapshot(purchases, holdings, portfolio.documents)
            privateDataAvailable = true
            historyComplete = true
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
        catch (_: Exception) { quoteRefreshFailed = true }
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
        expectedStatement: JsonRecord? = null, completed: (JsonRecord) -> Unit) = action {
        importedPdf = null; importedReview = null; uploadedDocument = null
        pdfUploadId = Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        try { readPurchasePdf(actions, engine, bondOnly, expectedStatement,
            selectedPdf = { importedPdf = it }, progress = { pdfImportProgress = it })?.let {
            importedReview = it
            completed(it)
        } }
        finally { pdfImportProgress = null }
    }

    fun discardImportedPdf(review: JsonRecord) {
        if (importedReview === review) { importedPdf = null; importedReview = null; uploadedDocument = null }
    }

    fun cancelPdfImport() { if (pdfImportProgress != null) activeJob?.cancel() }

    fun checkStatement(review: JsonRecord, completed: (List<JsonRecord>) -> Unit) = action {
        val rows = review.rows("rows").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        require(rows.isNotEmpty() && rows.size <= 500) { "No purchases were found in this statement." }
        val response = api.request("/api/bonds/statement-items", "POST", JsonRecord().put("items", JsonList(rows)))
        statementItems = api.request("/api/bonds/statement-items").rows("items")
        completed(response.rows("rows"))
    }

    fun saveImportedPurchase(review: JsonRecord, purchase: JsonRecord, id: String?, completed: () -> Unit) = action {
        require(importedReview === review && importedPdf != null) { "Choose the PDF again before confirming." }
        val bond = review.text("kind") == "bond"
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
                    if (index == count - 1) uploadedDocument = result.text("documentId").takeIf { it.isNotBlank() }
                }
                require(uploadedDocument != null) { "The PDF could not be saved. Try again." }
            }
            pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
            api.request(if (bond) "/api/bonds/purchases" + (id?.let { "/$it" } ?: "") else "/api/bonds/equities",
                if (bond && id != null) "PUT" else "POST", purchase)
            importedPdf = null; importedReview = null; uploadedDocument = null
            completed()
            refreshAfterWrite("Purchase and PDF saved.")
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

    fun deleteAccount(password: String) = action {
        check(canDeleteAccount) { "Only invited accounts can be deleted here." }
        val response = api.request("/api/bonds/auth/account", "DELETE", JsonRecord().put("password", password))
        check(response.optBoolean("deleted")) { "Account deletion was not confirmed. Please try again." }
        var credentialRemoved = true
        try { targetAccount()?.let(settings::forgetMonthlyTarget); vault?.delete(origin) }
        catch (_: Exception) { credentialRemoved = false }
        finally {
            secureSignInEnabled = false; locked = false
            api.clearSession(); clearPrivateData()
        }
        message = if (credentialRemoved) "Your account and its data have been deleted."
            else "Your account has been deleted. Saved sign-in could not be cleared from this device."
    }

    fun logout() = action {
        try { api.request("/api/bonds/auth/logout", "POST") }
        finally {
            try { vault?.delete(origin) }
            finally { secureSignInEnabled = false; locked = false; api.clearSession(); clearPrivateData() }
        }
    }

    override fun onCleared() { backgroundLockJob?.cancel(); importedPdf = null; importedReview = null; vault?.lock(); api.close() }

    private fun clearPrivateData() {
        backgroundLockJob?.cancel(); backgroundLockJob = null; backgroundedAt = null
        privateDataLoadedAt = null; marketLoadedAt = null
        privateDataAvailable = false
        importedPdf = null; importedReview = null; uploadedDocument = null
        refreshPhase = PortfolioRefreshPhase.IDLE
        monthlyInvestmentTarget = DEFAULT_MONTHLY_INVESTMENT_TARGET
        user = null
        portfolio = PortfolioSnapshot()
        statementItems = emptyList()
        pdfImportProgress = null
        historyComplete = false
        market = null
        quotes = null
        quoteRefreshFailed = false
    }
}

internal const val SESSION_BACKGROUND_GRACE_MILLIS = 30_000L
internal const val DATA_CACHE_MILLIS = 60_000L
