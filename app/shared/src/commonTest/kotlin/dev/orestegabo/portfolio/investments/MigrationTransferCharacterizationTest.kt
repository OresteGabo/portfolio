package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MigrationTransferCharacterizationTest {
    private val account = MigrationAccount("https://fixture.example.test", "owner", "owner@example.test")
    private val bondA = "guest:bond:a"
    private val bondB = "guest:bond:b"
    private val doc = "guest:document:allocation"
    private val receipt = "guest:receipt:coupon"
    private val cash = "guest:cash:deposit"
    private val target = "00000000-0000-4000-8000-000000000001"

    private inner class Reader : GuestPortfolioReader {
        val original = "%PDF-1.7 synthetic original\n%%EOF".encodeToByteArray()
        override suspend fun documentBytes(id: String): ByteArray { assertEquals(doc, id); return original.copyOf() }
        override suspend fun load() = GuestPortfolio(PortfolioSnapshot(
            purchases = listOf(guestBondFixture().put("id", bondA).put("documentIds", JsonList(listOf(doc))),
                guestBondFixture().put("id", bondB)),
            documents = listOf(JsonRecord().put("id", doc).put("kind", "bond").put("fileName", "allocation.pdf")
                .put("documentDate", "2026-11-01"))),
            listOf(JsonRecord().put("id", receipt).put("purchaseId", bondA).put("dueDate", "2027-01-15")
                .put("amount", 120175).put("received", true).put("method", "manual").put("currency", "RWF")
                .put("updatedAt", "2027-02-01T10:00:00Z")),
            JsonRecord().put("cashLedger", JsonList(listOf(JsonRecord().put("id", "deposit").put("kind", "deposit")
                .put("date", "2026-11-01").put("currency", "RWF").put("amount", 1000)))), 1)
    }

    private inner class Remote : GuestMigrationRemote {
        val calls = mutableListOf<String>()
        val attachments = mutableMapOf<String, List<String>>()
        val itemFailures = mutableMapOf<String, Exception>()
        var borrowedBytes: ByteArray? = null
        var documentReply: ((MigrationRow) -> JsonRecord)? = null
        override suspend fun status(account: MigrationAccount) = JsonRecord().put("accountId", account.id).put("items", JsonList())
        private fun accepted(row: MigrationRow) = JsonRecord().put("sourceId", row.id).put("kind", row.kind)
            .put("targetId", target).put("status", "created")
        override suspend fun document(account: MigrationAccount, row: MigrationRow, date: String, bytes: ByteArray, progress: (Int, Int) -> Unit): JsonRecord {
            calls += row.id; borrowedBytes = bytes
            assertEquals("2026-11-01", date)
            progress(1, 1)
            return documentReply?.invoke(row) ?: accepted(row)
        }
        override suspend fun item(account: MigrationAccount, row: MigrationRow, record: JsonRecord, documents: List<String>): JsonRecord {
            calls += row.id; attachments[row.id] = documents
            assertFalse(record.has("id")); assertFalse(record.has("documentIds"))
            itemFailures[row.id]?.let { throw it }
            return accepted(row)
        }
    }

    private suspend fun prepare(reader: Reader, remote: Remote): GuestMigration {
        val migration = GuestMigration(reader, remote, account) { true }
        migration.prepare()
        assertTrue(migration.prepared)
        for (id in listOf(doc, bondA, bondB, receipt, cash)) migration.choose(id, true)
        migration.consent = true
        return migration
    }

    @Test fun documentsPrecedeInvestmentsThenReceiptsAndCashAndSuccessfulReferencesAreLinked() = runTest {
        val reader = Reader(); val remote = Remote(); val migration = prepare(reader, remote)
        migration.transfer()
        assertEquals(listOf(doc, bondA, bondB, receipt, cash), remote.calls)
        assertEquals(listOf(target), remote.attachments[bondA])
        assertTrue(remote.attachments.getValue(bondB).isEmpty())
        assertTrue(migration.results.values.all { it.status == "created" })
        assertNull(migration.failure); assertFalse(migration.running); assertNull(migration.progress); assertFalse(migration.consent)
        assertTrue(assertNotNull(remote.borrowedBytes).all { it == 0.toByte() })
        assertTrue(reader.original.any { it != 0.toByte() })
    }

    @Test fun eachRecoverableStatusDefersDependentRecordsButContinuesIndependentTransfers() = runTest {
        for (status in listOf(400, 409, 413)) {
            val reader = Reader(); val remote = Remote().apply { documentReply = { throw ApiException(status, "synthetic conflict") } }
            val migration = prepare(reader, remote)
            migration.transfer()
            assertEquals(listOf(doc, bondB, cash), remote.calls)
            assertEquals("review", migration.results[doc]?.status)
            assertEquals("A selected PDF has not finished transferring.", migration.results[bondA]?.message)
            assertEquals("Transfer this receipt's bond first.", migration.results[receipt]?.message)
            assertEquals("created", migration.results[bondB]?.status); assertEquals("created", migration.results[cash]?.status)
            assertNull(migration.failure); assertFalse(migration.running); assertNull(migration.progress)
            assertTrue(assertNotNull(remote.borrowedBytes).all { it == 0.toByte() })
        }
    }

    @Test fun fatalErrorsAndMalformedAcknowledgmentsStopWithoutPublishingAnUnverifiedResult() = runTest {
        val replies: List<(MigrationRow) -> JsonRecord> = listOf(
            { throw ApiException(401, "synthetic unauthorized") },
            { throw ApiException(500, "synthetic server failure") },
            { row -> JsonRecord().put("sourceId", row.id).put("kind", "wrong-kind").put("targetId", target).put("status", "created") }
        )
        for (reply in replies) {
            val remote = Remote().apply { documentReply = reply }
            val migration = prepare(Reader(), remote)
            migration.transfer()
            assertEquals(listOf(doc), remote.calls)
            assertTrue(migration.results.isEmpty()); assertNotNull(migration.failure)
            assertFalse(migration.running); assertNull(migration.progress); assertFalse(migration.consent)
            assertTrue(assertNotNull(remote.borrowedBytes).all { it == 0.toByte() })
        }
    }

    @Test fun rejectedFinancialItemsDeferTheirReceiptsButDoNotDiscardSuccessfulPdfResults() = runTest {
        for (status in listOf(400, 409, 413)) {
            val remote = Remote().apply { itemFailures[bondA] = ApiException(status, "synthetic financial review") }
            val migration = prepare(Reader(), remote)
            migration.transfer()
            assertEquals(listOf(doc, bondA, bondB, cash), remote.calls)
            assertEquals("created", migration.results[doc]?.status)
            assertEquals("review", migration.results[bondA]?.status)
            assertEquals("Transfer this receipt's bond first.", migration.results[receipt]?.message)
            assertEquals("created", migration.results[bondB]?.status)
            assertNull(migration.failure); assertFalse(migration.running); assertNull(migration.progress)
        }
    }

    @Test fun cancellationPropagatesAndErasesBorrowedPdfBytesWithoutContinuingTheBatch() = runTest {
        val remote = Remote().apply { documentReply = { throw CancellationException("synthetic cancellation") } }
        val migration = prepare(Reader(), remote)
        assertFailsWith<CancellationException> { migration.transfer() }
        assertEquals(listOf(doc), remote.calls); assertTrue(migration.results.isEmpty())
        assertNull(migration.failure); assertFalse(migration.running); assertNull(migration.progress)
        assertTrue(assertNotNull(remote.borrowedBytes).all { it == 0.toByte() })
    }
}
