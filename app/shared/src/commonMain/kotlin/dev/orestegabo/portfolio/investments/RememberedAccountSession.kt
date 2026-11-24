package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioSessionApi
import kotlinx.coroutines.CancellationException

/** Local viewing permission must never be mistaken for verified authorization to contact account APIs. */
internal class SavedPortfolioApi(
    private val delegate: PortfolioSessionApi, private val viewingOffline: () -> Boolean,
) : PortfolioSessionApi by delegate {
    private fun checkAccess(path: String, method: String) {
        check(!viewingOffline() || method == "GET" && path == "/api/bonds/auth/session" ||
            method == "POST" && path in listOf("/api/bonds/auth/login", "/api/bonds/auth/logout")) {
            "Reconnect to refresh or change account data."
        }
    }
    override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
        checkAccess(path, method)
        return delegate.request(path, method, body)
    }
    override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray {
        checkAccess(path, method)
        return delegate.bytes(path, method, body)
    }
}

internal class RememberedAccountMismatch : IllegalStateException("Sign-in account did not match.")

internal data class VerifiedRememberedAccount(val user: JsonRecord, val renewed: Boolean)

internal fun parseRememberedSignIn(raw: String, origin: String): JsonRecord {
    require(raw.length <= 48_000) { "Saved sign-in is invalid." }
    val saved = try { JsonRecord(raw) }
    catch (_: Exception) { error("Saved sign-in is invalid. Use your password to sign in again.") }
    require(saved.text("origin") == origin) { "Saved sign-in belongs to another server." }
    require(saved.text("email").isNotBlank() && saved.text("password").length in 1..128) { "Saved sign-in is invalid." }
    return saved
}

internal suspend fun verifyRememberedAccount(api: PortfolioSessionApi, saved: JsonRecord): VerifiedRememberedAccount {
    try { api.restoreSession(saved.text("session")) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { api.clearSession() }
    val session = try {
        if (!api.hasSession()) JsonRecord().put("authenticated", false) else api.request("/api/bonds/auth/session")
    } catch (failure: ApiException) {
        if (failure.status == 401) JsonRecord().put("authenticated", false) else throw failure
    }
    val email = saved.text("email")
    val account = session.optJSONObject("user")?.takeIf { session.optBoolean("authenticated") && it.text("email").equals(email, true) }
    val verified = if (account != null) VerifiedRememberedAccount(account, false) else {
        api.clearSession()
        VerifiedRememberedAccount(api.request("/api/bonds/auth/login", "POST",
            JsonRecord().put("email", email).put("password", saved.text("password")).withDeviceIdentity()).getJSONObject("user"), true)
    }
    if (!verified.user.text("email").equals(email, true) || verified.user.text("id").isBlank() ||
        (saved.text("accountId").isNotBlank() && saved.text("accountId") != verified.user.text("id"))) throw RememberedAccountMismatch()
    return verified
}
