@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioApi

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class PortfolioAdministrationTest {
    @Test fun requestsOnlyRunThroughTheProvidedActionBoundary() = runTest {
        var requests = 0
        val queued = mutableListOf<suspend () -> Unit>()
        val api = PortfolioApi("https://admin.example.test", MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond("""{"accounts":[]}""") }
        }))
        try {
            val administration = PortfolioAdministration({ api }, { queued += it })
            administration.load("accounts")
            assertEquals(0, requests)
            assertNull(administration.accounts)
            queued.removeAt(0).invoke()
            assertEquals(1, requests)
            assertTrue(requireNotNull(administration.accounts).has("accounts"))
        } finally { api.close() }
    }

    @Test fun rejectedPublisherChangeDoesNotReloadOrReportCompletion() = runTest {
        var requests = 0
        var completed = false
        val queued = mutableListOf<suspend () -> Unit>()
        val api = PortfolioApi("https://admin.example.test", MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond("{}", HttpStatusCode.Forbidden) }
        }))
        try {
            val administration = PortfolioAdministration({ api }, { queued += it })
            administration.setPublisher(JsonRecord().put("id", "account"), true) { completed = true }
            val failure = assertFailsWith<ApiException> { queued.removeAt(0).invoke() }
            assertEquals(403, failure.status)
            assertFalse(completed)
            assertNull(administration.accounts)
            assertEquals(1, requests)
        } finally { api.close() }
    }
}
