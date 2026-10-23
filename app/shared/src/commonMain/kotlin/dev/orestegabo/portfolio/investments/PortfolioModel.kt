package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class PortfolioModel(
    private val settings: ServerSettings,
    private val assets: suspend (String) -> ByteArray = ::readSharedAsset,
    private val vault: SecureSignIn? = null,
    private val apiFactory: (String) -> PortfolioApi = { PortfolioApi(it) },
) : ViewModel() {
    var origin by mutableStateOf(settings.readOrigin() ?: DEFAULT_ORIGIN)
        private set
    var api = apiFactory(origin)
        private set
    val secureSignInAvailable get() = vault?.available == true
    val unlockLabel get() = vault?.unlockLabel ?: "Device unlock"
    var secureSignInEnabled by mutableStateOf(vault?.hasSaved(origin) == true)
        private set
    var locked by mutableStateOf(secureSignInEnabled)
        private set
    private var activeJob: Job? = null
    private var generation = 0
    private var foreground = true
    var user by mutableStateOf<JsonRecord?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var portfolio by mutableStateOf(PortfolioSnapshot())
        private set
    var historyComplete by mutableStateOf(false)
        private set
    private var compactOverviewSupported: Boolean? = null
    val purchases get() = portfolio.purchases
    val holdings get() = portfolio.holdings
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

    init { runAction(allowLocked = true) {
        loadPublicData()
        if (!locked) checkSession()
    } }

    private suspend fun loadPublicData() {
        calendar = JsonList(assets("issuance-calendar.json").decodeToString()).objects()
        education = JsonList(assets("education.json").decodeToString()).objects()
    }
    fun action(block: suspend () -> Unit) = runAction(false, block)
    private fun runAction(allowLocked: Boolean, block: suspend () -> Unit) {
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
                    error = failure.message?.takeIf { it.isNotBlank() } ?: "Could not connect. Please try again."
                }
            } finally { if (token == generation) { busy = false; activeJob = null } }
        }
    }
    fun foreground() { foreground = true }
    fun background(systemInteraction: Boolean = false) {
        foreground = false
        if (!systemInteraction && vault?.authenticating != true) lock()
    }
    fun lock() {
        if (!secureSignInEnabled) return
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
        if (!foreground && secureSignInEnabled) lock()
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
    fun unlock() = runAction(allowLocked = true) {
        val storage = requireNotNull(vault)
        val raw = storage.read(origin) ?: return@runAction
        require(raw.length <= 48_000) { "Saved sign-in is invalid." }
        val saved = runCatching { JsonRecord(raw) }.getOrElse { error("Saved sign-in is invalid. Use your password to sign in again.") }
        require(saved.text("origin") == origin) { "Saved sign-in belongs to another server." }
        val email = saved.text("email")
        val password = saved.text("password")
        require(email.isNotBlank() && password.length in 1..128) { "Saved sign-in is invalid." }
        if (!foreground) { lock(); return@runAction }
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
        if (!foreground) { lock(); return@runAction }
        user = account
        locked = false
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
        loadPrivateData()
        if (savePassword) saveSignIn(email, password)
    }

    suspend fun loadPrivateData() {
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
        fun ids(key: String): Set<String>? = overview?.optJSONArray(key)?.let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() }
        portfolio = PortfolioSnapshot(purchases, holdings, documents, ids("recentPurchaseIds"), ids("recentHoldingIds"))
        historyComplete = overview == null
        if (portfolio.activeShares(Today).isNotEmpty()) refreshEquityQuotes()
    }

    fun loadInvestmentHistory(force: Boolean = false) {
        if (historyComplete && !force) return
        action {
            val purchases = api.request("/api/bonds/purchases").rows("purchases")
            val holdings = api.request("/api/bonds/equities").rows("holdings")
            portfolio = PortfolioSnapshot(purchases, holdings, portfolio.documents)
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

    fun refresh() = action { loadPrivateData(); message = "Portfolio updated from your server." }
    fun loadMarket(refresh: Boolean = false) = action {
        market = api.request("/api/bonds/rse/market" + if (refresh) "?refresh=1" else "")
        val latest = api.request("/api/bonds/rse/quotes?kind=equities" + if (refresh) "&refresh=1" else "")
        if (latest.rows("quotes").isNotEmpty()) { quotes = latest; quoteRefreshFailed = false }
        else quoteRefreshFailed = true
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
        try { loadPrivateData() }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            error("$success Could not refresh the portfolio. Tap Sync to update; do not submit it again.")
        }
    }

    fun logout() = action {
        try { api.request("/api/bonds/auth/logout", "POST") }
        finally {
            try { vault?.delete(origin) }
            finally { secureSignInEnabled = false; locked = false; api.clearSession(); clearPrivateData() }
        }
    }

    override fun onCleared() { vault?.lock(); api.close() }

    private fun clearPrivateData() {
        user = null
        portfolio = PortfolioSnapshot()
        historyComplete = false
        market = null
        quotes = null
        quoteRefreshFailed = false
    }
}
