package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.runBlocking
import java.io.File

/** Opt-in verification against a local API; receives only a short-lived, revocable session. */
fun main() = runBlocking {
    val path = requireNotNull(System.getenv("GABO_VERIFICATION_SESSION_FILE")) {
        "Set GABO_VERIFICATION_SESSION_FILE to a private session fixture outside the repository."
    }
    val fixture = JsonRecord(File(path).readText())
    val origin = fixture.text("origin")
    require(origin == "http://localhost:8080" || origin.endsWith(".local:8080")) {
        "This check is restricted to the local development API."
    }
    val api = PortfolioApi(origin, allowHttp = true)
    try {
        check(!api.request("/api/bonds/auth/session").optBoolean("authenticated"))
        api.restoreSession(fixture.getJSONObject("session").toString())
        check(api.request("/api/bonds/auth/session").optBoolean("authenticated"))
        val bonds = api.request("/api/bonds/purchases").rows("purchases")
        val shares = api.request("/api/bonds/equities").rows("holdings")
        val documents = api.request("/api/bonds/documents").rows("documents")
        check(bonds.isNotEmpty() && shares.isNotEmpty() && documents.isNotEmpty())
        val overview = api.request("/api/bonds/mobile-overview")
        check(overview.optInt("version") == 1)
        val activePrincipal = bonds.filter { it.text("status") == "active" }.sumOf { it.optDouble("faceValue") }
        val overviewPrincipal = overview.rows("purchases").filter { it.text("status") == "active" }.sumOf { it.optDouble("faceValue") }
        check(kotlin.math.abs(activePrincipal - overviewPrincipal) < .01)
        for (document in documents) {
            val bytes = api.bytes(document.text("downloadUrl"))
            check(bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-")
        }
        api.request("/api/bonds/statement-items")
        println("Shared Kotlin client passed: ${bonds.size} bonds, ${shares.size} share purchases, ${documents.size} PDF downloads; overview principal matches full history.")
    } finally {
        try { api.request("/api/bonds/auth/logout", "POST") }
        finally { api.close() }
    }
}
