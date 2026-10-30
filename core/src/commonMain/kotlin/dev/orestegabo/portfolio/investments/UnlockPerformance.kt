package dev.orestegabo.portfolio.investments

import kotlin.time.TimeSource

/** Opt-in diagnostics. Callers supply only fixed stage/route labels, never user data or errors. */
object UnlockPerformance {
    var enabled = false
    var sink: (String) -> Unit = { println(it) }
    fun begin(stage: String, requestId: String = "", route: String = ""): Span? =
        if (enabled) Span(stage, requestId, route) else null

    class Span internal constructor(private val stage: String, private val requestId: String, private val route: String) {
        private val started = TimeSource.Monotonic.markNow()
        fun end(outcome: String = "ok") {
            // Diagnostic output must never be able to fail authentication or an HTTP request.
            runCatching { sink("[UnlockPerformance] stage=$stage duration_ms=${started.elapsedNow().inWholeMilliseconds} outcome=$outcome request_id=$requestId route=$route") }
        }
    }

    suspend fun <T> measure(stage: String, block: suspend () -> T): T {
        val span = begin(stage)
        return try { block().also { span?.end() } }
        catch (failure: Throwable) { span?.end("failed"); throw failure }
    }

    fun <T> measureSync(stage: String, block: () -> T): T {
        val span = begin(stage)
        return try { block().also { span?.end() } }
        catch (failure: Throwable) { span?.end("failed"); throw failure }
    }

    /** No query strings, identifiers, hostnames, or arbitrary caller-controlled paths in logs. */
    fun route(path: String): String = when (val route = path.substringBefore('?')) {
        "/api/bonds/auth/session", "/api/bonds/auth/login", "/api/bonds/mobile-overview",
        "/api/bonds/purchases", "/api/bonds/equities", "/api/bonds/documents", "/api/bonds/statement-items",
        "/api/bonds/rse/quotes", "/api/bonds/notifications", "/api/bonds/notifications/preferences",
        "/api/bonds/notifications/coupons", "/api/bonds/notifications/session-device" -> route
        else -> "/api/bonds/other"
    }
}
