@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource

class GuestQuotesTest {
    private class Disk : GuestSnapshotStorage {
        var bytes: ByteArray? = null
        var writes = 0
        override suspend fun read() = bytes?.copyOf()
        override suspend fun write(bytes: ByteArray) { this.bytes = bytes.copyOf(); writes++ }
    }
    private val start = Instant.parse("2026-11-02T14:00:00Z")
    private fun quote() = JsonRecord("""{"source":{"url":"https://rse.rw/equities-market","status":"available","publishedAt":"2026-11-02T12:00:00Z","checkedAt":"2026-11-02T13:00:00Z","stale":false},"quotes":[{"ticker":"TEST","name":"Synthetic company","closing":90}]}""")
    private fun TestScope.market(disk: Disk, enabled: Boolean = true, now: () -> Instant = { start }, clock: TimeSource = TimeSource.Monotonic, fetch: suspend () -> JsonRecord = { quote() }) =
        GuestMarketData(disk, enabled, fetch, now, clock, StandardTestDispatcher(testScheduler))

    @Test fun onlineQuotesSurviveRepositoryRecreationAndBecomeStaleWithoutChangingTheirDates() = runTest {
        val disk = Disk()
        val saved = requireNotNull(market(disk).refresh())
        assertFalse(saved.stale(start))
        val offline = requireNotNull(market(disk, now = { start + 96.hours }, fetch = { error("offline") }).load())
        assertTrue(offline.stale(start + 96.hours))
        assertEquals(saved.data.toString(), offline.data.toString())
        assertEquals(start, offline.receivedAt)
        assertEquals(1, disk.writes)
        val upstreamStale = quote().let { it.put("source", it.getJSONObject("source").put("stale", true)) }
        assertTrue(requireNotNull(market(Disk(), fetch = { upstreamStale }).refresh()).stale(start))
    }
    @Test fun failuresMalformedAndEmptyResponsesPreservePreviouslySavedQuotes() = runTest {
        val disk = Disk(); market(disk).refresh()
        val before = requireNotNull(disk.bytes).copyOf()
        for (fetch in listOf<suspend () -> JsonRecord>({ error("offline") }, { quote().put("quotes", JsonList()) }, { JsonRecord() })) {
            val repo = market(disk, now = { start + 24.hours }, fetch = fetch)
            assertFails { repo.refresh() }
            assertContentEquals(before, disk.bytes)
            assertNotNull(repo.load())
        }
    }
    @Test fun disabledDistributionDoesNotReadCacheOrCallTransport() = runTest {
        val repo = GuestMarketData(object : GuestSnapshotStorage {
            override suspend fun read(): ByteArray? = error("Must not read")
            override suspend fun write(bytes: ByteArray) = error("Must not write")
        }, false, { error("Must not fetch") })
        assertNull(repo.load()); assertNull(repo.refresh())
    }
    @Test fun corruptFutureVersionAndOversizedCachesAreUnavailableWithoutDeletingFinancialData() = runTest {
        for (raw in listOf("broken", "{\"schemaVersion\":2}", "x".repeat(PUBLIC_QUOTE_MAX_BYTES + 1))) {
            val disk = Disk().apply { bytes = raw.encodeToByteArray() }
            assertNull(market(disk).load()); assertEquals(0, disk.writes)
            assertEquals(raw, disk.bytes?.decodeToString())
        }
    }
    @Test fun simultaneousRequestsAndRepeatedFailuresAreThrottled() = runTest {
        var calls = 0
        var now = start
        val clock = kotlin.time.TestTimeSource()
        val gate = CompletableDeferred<Unit>()
        val repo = market(Disk(), now = { now }, clock = clock) { calls++; gate.await(); quote() }
        val first = async { repo.refresh() }; val second = async { repo.refresh() }
        runCurrent(); assertEquals(1, calls)
        gate.complete(Unit); first.await(); second.await(); assertEquals(1, calls)
        clock += 16.minutes; now += 16.minutes
        repo.refresh(); assertEquals(2, calls)
        var failures = 0
        val failing = market(Disk(), clock = clock) { failures++; error("unavailable") }
        repeat(2) { assertFails { failing.refresh() } }; assertEquals(1, failures)
    }
    @Test fun valuationReusesExistingEngineAndNeverTreatsCostAsAvailableMarketValue() = runTest {
        val holdings = listOf("TEST", "MISSING").map { ticker -> JsonRecord().put("ticker", ticker).put("currency","RWF")
            .put("status","active").put("tradeDate","2026-10-01").put("shares",400).put("netAmountPayable",36615.60) }
        val portfolio = PortfolioSnapshot(holdings = holdings)
        val data = requireNotNull(market(Disk()).refresh()).data
        val positions = portfolio.valuedShares(LocalDate.parse("2026-11-02"), data)
        assertEquals(36000.0, positions[0].estimatedValue)
        assertEquals(-615.60, requireNotNull(positions[0].gainLoss), .000001)
        assertNull(positions[1].closingPrice); assertNull(positions[1].gainLoss)
        assertEquals(36615.60, positions[1].position.cashCost)
        assertTrue(portfolio.valuedShares(LocalDate.parse("2026-11-02"), null).all { it.closingPrice == null })
        assertEquals(positions[0].estimatedValue, portfolio.valuedShares(LocalDate.parse("2026-11-06"), data)[0].estimatedValue)
    }
    @Test fun slowQuotesNeverBlockLocalPortfolioAndLateResponsesCannotEnterAccountOrSampleState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val waiting = CompletableDeferred<JsonRecord>()
        val repo = LocalGuestPortfolioRepository(Disk(), workDispatcher = StandardTestDispatcher(testScheduler))
        val settings = object : ServerSettings {
            override fun readSessionChoice() = GuestPath.PERSONAL.stored
            override fun writeSessionChoice(value: String?) {}
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) {}
        }
        var calls = 0
        val model = PortfolioModel(settings, guestRepository = repo,
            guestMarketData = market(Disk()) { calls++; waiting.await() },
            apiFactory = { error("No private API allowed") })
        try {
            runCurrent()
            assertNotNull(model.guestPortfolio); assertFalse(model.guestStorageLoading)
            assertTrue(model.guestQuotesRefreshing); assertEquals(1, calls)
            model.refreshGuestQuotes(); runCurrent(); assertEquals(1, calls)
            model.beginAccountSignIn(); waiting.complete(quote()); advanceUntilIdle()
            assertNull(model.guestQuotes); assertFalse(model.isGuest)
            model.enterGuest(GuestPath.SAMPLE); advanceUntilIdle()
            assertNull(model.guestQuotes); assertEquals(1, calls)
        } finally { ViewModelStore().apply { put("test",model); clear() }; Dispatchers.resetMain() }
    }
}
