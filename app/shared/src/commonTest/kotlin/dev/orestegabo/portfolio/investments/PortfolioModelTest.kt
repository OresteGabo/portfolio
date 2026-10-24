@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*

class PortfolioModelTest {
    private class Settings : ServerSettings {
        var hiddenNumbers = false
        override fun readNumbersHidden() = hiddenNumbers
        override fun writeNumbersHidden(hidden: Boolean) { hiddenNumbers = hidden }
        val targets = mutableMapOf<String, Long>()
        override fun forgetMonthlyTarget(account: String) { targets.remove(account) }
        override fun readMonthlyTarget(account: String) = targets[account]
        override fun writeMonthlyTarget(account: String, amount: Long) { targets[account] = amount }
        var value: String? = "https://first.example.test"
        override fun readOrigin() = value
        override fun writeOrigin(origin: String) { value = origin }
    }
    private class Scenario {
        var vault: SecureSignIn? = null
        var expired = false
        var deletionStatus = HttpStatusCode.OK
        var deletionConfirmed = true
        var deletionRequests = 0
        var failDocuments = false
        var equityWrites = 0
        var equitySaved = false
        var updatedBonds = false
        var failQuotes = false
        var quotePrice = 400
        var compact = false
        var overviewReads = 0
        var purchaseReads = 0
        var equityReads = 0
        val settings = Settings()
        val store = ViewModelStore()
    }
    private fun TestScope.model(scenario: Scenario): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(scenario.settings, vault = scenario.vault, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            val engine = MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/bonds/mobile-overview" -> {
                            scenario.overviewReads++
                            respond(if (scenario.compact) """{"version":1,"purchases":[{"id":"old-active","status":"active","currency":"RWF","purchaseDate":"2020-01-01","faceValue":100000}],"holdings":[{"id":"aggregate:BOK","ticker":"BOK","currency":"RWF","status":"active","tradeDate":"2020-01-01","shares":100,"netAmountPayable":36500,"lotCount":2}],"recentPurchaseIds":[],"recentHoldingIds":[]}""" else "{}")
                        }
                        "/api/bonds/auth/account" -> {
                            assertEquals(HttpMethod.Delete, request.method)
                            scenario.deletionRequests++
                            respond(if (scenario.deletionStatus == HttpStatusCode.OK) """{"deleted":${scenario.deletionConfirmed}}"""
                                else """{"error":"Account deletion failed."}""", scenario.deletionStatus)
                        }
                        "/api/bonds/auth/session" -> respond(if (origin.contains("first")) """{"authenticated":true,"user":{"id":"11111111-1111-4111-8111-111111111111","email":"one@example.test","isOwner":false}}""" else """{"authenticated":false}""")
                        "/api/bonds/purchases" -> { if (request.method == HttpMethod.Get) scenario.purchaseReads++; if (scenario.expired) respond("""{"error":"Authentication required."}""", HttpStatusCode.Unauthorized) else respond(if (scenario.updatedBonds) """{"purchases":[{"id":"new-purchase","currency":"RWF"}]}""" else """{"purchases":[{"id":"private-purchase","currency":"RWF"}]}""") }
                        "/api/bonds/equities" -> {
                            if (request.method == HttpMethod.Get) scenario.equityReads++
                            if (request.method == HttpMethod.Post) { scenario.equityWrites++; scenario.equitySaved = true }
                            respond(if (scenario.equitySaved) """{"holdings":[{"id":"bk-purchase","ticker":"BOK","securityName":"BK","currency":"RWF","status":"active","tradeDate":"2026-01-01","shares":100,"netAmountPayable":36500}]}""" else """{"holdings":[]}""")
                        }
                        "/api/bonds/rse/quotes" -> if (scenario.failQuotes) respond("""{"error":"Prices unavailable"}""", HttpStatusCode.ServiceUnavailable)
                            else respond("""{"source":{"publishedAt":"2026-01-01","stale":false},"quotes":[{"ticker":"BOK","closing":${scenario.quotePrice}}]}""")
                        "/api/bonds/documents" -> if (scenario.failDocuments) respond("""{"error":"Unavailable"}""", HttpStatusCode.ServiceUnavailable) else respond("""{"documents":[]}""")
                        else -> respond("{}")
                    }
                }
            })
            PortfolioApi(origin, engine)
        }).also { scenario.store.put("portfolio", it) }
    }
    @Test fun confirmedAccountDeletionClearsThePrivateSnapshotAndSession() = runTest {
        val scenario = Scenario()
        var removedOrigin: String? = null
        scenario.vault = object : SecureSignIn {
            override val available = true
            override val unlockLabel = "Device unlock"
            override fun hasSaved(origin: String) = false
            override suspend fun read(origin: String): String? = null
            override suspend fun save(origin: String, value: String) = true
            override fun delete(origin: String) { removedOrigin = origin }
            override fun lock() {}
        }
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertNotNull(model.user)
            assertTrue(model.purchases.isNotEmpty())
            model.updateMonthlyInvestmentTarget(300000)
            model.deleteAccount("synthetic password")
            advanceUntilIdle()
            assertEquals(1, scenario.deletionRequests)
            assertEquals("https://first.example.test", removedOrigin)
            assertNull(model.user)
            assertTrue(model.purchases.isEmpty())
            assertTrue(model.holdings.isEmpty())
            assertTrue(model.documents.isEmpty())
            assertNull(model.quotes)
            assertFalse(model.secureSignInEnabled)
            assertFalse(model.locked)
            assertEquals("Your account and its data have been deleted.", model.message)
            assertTrue(scenario.settings.targets.isEmpty())
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun failedOrUnconfirmedDeletionKeepsTheAccountAndItsData() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            scenario.deletionStatus = HttpStatusCode.Forbidden
            model.deleteAccount("incorrect password")
            advanceUntilIdle()
            assertNotNull(model.user)
            assertTrue(model.purchases.isNotEmpty())
            assertEquals("Account deletion failed.", model.error)
            scenario.deletionStatus = HttpStatusCode.OK
            scenario.deletionConfirmed = false
            model.deleteAccount("synthetic password")
            advanceUntilIdle()
            assertNotNull(model.user)
            assertTrue(model.purchases.isNotEmpty())
            assertNull(model.message)
            assertEquals(2, scenario.deletionRequests)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun expiredSessionClearsPrivateRecordsAndShowsTheFailure() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertEquals(1, model.purchases.size)
            assertNotNull(model.user)
            scenario.expired = true
            model.refresh()
            advanceUntilIdle()
            assertNull(model.user)
            assertTrue(model.purchases.isEmpty())
            assertTrue(model.documents.isEmpty())
            assertEquals("Authentication required.", model.error)
            assertFalse(model.busy)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun changingServerCannotShowThePreviousAccountsInvestments() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertEquals(1, model.purchases.size)
            model.connect("https://second.example.test/")
            advanceUntilIdle()
            assertEquals("https://second.example.test", model.origin)
            assertEquals(model.origin, scenario.settings.value)
            assertNull(model.user)
            assertTrue(model.purchases.isEmpty())
            assertFalse(model.busy)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun rapidActionsDoNotCreateDuplicateWrites() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            var writes = 0
            model.action { delay(1000); writes++ }
            model.action { writes++ }
            advanceUntilIdle()
            assertEquals(1, writes)
            assertFalse(model.busy)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun bkSaveUpdatesTheSameRecordsUsedByTheCardAndStocks() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            var completed = false
            model.saveEquity(JsonRecord().put("ticker", "BOK")) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertEquals(1, scenario.equityWrites)
            assertEquals("bk-purchase", model.holdings.single().text("id"))
            val today = kotlinx.datetime.LocalDate(2026, 10, 23)
            assertEquals(36500.0, model.portfolio.summary(today).single().investedCash)
            assertEquals(36500.0, model.portfolio.sharePositions(today).single().cashCost)
            assertNull(model.error)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun savedPurchaseClosesFormEvenIfRefreshFailsAndRetryOnlyReads() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            val original = model.portfolio
            scenario.failDocuments = true
            scenario.updatedBonds = true
            var completed = false
            model.saveEquity(JsonRecord().put("ticker", "BOK")) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertSame(original, model.portfolio) // No half-published refresh.
            assertEquals("private-purchase", model.purchases.single().text("id"))
            assertTrue(model.error.orEmpty().contains("Share purchase saved."))
            assertTrue(model.error.orEmpty().contains("do not submit it again"))
            scenario.failDocuments = false
            model.refresh()
            advanceUntilIdle()
            assertEquals(1, scenario.equityWrites)
            assertEquals("new-purchase", model.purchases.single().text("id"))
            assertEquals("bk-purchase", model.holdings.single().text("id"))
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }


    @Test fun quotesLoadWithThePortfolioAndOptionalFailuresPreservePurchasesAndLastKnownPrices() = runTest {
        val scenario = Scenario().apply { equitySaved = true; failQuotes = true }
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertNotNull(model.user)
            assertEquals(1, model.holdings.size)
            assertTrue(model.quoteRefreshFailed)
            assertNull(model.error)
            scenario.failQuotes = false
            model.refresh()
            advanceUntilIdle()
            assertEquals(400.0, model.quotes?.rows("quotes")?.single()?.optDouble("closing"))
            assertFalse(model.quoteRefreshFailed)
            scenario.failQuotes = true
            model.refresh()
            advanceUntilIdle()
            assertEquals(1, model.holdings.size)
            assertEquals(400.0, model.quotes?.rows("quotes")?.single()?.optDouble("closing"))
            assertTrue(model.quoteRefreshFailed)
            assertNull(model.error)
            scenario.failQuotes = false
            scenario.quotePrice = 500
            model.refresh()
            advanceUntilIdle()
            assertEquals(500.0, model.quotes?.rows("quotes")?.single()?.optDouble("closing"))
            assertFalse(model.quoteRefreshFailed)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun compactOverviewDefersFullHistoryAndReusesItUntilTheNextSync() = runTest {
        val scenario = Scenario().apply { compact = true }
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertFalse(model.historyComplete)
            assertEquals(0, scenario.purchaseReads)
            assertEquals(0, scenario.equityReads)
            assertTrue(model.portfolio.recentBonds(Today).isEmpty())
            assertTrue(model.portfolio.recentShares(Today).isEmpty())
            assertEquals(140000.0, model.portfolio.valuation(Today, model.quotes).single().estimatedNetWorth)
            model.loadInvestmentHistory()
            advanceUntilIdle()
            assertTrue(model.historyComplete)
            assertEquals(1, scenario.purchaseReads)
            assertEquals(1, scenario.equityReads)
            model.loadInvestmentHistory()
            advanceUntilIdle()
            assertEquals(1, scenario.purchaseReads)
            model.refresh()
            advanceUntilIdle()
            assertFalse(model.historyComplete)
            assertEquals(2, scenario.overviewReads)
            assertEquals(1, scenario.purchaseReads)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun legacyServersAreProbedOnceAndOpeningHistoryDoesNotRefetchExistingRecords() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertTrue(model.historyComplete)
            model.loadInvestmentHistory()
            advanceUntilIdle()
            assertEquals(1, scenario.purchaseReads)
            model.refresh()
            advanceUntilIdle()
            assertEquals(1, scenario.overviewReads)
            assertEquals(2, scenario.purchaseReads)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun fastPullRefreshHoldsLoadingThenCheckmarkAndIgnoresRepeatedPulls() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            model.refreshPortfolio(); runCurrent()
            assertEquals(PortfolioRefreshPhase.LOADING, model.refreshPhase)
            assertTrue(model.busy)
            val reads = scenario.purchaseReads
            model.refreshPortfolio(); runCurrent()
            assertEquals(reads, scenario.purchaseReads)
            advanceTimeBy(500); runCurrent()
            assertEquals(PortfolioRefreshPhase.LOADING, model.refreshPhase)
            advanceTimeBy(500); runCurrent()
            assertEquals(PortfolioRefreshPhase.SUCCESS, model.refreshPhase)
            assertTrue(model.busy)
            advanceTimeBy(300); runCurrent()
            assertEquals(PortfolioRefreshPhase.SUCCESS, model.refreshPhase)
            advanceUntilIdle()
            assertEquals(PortfolioRefreshPhase.IDLE, model.refreshPhase)
            assertFalse(model.busy)
            assertNull(model.message) // The checkmark supplies completion feedback, without a second banner.
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun failedPullRefreshShowsFailureInsteadOfSuccessAndPreservesTheSnapshot() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            val original = model.portfolio
            scenario.failDocuments = true
            model.refreshPortfolio(); runCurrent()
            assertEquals(PortfolioRefreshPhase.LOADING, model.refreshPhase)
            advanceTimeBy(1000); runCurrent()
            assertEquals(PortfolioRefreshPhase.ERROR, model.refreshPhase)
            assertSame(original, model.portfolio)
            advanceUntilIdle()
            assertEquals(PortfolioRefreshPhase.IDLE, model.refreshPhase)
            assertFalse(model.busy)
            assertNotNull(model.error)
            assertNull(model.message)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun monthlyTargetDefaultsTo100kAndPersistsForOnlyTheSignedInAccountAndServer() = runTest {
        val scenario = Scenario()
        val first = model(scenario)
        try {
            advanceUntilIdle()
            assertEquals(100_000L, first.monthlyInvestmentTarget)
            first.updateMonthlyInvestmentTarget(300_000L)
            assertEquals(300_000L, first.monthlyInvestmentTarget)
            assertEquals(listOf(300_000L), scenario.settings.targets.values.toList())
            assertFailsWith<IllegalArgumentException> { first.updateMonthlyInvestmentTarget(0) }
            scenario.store.clear()
            val reopened = model(scenario)
            advanceUntilIdle()
            assertEquals(300_000L, reopened.monthlyInvestmentTarget)
            reopened.connect("https://second.example.test"); advanceUntilIdle()
            assertNull(reopened.user)
            assertEquals(100_000L, reopened.monthlyInvestmentTarget)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun numberPrivacyPersistsWithoutChangingOrRefetchingFinancialData() = runTest {
        val scenario = Scenario()
        val first = model(scenario)
        try {
            advanceUntilIdle()
            assertFalse(first.numbersHidden)
            val snapshot = first.portfolio
            val reads = scenario.purchaseReads
            first.toggleNumberVisibility()
            assertTrue(first.numbersHidden); assertTrue(scenario.settings.hiddenNumbers)
            assertSame(snapshot, first.portfolio)
            assertEquals(reads, scenario.purchaseReads)
            scenario.store.clear()
            val reopened = model(scenario)
            advanceUntilIdle()
            assertTrue(reopened.numbersHidden)
            reopened.toggleNumberVisibility()
            assertFalse(reopened.numbersHidden); assertFalse(scenario.settings.hiddenNumbers)
            assertEquals("private-purchase", reopened.purchases.single().text("id"))
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

}
