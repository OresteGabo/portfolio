package dev.orestegabo.portfolio.investments

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlin.time.Instant

const val PUBLIC_QUOTE_MAX_BYTES = 128 * 1024
const val RSE_EQUITY_SOURCE = "https://rse.rw/equities-market"
internal expect fun createPublicQuoteHttpEngine(): HttpClientEngine

/** Entire public board only: no account session, cookie plugin, ticker selection or request body. */
class PublicEquityQuotesApi(
    origin: String = DEFAULT_ORIGIN,
    engine: HttpClientEngine = createPublicQuoteHttpEngine(),
) {
    private val endpoint: String
    private val client: HttpClient
    init {
        val url = Url(origin)
        require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() && url.user == null && url.password == null &&
            url.parameters.isEmpty() && url.fragment.isEmpty() && url.encodedPath.trim('/').isEmpty())
        endpoint = origin.trimEnd('/') + "/api/bonds/rse/quotes?kind=equities"
        client = HttpClient(engine) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) { connectTimeoutMillis = 4_000; socketTimeoutMillis = 6_000; requestTimeoutMillis = 8_000 }
        }
    }
    suspend fun fetch(): JsonRecord = client.prepareGet(endpoint) {
        headers.append(HttpHeaders.Accept, "application/json")
    }.execute { response ->
        require(response.status.value == 200) { "Public quotes unavailable." }
        require((response.contentLength() ?: 0) in 0..PUBLIC_QUOTE_MAX_BYTES.toLong())
        val chunks = mutableListOf<ByteArray>()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(8192)
        var size = 0
        while (true) {
            val count = channel.readAvailable(buffer)
            if (count < 0) break
            if (count == 0) continue
            size += count
            require(size <= PUBLIC_QUOTE_MAX_BYTES) { "Public quote response too large." }
            chunks += buffer.copyOf(count)
        }
        val bytes = ByteArray(size)
        var offset = 0
        chunks.forEach { it.copyInto(bytes, offset); offset += it.size }
        JsonRecord(bytes.decodeToString(throwOnInvalidSequence = true))
    }
    fun close() = client.close()
}

/** Whitelist data before persistence/valuation. Retrieval time never substitutes for publication time. */
fun normalizedPublicEquityQuotes(raw: JsonRecord, now: Instant): JsonRecord {
    val source = raw.getJSONObject("source")
    require(source.text("url") == RSE_EQUITY_SOURCE && source.text("status") == "available")
    val published = Instant.parse(source.text("publishedAt"))
    val checked = Instant.parse(source.text("checkedAt"))
    require(published <= now && checked <= now && published <= checked)
    val array = raw.getJSONArray("quotes")
    val rows = array.objects()
    require(rows.size == array.length() && rows.size in 1..200)
    val quotes = rows.map {
        val ticker = it.text("ticker")
        val closing = it.getDouble("closing")
        require(ticker.matches(Regex("[A-Z][A-Z0-9]{1,9}")) && closing > 0)
        JsonRecord().put("ticker", ticker).put("closing", closing).put("name", it.text("name").take(160))
    }
    require(quotes.map { it.text("ticker") }.distinct().size == quotes.size)
    return JsonRecord().put("source", JsonRecord().put("url", RSE_EQUITY_SOURCE).put("status", "available")
        .put("publishedAt", published.toString()).put("checkedAt", checked.toString()).put("stale", source.optBoolean("stale")))
        .put("quotes", JsonList(quotes))
}
