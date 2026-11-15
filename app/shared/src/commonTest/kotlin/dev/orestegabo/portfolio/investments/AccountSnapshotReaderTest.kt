@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioApi

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class AccountSnapshotReaderTest {
    @Test fun unsupportedOverviewIsRememberedUntilTheConnectionChanges() = runTest {
        var overviewReads = 0
        val api = PortfolioApi("https://snapshot.example.test", MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request -> when (request.url.encodedPath) {
                "/api/bonds/mobile-overview" -> { overviewReads++; respond("{}", HttpStatusCode.NotFound) }
                "/api/bonds/purchases" -> respond("""{"purchases":[]}""")
                "/api/bonds/equities" -> respond("""{"holdings":[]}""")
                "/api/bonds/documents" -> respond("""{"documents":[]}""")
                else -> error("Unexpected endpoint")
            } }
        }))
        try {
            val reader = AccountSnapshotReader({ api }, { PortfolioSnapshot() })
            repeat(2) { assertTrue(reader.read(false).historyComplete) }
            assertEquals(1, overviewReads)
            reader.resetCompatibility()
            reader.read(false)
            assertEquals(2, overviewReads)
        } finally { api.close() }
    }

    @Test fun optionalFailureKeepsPendingRecordsAndLatestParallelDocuments() = runTest {
        val pending = JsonRecord().put("id", "pending").put("status", "submitted")
        var current = PortfolioSnapshot(purchases = listOf(pending))
        val latest = JsonRecord().put("id", "latest-document")
        val api = PortfolioApi("https://snapshot.example.test", MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request -> when (request.url.encodedPath) {
                "/api/bonds/mobile-overview" -> respond("""{"version":1,"recentPurchaseIds":[],"recentHoldingIds":[],"purchases":[],"holdings":[]}""")
                "/api/bonds/purchases" -> {
                    current = current.copy(documents = listOf(latest))
                    respond("{}", HttpStatusCode.ServiceUnavailable)
                }
                else -> error("Deferred document load must not request another resource")
            } }
        }))
        try {
            val loaded = AccountSnapshotReader({ api }, { current }).read(true)
            assertFalse(loaded.historyComplete)
            assertEquals("pending", loaded.snapshot.purchases.single().text("id"))
            assertEquals("latest-document", loaded.snapshot.documents.single().text("id"))
        } finally { api.close() }
    }

    @Test fun rejectedAuthenticationNeverFallsBackToPrivateRecordEndpoints() = runTest {
        var requests = 0
        val api = PortfolioApi("https://snapshot.example.test", MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond("{}", HttpStatusCode.Unauthorized) }
        }))
        try {
            val failure = assertFailsWith<ApiException> { AccountSnapshotReader({ api }, { PortfolioSnapshot() }).read(false) }
            assertEquals(401, failure.status)
            assertEquals(1, requests)
        } finally { api.close() }
    }
}
