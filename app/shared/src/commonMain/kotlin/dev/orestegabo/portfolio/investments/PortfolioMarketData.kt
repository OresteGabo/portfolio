package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Optional quote enrichment and market-board caching; never owns session or portfolio records. */
internal class PortfolioMarketData(private val currentApi: () -> PortfolioDataApi, private val clock: TimeSource) {
    private val api get() = currentApi()
    private var marketLoadedAt: TimeMark? = null
    var market by mutableStateOf<JsonRecord?>(null); private set
    var quotes by mutableStateOf<JsonRecord?>(null); private set
    var quoteRefreshFailed by mutableStateOf(false); private set
    // Prices enrich the saved snapshot. An optional quote failure must not hide saved purchases.
    suspend fun refreshEquityQuotes(refresh: Boolean = false) {
        try {
            val latest = api.request("/api/bonds/rse/quotes?kind=equities" + if (refresh) "&refresh=1" else "")
            if (latest.rows("quotes").isNotEmpty()) {
                quotes = latest
                quoteRefreshFailed = latest.optJSONObject("source")?.text("status") == "error"
            } else {
                quoteRefreshFailed = true
            }
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            quoteRefreshFailed = true
        }
    }

    private val marketMutex = Mutex()
    var marketLoading by mutableStateOf(false)
        private set

    suspend fun prefetch(refresh: Boolean = false) = marketMutex.withLock {
        if (!refresh && marketLoadedAt?.elapsedNow()?.inWholeMilliseconds?.let { it < DATA_CACHE_MILLIS } == true) return@withLock
        marketLoading = true
        try {
            // Independent upstream resources: equity prices never wait for the multi-page bond board.
            supervisorScope {
                val prices = async { refreshEquityQuotes(refresh) }
                val board = async { market = api.request("/api/bonds/rse/market" + if (refresh) "?refresh=1" else "") }
                prices.await(); board.await()
            }
            marketLoadedAt = clock.markNow()
        } finally { marketLoading = false }
    }

    suspend fun equityHistory(symbol: String, period: String): JsonRecord =
        api.request("/api/bonds/rse/equity-history?tickers=$symbol&period=$period")

    fun invalidate() { marketLoadedAt = null }
    fun clear() { invalidate(); market = null; quotes = null; quoteRefreshFailed = false }
}
