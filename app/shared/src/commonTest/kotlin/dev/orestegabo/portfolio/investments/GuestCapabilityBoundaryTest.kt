package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GuestCapabilityBoundaryTest {
    private val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
    private val account = MigrationAccount("https://fixture.example.test", "owner", "owner@example.test")

    private class Importer : GuestInvestmentImporter {
        var writes = 0
        var savedBytes: ByteArray? = null
        var savedTarget: String? = null
        var savedName = ""
        val result = GuestPortfolio(PortfolioSnapshot(), emptyList(), JsonRecord(), 1)
        override suspend fun load() = GuestPortfolio(PortfolioSnapshot(), emptyList(), JsonRecord(), 0)
        override suspend fun documentBytes(id: String) = "%PDF-1.7 retained\n%%EOF".encodeToByteArray()
        override suspend fun importPdf(bytes: ByteArray, fileName: String, kind: String, body: JsonRecord, targetId: String?): GuestPortfolio {
            assertEquals("bond", kind)
            assertEquals(2200000.0, body.optDouble("faceValue"))
            writes++; savedBytes = bytes.copyOf(); savedTarget = targetId; savedName = fileName
            return result
        }
    }

    @Test fun privateImportWorksWithOnlyReadAndAtomicImportCapabilities() = runTest {
        val importer = Importer()
        val workflow = GuestPdfImport(importer, Dispatchers.Unconfined)
        val picker = object : PdfPicker {
            override val pickedPdfName = "allocation.pdf"
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes.copyOf() }
        }
        val parser = object : InvestmentDocumentParser {
            override var onProgress: (String) -> Unit = {}
            override suspend fun parse(pdf: ByteArray) = JsonRecord().put("kind", "bond").put("fields", guestBondFixture())
                .put("missingFields", JsonList()).put("warnings", JsonList())
            override fun cancel() {}
        }
        workflow.select(picker, parser)
        assertNotNull(workflow.review); assertEquals(0, importer.writes)
        assertFailsWith<IllegalStateException> { workflow.save(guestBondFixture()) }
        workflow.selectTarget("existing-purchase"); workflow.confirmDetails(true)
        assertSame(importer.result, workflow.save(guestBondFixture()))
        assertEquals(1, importer.writes)
        assertEquals("allocation.pdf", importer.savedName)
        assertEquals("existing-purchase", importer.savedTarget)
        assertContentEquals(bytes, importer.savedBytes)
        assertNull(workflow.review)
    }

    private inner class Reader : GuestPortfolioReader {
        var revision = 1
        var reads = 0
        val bond = guestBondFixture().put("id", "guest:bond:fixture")
        val document = JsonRecord().put("id", "guest:document:fixture").put("kind", "bond").put("fileName", "allocation.pdf")
        override suspend fun load(): GuestPortfolio {
            reads++
            return GuestPortfolio(PortfolioSnapshot(purchases = listOf(JsonRecord(bond.toString())),
                documents = listOf(JsonRecord(document.toString()))), emptyList(), JsonRecord(), revision)
        }
        override suspend fun documentBytes(id: String): ByteArray { assertEquals("guest:document:fixture", id); return bytes.copyOf() }
    }

    private class Remote : GuestMigrationRemote {
        var writes = 0
        override suspend fun status(account: MigrationAccount) = JsonRecord().put("accountId", account.id).put("items", JsonList())
        private fun accepted(row: MigrationRow) = JsonRecord().put("sourceId", row.id).put("kind", row.kind)
            .put("targetId", "00000000-0000-4000-8000-000000000001").put("status", "created")
        override suspend fun document(account: MigrationAccount, row: MigrationRow, date: String, bytes: ByteArray, progress: (Int, Int) -> Unit): JsonRecord {
            writes++; return accepted(row)
        }
        override suspend fun item(account: MigrationAccount, row: MigrationRow, record: JsonRecord, documents: List<String>): JsonRecord {
            assertFalse(record.has("id")); writes++; return accepted(row)
        }
    }

    @Test fun migrationPreviewsAndTransfersUsingOnlyTheLocalReadCapability() = runTest {
        val reader = Reader(); val remote = Remote()
        val migration = GuestMigration(reader, remote, account) { true }
        migration.prepare()
        assertTrue(migration.prepared); assertNull(migration.failure)
        migration.showOriginal(migration.rows.single { it.kind == "document" })
        val preview = assertNotNull(migration.preview?.bytes)
        assertContentEquals(bytes, preview)
        migration.closePreview(); assertTrue(preview.all { it == 0.toByte() })
        val before = reader.bond.toString()
        migration.choose("guest:bond:fixture", true); migration.consent = true
        migration.transfer()
        assertNull(migration.failure)
        assertEquals("created", migration.results["guest:bond:fixture"]?.status)
        assertEquals(1, remote.writes); assertEquals(2, reader.reads)
        assertEquals(before, reader.bond.toString())
        assertContentEquals(bytes, reader.documentBytes("guest:document:fixture"))
    }

    @Test fun readOnlyMigrationStillChecksRevisionBeforeAnyRemoteWrite() = runTest {
        val reader = Reader(); val remote = Remote()
        val migration = GuestMigration(reader, remote, account) { true }
        migration.prepare(); migration.choose("guest:bond:fixture", true); migration.consent = true
        reader.revision++
        migration.transfer()
        assertNotNull(migration.failure); assertEquals(0, remote.writes)
    }
}
