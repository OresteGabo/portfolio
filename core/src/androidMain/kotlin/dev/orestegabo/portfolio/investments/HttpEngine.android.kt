package dev.orestegabo.portfolio.investments
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
// HttpURLConnection stream cancellation can drain sockets on the caller/UI thread.
// Reuse the project’s coroutine transport (also used on JVM) for safe lifecycle cancellation.
actual fun createPlatformHttpEngine(): HttpClientEngine = CIO.create()
internal actual fun createPublicQuoteHttpEngine(): HttpClientEngine {
    // HttpURLConnection can consult a process-global CookieHandler outside Ktor's cookie plugin.
    // Fail closed rather than changing another feature's global cookie policy or leaking cookies.
    check(java.net.CookieHandler.getDefault() == null) { "Public quote transport is unavailable." }
    return createPlatformHttpEngine()
}
actual val allowsDevelopmentHttp: Boolean get() = PlatformBuild.debug
/** Set by the Android host using its build variant; production defaults to HTTPS-only. */
object PlatformBuild {
    var debug: Boolean = false
    var apiOrigin: String? = null
}
