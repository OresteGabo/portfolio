package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Optional quote enrichment and market-board caching; never owns session or portfolio records. */
internal class PortfolioMarketData(private val currentApi: () -> PortfolioDataApi, private val clock: TimeSource,
    settings: ServerSettings, private val now: () -> Instant = { Clock.System.now() },
) {
    private val cache = EquityQuoteCache(settings)
    private var account: String? = null
    private var generation = 0
    private var quoteCheckedAt: Instant? = null
    private val quoteMutex = Mutex()

    fun attach(account: String?) {
        if (this.account == account) return
        clear()
        this.account = account
        if (account != null) cache.read(account, now())?.let {
            quotes = it.quotes
            quoteCheckedAt = it.receivedAt
        }
    }
    private val api get() = currentApi()
    private var marketLoadedAt: TimeMark? = null
    var market by mutableStateOf<JsonRecord?>(null); private set
    var quotes by mutableStateOf<JsonRecord?>(null); private set
    var quoteRefreshFailed by mutableStateOf(false); private set
    // Prices enrich the saved snapshot. An optional quote failure must not hide saved purchases.
    suspend fun refreshEquityQuotes(refresh: Boolean = false) = quoteMutex.withLock {
        val token = generation
        val scope = account
        val time = now()
        if (!refresh && quoteCheckedAt?.let { time >= it && time - it < 15.minutes } == true) return@withLock
        try {
            val latest = api.request("/api/bonds/rse/quotes?kind=equities" + if (refresh) "&refresh=1" else "")
            val valid = EquityQuoteCache.sanitize(latest, now())
            if (token != generation) return@withLock
            // Reject an older board arriving after a newer one, including server-cache regressions.
            val oldDate = quotes?.optJSONObject("source")?.text("publishedAt")?.take(10)
            if (oldDate != null && valid.getJSONObject("source").text("publishedAt").take(10) < oldDate) {
                quoteRefreshFailed = true
                return@withLock
            }
            quotes = valid
            quoteCheckedAt = now()
            quoteRefreshFailed = valid.getJSONObject("source").optBoolean("stale")
            if (scope != null) cache.write(scope, valid, now())
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            if (token != generation) return@withLock
            if (failure is ApiException && failure.status == 401) throw failure
            quoteRefreshFailed = true
        }
    }

    private val marketMutex = Mutex()
    var marketLoading by mutableStateOf(false)
        private set

    suspend fun prefetch(refresh: Boolean = false) = marketMutex.withLock {
        if (!refresh && marketLoadedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < DATA_CACHE_MILLIS } == true) return@withLock
        val token = generation
        marketLoading = true
        try {
            // Independent upstream resources: equity prices never wait for the multi-page bond board.
            supervisorScope {
                val prices = async { refreshEquityQuotes(refresh) }
                val board = async {
                    val latest = api.request("/api/bonds/rse/market" + if (refresh) "?refresh=1" else "")
                    if (token == generation) market = latest
                }
                prices.await(); board.await()
            }
            if (token == generation) marketLoadedAt = clock.markNow()
        } finally { if (token == generation) marketLoading = false }
    }

    suspend fun equityHistory(symbol: String, period: String): JsonRecord =
        api.request("/api/bonds/rse/equity-history?tickers=$symbol&period=$period")

    fun invalidate() { marketLoadedAt = null; quoteCheckedAt = null }
    fun clear() {
        generation++; account = null; invalidate()
        market = null; quotes = null; quoteRefreshFailed = false; marketLoading = false
    }
}
