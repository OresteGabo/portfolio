package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MigrationPreparationTest {
    private val account = MigrationAccount("https://fixture.example.test", "owner", "test account")

    private suspend fun prepare(snapshot: GuestPortfolio, previousItems: List<JsonRecord> = emptyList()): GuestMigration {
        val reader = object : GuestPortfolioReader {
            override suspend fun load() = snapshot
            override suspend fun documentBytes(id: String): ByteArray = error("Preparation must not read PDFs")
        }
        val remote = object : GuestMigrationRemote {
            override suspend fun status(account: MigrationAccount) = JsonRecord().put("accountId", account.id).put("items", JsonList(previousItems))
            override suspend fun document(account: MigrationAccount, row: MigrationRow, date: String, bytes: ByteArray, progress: (Int, Int) -> Unit): JsonRecord = error("Preparation must not upload")
            override suspend fun item(account: MigrationAccount, row: MigrationRow, record: JsonRecord, documents: List<String>): JsonRecord = error("Preparation must not transfer")
        }
        return GuestMigration(reader, remote, account) { true }.also { it.prepare(); assertTrue(it.prepared) }
    }

    @Test fun projectionPreservesOrderingReferencesAndParentLinksWithoutMutatingInputs() = runTest {
        val bond = JsonRecord().put("id", "guest:bond:a").put("bondName", "Treasury bond")
        val share = JsonRecord().put("id", "guest:share:b").put("securityName", "Bank of Kigali")
        val receipt = JsonRecord().put("id", "guest:receipt:c").put("purchaseId", "guest:bond:a").put("dueDate", "2027-01-15")
        val document = JsonRecord().put("id", "guest:document:d").put("fileName", "allocation.pdf")
        val cash = JsonRecord().put("id", "e").put("kind", "deposit").put("date", "2026-11-01").put("holdingId", "guest:share:b")
        val snapshot = GuestPortfolio(PortfolioSnapshot(purchases = listOf(bond), holdings = listOf(share), documents = listOf(document)),
            listOf(receipt), JsonRecord().put("cashLedger", JsonList(listOf(cash))), 1)
        val originals = listOf(bond, share, receipt, document, snapshot.metadata).map { it.toString() }

        val migration = prepare(snapshot)

        assertEquals(listOf("bond", "equity", "receipt", "document", "cash"), migration.rows.map { it.kind })
        assertEquals(listOf("guest:bond:a", "guest:share:b", "guest:receipt:c", "guest:document:d", "guest:cash:e"), migration.rows.map { it.id })
        assertEquals(listOf("Treasury bond", "Bank of Kigali", "Treasury bond · 2027-01-15", "allocation.pdf"), migration.rows.take(4).map { it.title })
        assertSame(bond, migration.rows[0].record)
        assertSame(share, migration.rows[1].record)
        assertSame(receipt, migration.rows[2].record)
        assertSame(document, migration.rows[3].record)
        assertEquals("guest:bond:a", migration.rows[2].parent)
        assertEquals("guest:share:b", migration.rows[4].parent)
        assertEquals(originals, listOf(bond, share, receipt, document, snapshot.metadata).map { it.toString() })
    }

    @Test fun duplicateBondIdsKeepTheFirstNameAndOrphanReceiptsKeepAnEmptyName() = runTest {
        val purchases = listOf(JsonRecord().put("id", "guest:bond:a").put("bondName", "First name"),
            JsonRecord().put("id", "guest:bond:a").put("bondName", "Second name"))
        val receipts = listOf(JsonRecord().put("id", "guest:receipt:a").put("purchaseId", "guest:bond:a"),
            JsonRecord().put("id", "guest:receipt:b").put("purchaseId", "guest:bond:missing"))
        val snapshot = GuestPortfolio(PortfolioSnapshot(purchases = purchases), receipts,
            JsonRecord().put("cashLedger", JsonList(listOf(JsonRecord().put("id", "x").put("holdingId", " ")))), 1)

        val migration = prepare(snapshot)

        assertEquals(listOf("First name · ", " · "), migration.rows.filter { it.kind == "receipt" }.map { it.title })
        assertNull(migration.rows.last().parent)
    }

    @Test fun receiptProjectionUsesBoundedPassesOverPurchases() = runTest {
        val size = 250
        val source = (0 until size).map { JsonRecord().put("id", "guest:bond:b$it").put("bondName", "Bond $it") }
        var purchaseReads = 0
        val purchases = object : AbstractList<JsonRecord>() {
            override val size = source.size
            override fun get(index: Int): JsonRecord { purchaseReads++; return source[index] }
        }
        val receipts = (0 until size).map { JsonRecord().put("id", "guest:receipt:r$it").put("purchaseId", "guest:bond:b$it") }

        val migration = prepare(GuestPortfolio(PortfolioSnapshot(purchases = purchases), receipts, JsonRecord(), 1))

        assertEquals(size * 2, migration.rows.size)
        assertEquals("Bond 249 · ", migration.rows.last().title)
        assertTrue(purchaseReads <= size * 4, "Receipt projection repeatedly scanned purchases: $purchaseReads reads")
    }


    @Test fun documentDatesPreferLocalValuesThenTheFirstHistoricalValueIncludingBlank() = runTest {
        val documents = listOf("local", "history", "blank").map { id ->
            JsonRecord().put("id", "guest:document:$id").put("documentDate", if (id == "local") "2026-11-01" else " ")
        }
        val previous = listOf(
            JsonRecord().put("sourceId", "guest:document:local").put("documentDate", "2025-01-01"),
            JsonRecord().put("sourceId", "guest:document:history").put("documentDate", "2026-10-01"),
            JsonRecord().put("sourceId", "guest:document:history").put("documentDate", "2026-10-02"),
            JsonRecord().put("sourceId", "guest:document:blank"),
            JsonRecord().put("sourceId", "guest:document:blank").put("documentDate", "2026-10-03"),
        )

        val migration = prepare(GuestPortfolio(PortfolioSnapshot(documents = documents), emptyList(), JsonRecord(), 1), previous)

        assertEquals(mapOf("guest:document:local" to "2026-11-01", "guest:document:history" to "2026-10-01", "guest:document:blank" to ""), migration.dates)
        assertEquals(documents.map { it.text("documentDate") }, listOf("2026-11-01", " ", " "))
    }


    @Test fun documentDateProjectionUsesOnePassOverHistory() {
        val size = 250
        val source = (0 until size).map { JsonRecord().put("sourceId", "guest:document:d$it").put("documentDate", "2026-11-01") }
        var historyReads = 0
        val history = object : AbstractList<JsonRecord>() {
            override val size = source.size
            override fun get(index: Int): JsonRecord { historyReads++; return source[index] }
        }
        val rows = (0 until size).map { MigrationRow("guest:document:d$it", "document", "PDF $it", JsonRecord()) }

        val dates = migrationDocumentDates(rows, history)

        assertEquals(size, dates.size)
        assertEquals("2026-11-01", dates["guest:document:d249"])
        assertTrue(historyReads <= size * 2, "Document projection repeatedly scanned history: $historyReads reads")
    }

}
