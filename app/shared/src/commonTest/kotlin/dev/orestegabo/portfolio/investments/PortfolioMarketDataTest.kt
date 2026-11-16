package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PortfolioMarketDataTest {
    private val initialTime = Instant.parse("2026-11-16T12:00:00Z")
    private class Settings : ServerSettings {
        val cache = mutableMapOf<String, String>()
        var failWrites = false
        override fun readOrigin(): String? = null
        override fun writeOrigin(origin: String) {}
        override fun readCachedEquityQuotes(account: String) = cache[account]
        override fun writeCachedEquityQuotes(account: String, quotes: String?) {
            check(!failWrites)
            if (quotes == null) cache.remove(account) else cache[account] = quotes
        }
    }
    private fun board(price: Double = 720.0, date: String = "2026-11-16") =
        JsonRecord().put("source", JsonRecord().put("publishedAt", date).put("stale", false))
            .put("quotes", JsonList(listOf(JsonRecord().put("ticker", "BOK").put("closing", price).put("name", "BK"))))
    private fun api(fetch: suspend () -> JsonRecord) = object : PortfolioDataApi {
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord = fetch()
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("Unused")
    }

    @Test fun restartRestoresMarketValueBeforeNetworkAndUnchangedHoldingsKeepTheirTotal() = runTest {
        val settings = Settings()
        val first = PortfolioMarketData({ api { board() } }, TimeSource.Monotonic, settings, { initialTime })
        first.attach("backend|account")
        first.refreshEquityQuotes()
        val blocked = CompletableDeferred<JsonRecord>()
        val reopened = PortfolioMarketData({ api { blocked.await() } }, TimeSource.Monotonic, settings, { initialTime + 16.minutes })
        reopened.attach("backend|account")
        val holdings = PortfolioSnapshot(holdings = listOf(JsonRecord().put("ticker", "BOK").put("currency", "RWF")
            .put("status", "active").put("tradeDate", "2026-01-01").put("shares", 1000).put("netAmountPayable", 732312)))
        assertEquals(720000.0, holdings.valuation(bondSubscriptionDate(initialTime), reopened.quotes).single().estimatedNetWorth)
        assertTrue(reopened.quotes!!.getJSONObject("source").optBoolean("stale"))
        val refresh = async { reopened.refreshEquityQuotes() }; runCurrent()
        assertEquals(720.0, reopened.quotes!!.rows("quotes").single().getDouble("closing"))
        blocked.complete(board(721.0)); refresh.await()
        assertEquals(721000.0, holdings.valuation(bondSubscriptionDate(initialTime), reopened.quotes).single().estimatedNetWorth)
    }

    @Test fun freshCacheAvoidsDuplicateFetchesButExplicitRefreshBypassesTheInterval() = runTest {
        var calls = 0
        var now = initialTime
        val data = PortfolioMarketData({ api { calls++; board() } }, TimeSource.Monotonic, Settings(), { now })
        data.attach("backend|account")
        data.refreshEquityQuotes(); data.refreshEquityQuotes()
        assertEquals(1, calls)
        data.refreshEquityQuotes(refresh = true)
        assertEquals(2, calls)
        now += 16.minutes
        data.refreshEquityQuotes()
        assertEquals(3, calls)
    }

    @Test fun concurrentRefreshesShareOneRequestAndChangedQuantitiesUseTheCachedPrice() = runTest {
        var calls = 0
        val response = CompletableDeferred<JsonRecord>()
        val data = PortfolioMarketData({ api { calls++; response.await() } }, TimeSource.Monotonic, Settings(), { initialTime })
        data.attach("backend|account")
        val first = async { data.refreshEquityQuotes() }
        val second = async { data.refreshEquityQuotes() }
        runCurrent(); assertEquals(1, calls)
        response.complete(board()); first.await(); second.await()
        assertEquals(1, calls)
        fun snapshot(shares: Int) = PortfolioSnapshot(holdings = listOf(JsonRecord().put("ticker", "BOK")
            .put("currency", "RWF").put("status", "active").put("tradeDate", "2026-01-01")
            .put("shares", shares).put("netAmountPayable", shares * 732.312)))
        assertEquals(720000.0, snapshot(1000).valuation(bondSubscriptionDate(initialTime), data.quotes).single().estimatedNetWorth)
        assertEquals(648000.0, snapshot(900).valuation(bondSubscriptionDate(initialTime), data.quotes).single().estimatedNetWorth)
    }

    @Test fun failuresMalformedBoardsAndOlderBoardsPreserveLastPrice() = runTest {
        var response: suspend () -> JsonRecord = { board() }
        val data = PortfolioMarketData({ api { response() } }, TimeSource.Monotonic, Settings(), { initialTime })
        data.attach("backend|account"); data.refreshEquityQuotes()
        for (failure in listOf<suspend () -> JsonRecord>(
            { error("Offline") }, { board(Double.NaN) }, { board(date = "2027-01-01") },
            { board(date = "2026-11-15") }, { board(date = "2026-11-16T13:00:00Z") },
            { board(date = "2026-11-16-invalid") }, { JsonRecord() },
        )) {
            response = failure; data.refreshEquityQuotes(refresh = true)
            assertEquals(720.0, data.quotes!!.rows("quotes").single().getDouble("closing"))
            assertTrue(data.quoteRefreshFailed)
        }
    }

    @Test fun cancellationPropagatesAndLateResponseCannotRepopulateClearedOrDifferentAccount() = runTest {
        val response = CompletableDeferred<JsonRecord>()
        val settings = Settings()
        val data = PortfolioMarketData({ api { response.await() } }, TimeSource.Monotonic, settings, { initialTime })
        data.attach("one|account")
        val pending = async { data.refreshEquityQuotes() }; runCurrent()
        data.clear(); data.attach("other|account")
        response.complete(board()); pending.await()
        assertNull(data.quotes); assertTrue(settings.cache.isEmpty())
        val cancelled = PortfolioMarketData({ api { throw CancellationException() } }, TimeSource.Monotonic, settings, { initialTime })
        assertFailsWith<CancellationException> { cancelled.refreshEquityQuotes() }
        assertFalse(cancelled.quoteRefreshFailed)
    }

    @Test fun cacheIsScopedSanitizedAndRecoverableWhenCorruptOrUnwritable() = runTest {
        val settings = Settings()
        val cache = EquityQuoteCache(settings)
        cache.write("one|account", board().put("privateHoldings", "must-not-persist"), initialTime)
        assertFalse(settings.cache.getValue("one|account").contains("privateHoldings"))
        assertNull(cache.read("two|account", initialTime))
        assertNull(cache.read("one|account", initialTime - 1.minutes))
        settings.cache["one|account"] = "invalid JSON"
        assertNull(cache.read("one|account", initialTime))
        settings.failWrites = true
        val data = PortfolioMarketData({ api { board() } }, TimeSource.Monotonic, settings, { initialTime })
        data.attach("one|account"); data.refreshEquityQuotes()
        assertNotNull(data.quotes); assertFalse(data.quoteRefreshFailed)
    }
}
