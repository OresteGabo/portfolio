package dev.orestegabo.portfolio.investments

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PortfolioModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("connection", 0)
    var origin by mutableStateOf(preferences.getString("origin", "https://bonds.orestegabo.dev")!!)
        private set
    var api = PortfolioApi(origin)
        private set
    var user by mutableStateOf<JSONObject?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var purchases by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var holdings by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var documents by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var market by mutableStateOf<JSONObject?>(null)
        private set
    var quotes by mutableStateOf<JSONObject?>(null)
        private set
    val calendar = JSONArray(application.assets.open("issuance-calendar.json").bufferedReader().use { it.readText() }).objects()

    init { action { checkSession() } }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            message = null
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is ApiException && failure.status == 401) clearPrivateData()
                error = failure.message?.takeIf { it.isNotBlank() } ?: "Could not connect. Please try again."
            } finally { busy = false }
        }
    }

    fun connect(value: String) = action {
        val candidate = value.trim().trimEnd('/')
        val next = PortfolioApi(candidate)
        clearPrivateData()
        api.clearSession()
        api = next
        origin = candidate
        preferences.edit().putString("origin", origin).apply()
        checkSession()
    }

    private suspend fun checkSession() {
        val session = api.request("/api/bonds/auth/session")
        user = if (session.optBoolean("authenticated")) session.optJSONObject("user") else null
        if (user != null) loadPrivateData()
    }

    fun login(email: String, password: String, invitation: String = "") = action {
        val register = invitation.isNotBlank()
        val response = api.request("/api/bonds/auth/${if (register) "register" else "login"}", "POST",
            JSONObject().put("email", email.trim()).put("password", password).apply { if (register) put("invite", invitation.trim()) })
        user = response.getJSONObject("user")
        loadPrivateData()
    }

    suspend fun loadPrivateData() {
        // Apply each result only after it succeeds; a failure remains visible instead of looking like an empty portfolio.
        purchases = api.request("/api/bonds/purchases").rows("purchases")
        holdings = api.request("/api/bonds/equities").rows("holdings")
        documents = api.request("/api/bonds/documents").rows("documents")
    }

    fun refresh() = action { loadPrivateData(); message = "Portfolio updated from your server." }
    fun loadMarket(refresh: Boolean = false) = action {
        market = api.request("/api/bonds/rse/market" + if (refresh) "?refresh=1" else "")
        quotes = api.request("/api/bonds/rse/quotes?kind=equities" + if (refresh) "&refresh=1" else "")
    }

    fun savePurchase(purchase: JSONObject, id: String?, completed: () -> Unit) = action {
        api.request("/api/bonds/purchases" + (id?.let { "/$it" } ?: ""), if (id == null) "POST" else "PUT", purchase)
        loadPrivateData()
        completed()
        message = "Purchase saved to your portfolio."
    }

    fun deletePurchase(id: String, completed: () -> Unit) = action {
        api.request("/api/bonds/purchases/$id", "DELETE")
        loadPrivateData()
        completed()
        message = "Purchase deleted."
    }

    fun saveEquity(holding: JSONObject, completed: () -> Unit) = action {
        api.request("/api/bonds/equities", "POST", holding)
        loadPrivateData()
        completed()
        message = "Share purchase saved."
    }

    fun logout() = action {
        api.request("/api/bonds/auth/logout", "POST")
        api.clearSession()
        clearPrivateData()
    }

    private fun clearPrivateData() {
        user = null
        purchases = emptyList()
        holdings = emptyList()
        documents = emptyList()
        market = null
        quotes = null
    }
}
