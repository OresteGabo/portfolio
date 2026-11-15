@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Characterizes the public model boundary, independently of the implementation's collaborators. */
class AccountRecordWorkflowsTest {
    private class Scenario {
        val events = mutableListOf<String>()
        val bodies = mutableListOf<JsonRecord>()
        val failures = mutableMapOf<String, HttpStatusCode>()
        var ledger = listOf(JsonRecord().put("id", "cash-before"))
        var orders = listOf(JsonRecord().put("id", "order-before").put("status", "pending"))
        var statement = JsonRecord().put("id", "statement-one").put("state", "review-required")
            .put("decisions", JsonList(listOf(JsonRecord().put("at", "2026-11-01T12:00:00Z"))))
        var holdLedger = false
        val ledgerStarted = CompletableDeferred<Unit>()
    }

    private suspend fun TestScope.withModel(block: suspend (Scenario, PortfolioModel) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val scenario = Scenario()
        val model = PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://records.example.test"
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val path = request.url.encodedPath
                    val event = "${request.method.value} $path"
                    scenario.events += event
                    scenario.failures[event]?.let { return@addHandler respond("{}", it) }
                    val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()?.let(::JsonRecord)
                    if (body != null) scenario.bodies += body
                    val result = when (path) {
                        "/api/bonds/auth/session" -> JsonRecord().put("authenticated", true).put("user", JsonRecord().put("id", "test-account"))
                        "/api/bonds/mobile-overview" -> return@addHandler respond("{}", HttpStatusCode.NotFound)
                        "/api/bonds/cash-ledger" -> {
                            if (scenario.holdLedger) { scenario.ledgerStarted.complete(Unit); awaitCancellation() }
                            if (request.method == HttpMethod.Post) scenario.ledger = listOf(requireNotNull(body))
                            JsonRecord().put("entries", JsonList(scenario.ledger))
                        }
                        "/api/bonds/share-orders" -> JsonRecord().put("orders", JsonList(scenario.orders))
                        "/api/bonds/statement-items" -> JsonRecord().put("items", JsonList(listOf(scenario.statement)))
                            .put("rows", JsonList(listOf(scenario.statement)))
                        "/api/bonds/statement-items/statement-one/resolve", "/api/bonds/statement-items/statement-one/bond" -> {
                            scenario.statement = JsonRecord(scenario.statement.toString()).put("state", "recorded")
                            JsonRecord().put("rows", JsonList(listOf(scenario.statement)))
                        }
                        else -> JsonRecord()
                    }
                    respond(result.toString())
                }
            }))
        })
        store.put("model", model)
        try {
            advanceUntilIdle()
            assertNotNull(model.user)
            scenario.events.clear(); scenario.bodies.clear()
            block(scenario, model)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun ledgerTransientFailureRetainsEntriesButUnsupportedEndpointClearsThem() = runTest {
        withModel { scenario, model ->
            assertEquals("cash-before", model.ledgerEntries.single().text("id"))
            scenario.failures["GET /api/bonds/cash-ledger"] = HttpStatusCode.ServiceUnavailable
            model.reloadLedger()
            assertFalse(model.ledgerAvailable)
            assertEquals("cash-before", model.ledgerEntries.single().text("id"))
            assertEquals("Cash activity could not be refreshed. Try again.", model.ledgerError)
            scenario.failures["GET /api/bonds/cash-ledger"] = HttpStatusCode.NotFound
            model.reloadLedger()
            assertTrue(model.ledgerEntries.isEmpty())
            assertEquals("Cash activity is currently unavailable. Your investments remain available.", model.ledgerError)
            scenario.failures.clear(); model.reloadLedger()
            assertTrue(model.ledgerAvailable); assertNull(model.ledgerError)
        }
    }

    @Test fun ledgerAuthorizationAndCancellationDoNotBecomeOptionalDataFailures() = runTest {
        withModel { scenario, model ->
            scenario.failures["GET /api/bonds/cash-ledger"] = HttpStatusCode.Unauthorized
            assertEquals(401, assertFailsWith<ApiException> { model.reloadLedger() }.status)
            assertTrue(model.ledgerAvailable); assertNull(model.ledgerError)
            scenario.failures.clear(); scenario.holdLedger = true
            val job = launch { model.reloadLedger() }
            scenario.ledgerStarted.await(); job.cancelAndJoin()
            assertTrue(job.isCancelled); assertTrue(model.ledgerAvailable); assertNull(model.ledgerError)
        }
    }

    @Test fun ledgerWriteCompletesAfterReloadAndPortfolioRefresh() = runTest {
        withModel { scenario, model ->
            val entry = JsonRecord().put("id", "cash-after").put("amount", 1234)
            model.saveLedgerEntry(entry) {
                assertEquals("cash-after", model.ledgerEntries.single().text("id"))
                assertTrue(scenario.events.contains("GET /api/bonds/purchases"))
                scenario.events += "completed"
            }
            advanceUntilIdle()
            assertEquals("POST /api/bonds/cash-ledger", scenario.events.first())
            assertEquals("GET /api/bonds/cash-ledger", scenario.events[1])
            assertEquals("completed", scenario.events.last())
            assertEquals(entry.toString(), scenario.bodies.first().toString())
        }
    }

    @Test fun orderCallbackPrecedesRefreshAndRefreshFailureRetainsPendingProjection() = runTest {
        withModel { scenario, model ->
            scenario.failures["GET /api/bonds/share-orders"] = HttpStatusCode.ServiceUnavailable
            val order = JsonRecord().put("shares", 100).put("version", 3)
            model.saveShareOrder("order-new", order) { scenario.events += "completed" }
            advanceUntilIdle()
            assertEquals(listOf("PUT /api/bonds/share-orders/order-new", "completed", "GET /api/bonds/share-orders"), scenario.events)
            assertEquals(order.toString(), scenario.bodies.single().toString())
            assertFalse(model.shareOrdersAvailable)
            assertEquals("order-before", model.shareOrders.single().text("id"))
            assertEquals(model.shareOrders, model.portfolio.shareOrders)
            assertEquals("Pending share order saved.", model.message)
            scenario.failures["GET /api/bonds/share-orders"] = HttpStatusCode.MethodNotAllowed
            model.cancelShareOrder(JsonRecord().put("id", "order-before").put("version", 4)) { }
            advanceUntilIdle()
            assertTrue(model.shareOrders.isEmpty()); assertTrue(model.portfolio.shareOrders.isEmpty())
            assertEquals(4, scenario.bodies.last().optInt("version"))
        }
    }

    @Test fun rejectedOrderWriteDoesNotCallCompletionOrReload() = runTest {
        withModel { scenario, model ->
            scenario.failures["PUT /api/bonds/share-orders/order-new"] = HttpStatusCode.Conflict
            var completed = false
            model.saveShareOrder("order-new", JsonRecord()) { completed = true }
            advanceUntilIdle()
            assertFalse(completed)
            assertEquals(listOf("PUT /api/bonds/share-orders/order-new"), scenario.events)
            assertNotNull(model.error); assertNull(model.message)
        }
    }

    @Test fun statementResolvePublishesReviewThenReloadsItemsBeforeCompletion() = runTest {
        withModel { scenario, model ->
            model.showStatementRecordReview(scenario.statement)
            model.resolveStatementRecord(scenario.statement, null, "  Rechecked original evidence  ") {
                assertEquals("recorded", model.statementReviewRows!!.single().text("state"))
                assertEquals("recorded", model.statementItems.single().text("state"))
                scenario.events += "completed"
            }
            advanceUntilIdle()
            assertEquals(listOf("POST /api/bonds/statement-items/statement-one/resolve", "GET /api/bonds/statement-items", "completed"), scenario.events)
            val body = scenario.bodies.single()
            assertEquals("clear", body.text("action")); assertEquals("", body.text("purchaseId"))
            assertEquals("Rechecked original evidence", body.text("reason"))
            assertEquals("2026-11-01T12:00:00Z", body.text("expectedDecisionAt"))
        }
    }

    @Test fun statementBondCompletionPrecedesRefreshAndDoesNotMutateFigures() = runTest {
        withModel { scenario, model ->
            val figures = JsonRecord().put("faceValue", 100000)
            val original = figures.toString()
            model.showStatementRecordReview(scenario.statement)
            model.createStatementBond(scenario.statement, JsonRecord().put("id", "public-bond"), figures) {
                assertEquals("recorded", model.statementReviewRows!!.single().text("state"))
                scenario.events += "completed"
            }
            advanceUntilIdle()
            assertEquals("POST /api/bonds/statement-items/statement-one/bond", scenario.events[0])
            assertEquals("completed", scenario.events[1])
            assertTrue(scenario.events.drop(2).contains("GET /api/bonds/purchases"))
            assertEquals(original, figures.toString())
            assertEquals("public-bond", scenario.bodies.first().text("instrumentId"))
            assertTrue(scenario.bodies.first().optBoolean("confirmed"))
        }
    }

    @Test fun logoutClearsAllAccountRecordState() = runTest {
        withModel { _, model ->
            model.showStatementRecordReview(model.statementItems.single())
            model.logout(); advanceUntilIdle()
            assertTrue(model.ledgerEntries.isEmpty()); assertFalse(model.ledgerAvailable); assertNull(model.ledgerError)
            assertTrue(model.shareOrders.isEmpty()); assertFalse(model.shareOrdersAvailable)
            assertTrue(model.statementItems.isEmpty()); assertNull(model.statementReviewRows)
        }
    }
}
