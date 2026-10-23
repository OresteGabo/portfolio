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
        var value: String? = "https://first.example.test"
        override fun readOrigin() = value
        override fun writeOrigin(origin: String) { value = origin }
    }
    private class Scenario {
        var expired = false
        var failDocuments = false
        var equityWrites = 0
        var equitySaved = false
        var updatedBonds = false
        val settings = Settings()
        val store = ViewModelStore()
    }
    private fun TestScope.model(scenario: Scenario): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(scenario.settings, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            val engine = MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/bonds/auth/session" -> respond(if (origin.contains("first")) """{"authenticated":true,"user":{"id":"one","email":"one@example.test","isOwner":false}}""" else """{"authenticated":false}""")
                        "/api/bonds/purchases" -> if (scenario.expired) respond("""{"error":"Authentication required."}""", HttpStatusCode.Unauthorized) else respond(if (scenario.updatedBonds) """{"purchases":[{"id":"new-purchase","currency":"RWF"}]}""" else """{"purchases":[{"id":"private-purchase","currency":"RWF"}]}""")
                        "/api/bonds/equities" -> {
                            if (request.method == HttpMethod.Post) { scenario.equityWrites++; scenario.equitySaved = true }
                            respond(if (scenario.equitySaved) """{"holdings":[{"id":"bk-purchase","ticker":"BOK","securityName":"BK","currency":"RWF","status":"active","tradeDate":"2026-01-01","shares":100,"netAmountPayable":36500}]}""" else """{"holdings":[]}""")
                        }
                        "/api/bonds/documents" -> if (scenario.failDocuments) respond("""{"error":"Unavailable"}""", HttpStatusCode.ServiceUnavailable) else respond("""{"documents":[]}""")
                        else -> respond("{}")
                    }
                }
            })
            PortfolioApi(origin, engine)
        }).also { scenario.store.put("portfolio", it) }
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

}
