package dev.orestegabo.portfolio.features

import dev.orestegabo.portfolio.features.transactions.PortfolioCashLedger
import dev.orestegabo.portfolio.features.shares.PortfolioShareOrders
import dev.orestegabo.portfolio.features.documents.PortfolioStatements
import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FeatureStateOwnershipTest {
    private class Data : PortfolioDataApi {
        var amount = 1000
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            val row = JsonRecord().put("id", "row").put("amount", amount).put("version", 3)
            val rows = JsonList(listOf(row))
            return JsonRecord().put("entries", rows).put("orders", rows).put("items", rows).put("rows", rows)
        }
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("No PDF access")
    }

    private fun assertProtected(rows: List<JsonRecord>, amount: Int = 1000) {
        assertFailsWith<IllegalStateException> { rows.single().put("amount", 0) }
        assertTrue(runCatching { (rows as MutableList<JsonRecord>).clear() }.isFailure)
        assertEquals(amount, rows.single().getInt("amount"))
    }

    @Test fun ledgerPublishesOwnedSnapshotsAndValidWritesStillRefresh() = runTest {
        val data = Data(); var refreshes = 0
        val ledger = PortfolioCashLedger({ data }) { refreshes++ }
        ledger.reload(); val previous = ledger.entries
        assertProtected(previous)
        data.amount = 2000
        ledger.save(JsonRecord().put("amount", 2000))
        assertEquals(1, refreshes); assertProtected(ledger.entries, 2000)
        assertProtected(previous)
        ledger.clear(); assertTrue(ledger.entries.isEmpty())
    }

    @Test fun pendingOrderProjectionReceivesTheSameProtectedSnapshot() = runTest {
        val data = Data(); var projection: List<JsonRecord> = emptyList()
        val orders = PortfolioShareOrders({ data }) { projection = it }
        orders.reload()
        assertSame(orders.orders, projection); assertProtected(projection)
        var completed = false
        data.amount = 2000
        orders.save("row", JsonRecord().put("version", 3)) { completed = true }
        assertTrue(completed); assertSame(orders.orders, projection); assertProtected(projection, 2000)
        orders.clear(); assertTrue(projection.isEmpty())
    }

    @Test fun everyStatementPublicationOwnsReviewAndListRecords() = runTest {
        val data = Data(); val statements = PortfolioStatements({ data }) {}
        val input = JsonRecord().put("id", "row").put("amount", 1000)
        statements.showRecord(input); input.put("amount", 0)
        assertProtected(assertNotNull(statements.reviewRows))
        statements.showDocument("document"); assertProtected(assertNotNull(statements.reviewRows))
        statements.resolve(statements.reviewRows!!.single(), null, "Checked original evidence") {}
        assertProtected(statements.items); assertProtected(assertNotNull(statements.reviewRows))
        statements.createBond(statements.items.single(), JsonRecord().put("id", "terms"), JsonRecord()) {}
        assertProtected(assertNotNull(statements.reviewRows))
        statements.check(JsonRecord().put("rows", JsonList(listOf(JsonRecord().put("kind", "bond"))))) { returned ->
            returned.single().put("amount", 0) // The callback result is detached from owned state.
        }
        assertProtected(statements.items)
        statements.load(); assertProtected(statements.items)
        statements.clear(); assertTrue(statements.items.isEmpty()); assertNull(statements.reviewRows)
    }
}
