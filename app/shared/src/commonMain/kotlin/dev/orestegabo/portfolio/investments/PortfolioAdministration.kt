package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*

/** Account administration commands; the caller supplies the authenticated action boundary. */
internal class PortfolioAdministration(
    private val currentApi: () -> PortfolioDataApi,
    private val action: (suspend () -> Unit) -> Unit,
) {
    private val api get() = currentApi()
    var accounts by mutableStateOf<JsonRecord?>(null); private set
    var registration by mutableStateOf<JsonRecord?>(null); private set

    fun load(section: String) = action {
        if (section == "accounts") accounts = api.request("/api/bonds/auth/accounts")
        else registration = api.request("/api/bonds/auth/registration")
    }
    fun setPublisher(account: JsonRecord, enabled: Boolean, completed: () -> Unit) = action {
        api.request("/api/bonds/auth/accounts/${account.text("id")}/public-data-publisher", "PUT", JsonRecord().put("enabled", enabled))
        accounts = api.request("/api/bonds/auth/accounts")
        completed()
    }
    fun createInvitation(email: String, origin: String, share: (String) -> Unit) = action {
        val response = api.request("/api/bonds/auth/registration", "POST", JsonRecord().put("email", email))
        registration = api.request("/api/bonds/auth/registration")
        share("$origin/join#invite=${response.text("invite")}")
    }
    fun closeInvitations() = action {
        api.request("/api/bonds/auth/registration", "DELETE")
        registration = api.request("/api/bonds/auth/registration")
    }
}
