@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.features

import dev.orestegabo.portfolio.features.documents.PortfolioStatements
import dev.orestegabo.portfolio.features.shares.PortfolioShareOrders
import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AccountReadLifecycleTest {
    private class PendingData : PortfolioDataApi {
        val replies = ArrayDeque<CompletableDeferred<JsonRecord>>()
        fun enqueue() = CompletableDeferred<JsonRecord>().also { replies += it }
        override suspend fun request(path: String, method: String, body: JsonRecord?) = replies.removeFirst().await()
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("Unexpected binary read")
    }
    private fun response(id: String) = JsonRecord().put("orders", JsonList(listOf(JsonRecord().put("id", id))))
        .put("items", JsonList(listOf(JsonRecord().put("id", id))))

    @Test fun clearedPendingOrdersCannotRepublishToThePortfolio() = runTest {
        val data = PendingData(); var projected = emptyList<JsonRecord>()
        val orders = PortfolioShareOrders({ data }) { projected = it }
        val reply = data.enqueue(); val request = launch { orders.reload() }; runCurrent()
        orders.clear(); reply.complete(response("old-account")); request.join()
        assertTrue(orders.orders.isEmpty()); assertTrue(projected.isEmpty()); assertFalse(orders.available)
    }

    @Test fun newerOrdersWinOverLateSuccessOrUnauthorizedFailure() = runTest {
        for (failure in listOf(false, true)) {
            val data = PendingData(); val published = mutableListOf<String>()
            val orders = PortfolioShareOrders({ data }) { published += it.single().text("id") }
            val first = data.enqueue(); val second = data.enqueue()
            val older = launch { orders.reload() }; runCurrent()
            val newer = launch { orders.reload() }; runCurrent()
            second.complete(response("newer")); newer.join()
            if (failure) first.completeExceptionally(ApiException(401, "stale")) else first.complete(response("older"))
            older.join()
            assertEquals(listOf("newer"), published); assertTrue(orders.available)
        }
    }

    @Test fun clearedStatementsCannotBeRestoredByALateListRead() = runTest {
        val data = PendingData(); val statements = PortfolioStatements({ data }) {}
        val reply = data.enqueue(); val request = launch { statements.load() }; runCurrent()
        statements.clear(); reply.complete(response("old-account")); request.join()
        assertTrue(statements.items.isEmpty()); assertNull(statements.reviewRows)
    }

    @Test fun latestStatementListWinsWithoutDiscardingTheSelectedReview() = runTest {
        val data = PendingData(); val statements = PortfolioStatements({ data }) {}
        statements.showRecord(JsonRecord().put("id", "selected"))
        val first = data.enqueue(); val second = data.enqueue()
        val older = launch { statements.load() }; runCurrent()
        val newer = launch { statements.load() }; runCurrent()
        second.complete(response("newer")); newer.join()
        first.complete(response("older")); older.join()
        assertEquals("newer", statements.items.single().text("id"))
        assertEquals("selected", statements.reviewRows?.single()?.text("id"))
    }

    @Test fun currentAuthorizationFailuresStillReachTheSessionOwner() = runTest {
        val data = PendingData()
        val orders = PortfolioShareOrders({ data }) { error("Unauthorized reads cannot publish orders") }
        data.enqueue().completeExceptionally(ApiException(401, "current"))
        assertEquals(401, assertFailsWith<ApiException> { orders.reload() }.status)
        assertFalse(orders.available)
        val statements = PortfolioStatements({ data }) {}
        data.enqueue().completeExceptionally(ApiException(401, "current"))
        assertEquals(401, assertFailsWith<ApiException> { statements.load() }.status)
        assertTrue(statements.items.isEmpty())
    }

}
