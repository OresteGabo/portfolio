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
                        "/api/bonds/purchases" -> if (scenario.expired) respond("""{"error":"Authentication required."}""", HttpStatusCode.Unauthorized) else respond("""{"purchases":[{"id":"private-purchase","currency":"RWF"}]}""")
                        "/api/bonds/equities" -> respond("""{"holdings":[]}""")
                        "/api/bonds/documents" -> respond("""{"documents":[]}""")
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
}
