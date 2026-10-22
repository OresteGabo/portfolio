package dev.orestegabo.portfolio.investments

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val DEFAULT_ORIGIN = "https://bonds.orestegabo.dev"
class ApiException(val status: Int, message: String) : Exception(message)
expect fun createPlatformHttpEngine(): HttpClientEngine
expect val allowsDevelopmentHttp: Boolean

private class SessionCookies : CookiesStorage {
    private val mutex = Mutex()
    private var delegate = AcceptAllCookiesStorage()
    override suspend fun get(requestUrl: Url) = mutex.withLock { delegate.get(requestUrl) }
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock { delegate.addCookie(requestUrl, cookie) }
    suspend fun clear() = mutex.withLock { delegate.close(); delegate = AcceptAllCookiesStorage() }
    override fun close() { delegate.close() }
}

/** Identical origin, redirect, error and in-memory session policy on Android and iOS. */
class PortfolioApi(
    val origin: String,
    engine: HttpClientEngine = createPlatformHttpEngine(),
    allowHttp: Boolean = allowsDevelopmentHttp,
) {
    private val cookies = SessionCookies()
    private val client: HttpClient
    init {
        val url = runCatching { Url(origin) }.getOrElse { error("Enter a valid server origin.") }
        require(url.protocol == URLProtocol.HTTPS || (allowHttp && url.protocol == URLProtocol.HTTP)) { "Use an HTTPS server URL. HTTP is available in debug builds only." }
        require(url.host.isNotBlank() && url.user == null && url.password == null && url.parameters.isEmpty() && url.fragment.isEmpty() && url.encodedPath.trim('/').isEmpty()) {
            "Enter the server origin only, for example $DEFAULT_ORIGIN"
        }
        client = HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(HttpCookies) { storage = cookies }
            install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 45_000; socketTimeoutMillis = 40_000 }
        }
    }
    suspend fun request(path: String, method: String = "GET", body: JsonRecord? = null): JsonRecord {
        val raw = bytes(path, method, body).decodeToString()
        return if (raw.isBlank()) JsonRecord() else JsonRecord(raw)
    }
    suspend fun bytes(path: String, method: String = "GET", body: JsonRecord? = null): ByteArray {
        val decoded = path.substringBefore('?').decodeURLPart()
        require(path.startsWith("/api/bonds/") && !decoded.contains("..") && !path.contains('\\') && !path.contains('#') && !path.contains('\r') && !path.contains('\n')) { "Invalid API path." }
        return client.prepareRequest(origin + path) {
            this.method = HttpMethod.parse(method)
            header(HttpHeaders.Accept, "application/json, application/pdf")
            header(HttpHeaders.Origin, origin)
            if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
        }.execute { response ->
            val limit = 24 * 1024 * 1024
            require((response.contentLength() ?: 0) <= limit) { "Response is too large." }
            val chunks = mutableListOf<ByteArray>()
            var total = 0
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(8192)
            try {
                while (true) {
                    val count = channel.readAvailable(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= limit) { "Response is too large." }
                    chunks += buffer.copyOf(count)
                }
            } finally { channel.cancel() }
            val bytes = ByteArray(total)
            var offset = 0
            chunks.forEach { it.copyInto(bytes, offset); offset += it.size }
            if (response.status.value !in 200..299) {
                val message = runCatching { JsonRecord(bytes.decodeToString()).text("error") }.getOrNull()
                throw ApiException(response.status.value, message?.takeIf { it.isNotBlank() } ?: "Server returned HTTP ${response.status.value}. Please try again.")
            }
            bytes
        }
    }
    suspend fun clearSession() = cookies.clear()
    fun close() = client.close()
}
