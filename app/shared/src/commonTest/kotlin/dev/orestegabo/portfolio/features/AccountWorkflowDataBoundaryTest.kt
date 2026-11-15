package dev.orestegabo.portfolio.features

import dev.orestegabo.portfolio.features.documents.PortfolioStatements
import dev.orestegabo.portfolio.features.shares.PortfolioShareOrders
import dev.orestegabo.portfolio.features.transactions.PortfolioCashLedger
import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.PortfolioDataApi

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** A data-only fake has no session restoration, cookie clearing, closing or HTTP engine capability. */
class AccountWorkflowDataBoundaryTest {
    private class Data(private val id: String) : PortfolioDataApi {
        val calls = mutableListOf<Pair<String, String>>()
        var lastBody: JsonRecord? = null
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            calls += path to method
            if (body != null) lastBody = body
            val row = JsonRecord().put("id", id)
            return JsonRecord().put("entries", JsonList(listOf(row))).put("orders", JsonList(listOf(row)))
                .put("items", JsonList(listOf(row))).put("rows", JsonList(listOf(row)))
        }
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("Unexpected binary request")
    }

    @Test fun constructionDoesNotAcquireClientOrPerformIo() {
        val unavailable: () -> PortfolioDataApi = { error("Client must remain lazy") }
        assertFalse(PortfolioCashLedger(unavailable) {}.available)
        assertTrue(PortfolioShareOrders(unavailable) {}.orders.isEmpty())
        assertNull(PortfolioStatements(unavailable) {}.reviewRows)
    }

    @Test fun pendingOrdersFollowProviderReplacementWithoutOwningTheSession() = runTest {
        val first = Data("first")
        val second = Data("second")
        var current: PortfolioDataApi = first
        val published = mutableListOf<String>()
        val orders = PortfolioShareOrders({ current }) { published += it.single().text("id") }
        orders.reload(); current = second; orders.reload()
        assertEquals(listOf("first", "second"), published)
        assertEquals("second", orders.orders.single().text("id"))
        assertEquals(1, first.calls.size); assertEquals(1, second.calls.size)
    }

    @Test fun ledgerAndReconciliationNeedOnlyTheDataCapability() = runTest {
        val data = Data("statement-one")
        var refreshed = 0
        val ledger = PortfolioCashLedger({ data }) { refreshed++ }
        ledger.save(JsonRecord().put("id", "cash"))
        assertTrue(ledger.available); assertEquals(1, refreshed)
        assertEquals(listOf("POST", "GET"), data.calls.map { it.second })
        val statements = PortfolioStatements({ data }) { error("Resolving evidence must not refresh financial records") }
        val row = JsonRecord().put("id", "statement-one")
        statements.showRecord(row)
        var completed = false
        statements.resolve(row, null, "Checked original evidence") { completed = true }
        assertTrue(completed)
        assertEquals("statement-one", statements.items.single().text("id"))
        assertEquals("clear", assertNotNull(data.lastBody).text("action"))
        assertEquals("POST", data.calls[data.calls.lastIndex - 1].second)
    }
}
