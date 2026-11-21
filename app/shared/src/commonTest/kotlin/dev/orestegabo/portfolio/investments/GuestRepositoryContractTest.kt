package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Exercise required capabilities through the contract, including a reconstructed owner. */
class GuestRepositoryContractTest {
    private class Disk : GuestSnapshotStorage {
        var bytes: ByteArray? = null
        var unreadable = false
        override suspend fun read(): ByteArray? { check(!unreadable); return bytes?.copyOf() }
        override suspend fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
    }
    private class Documents : GuestDocumentStorage {
        val entries = mutableMapOf<String, ByteArray>()
        override suspend fun read(id: String) = entries[id]?.copyOf()
        override suspend fun write(id: String, bytes: ByteArray) { entries[id] = bytes.copyOf() }
        override suspend fun delete(id: String) { entries.remove(id) }
    }
    private fun repository(disk: Disk, documents: Documents): GuestPortfolioRepository =
        LocalGuestPortfolioRepository(disk, documentStorage = documents, workDispatcher = Dispatchers.Unconfined,
            now = { "2027-02-01T12:00:00Z" }, today = { LocalDate(2027, 2, 1) })

    @Test fun yearlyGoalAndBothAmountsSurviveReconstructionAndBackupRestore() = runTest {
        val disk = Disk(); val docs = Documents(); val source = repository(disk, docs)
        val goal = InvestmentGoal(300000, 5000000, InvestmentGoalPeriod.YEARLY)
        source.setInvestmentGoal(goal)
        assertEquals(goal, investmentGoalFromRecord(repository(disk, docs).load().metadata))
        val restored = repository(Disk(), Documents()).restoreBackup(source.exportBackup())
        assertEquals(goal, investmentGoalFromRecord(restored.metadata))
        assertEquals(300000L, goal.copy(period = InvestmentGoalPeriod.MONTHLY).amount)
    }

    @Test fun requiredLedgerOperationsPersistAndRemainAvailableAfterReconstruction() = runTest {
        val disk = Disk(); val documents = Documents()
        val entry = JsonRecord().put("id", "deposit-one").put("kind", "deposit").put("date", "2026-11-01")
            .put("currency", "RWF").put("amount", 1000)
        val first = repository(disk, documents).saveLedgerEntry(entry)
        assertEquals("deposit-one", first.metadata.rows("cashLedger").single().text("id"))
        val reopened = repository(disk, documents)
        assertEquals(1000.0, reopened.load().metadata.rows("cashLedger").single().optDouble("amount"))
        assertTrue(reopened.deleteLedgerEntry("deposit-one").metadata.rows("cashLedger").isEmpty())
        assertTrue(repository(disk, documents).load().metadata.rows("cashLedger").isEmpty())
    }

    @Test fun requiredBackupOperationsRestoreTheOriginalDocumentThroughTheSameContract() = runTest {
        val source = repository(Disk(), Documents())
        val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
        source.importPdf(bytes, "contract.pdf", "equity", guestShareFixture().put("isin", "FIXTURE-SHARE"))
        val backup = source.exportBackup()
        val destinationDisk = Disk(); val destinationDocuments = Documents()
        val restored = repository(destinationDisk, destinationDocuments).restoreBackup(backup)
        assertEquals(1, restored.portfolio.holdings.size)
        val document = restored.portfolio.documents.single()
        assertContentEquals(bytes, repository(destinationDisk, destinationDocuments).documentBytes(document.text("id")))
        assertEquals("contract.pdf", document.text("fileName"))
    }

    @Test fun invalidOperationsAndUnreadableStorageFailWithoutReplacingSavedData() = runTest {
        val disk = Disk(); val documents = Documents(); val owner = repository(disk, documents)
        owner.saveBond(guestBondFixture())
        val before = requireNotNull(disk.bytes).copyOf()
        assertFails { owner.saveLedgerEntry(JsonRecord().put("id", "invalid/id")) }
        assertFails { owner.deleteLedgerEntry("missing") }
        assertFails { owner.restoreBackup("not a backup".encodeToByteArray()) }
        assertContentEquals(before, disk.bytes)
        disk.unreadable = true
        assertFails { owner.load() }
        assertContentEquals(before, disk.bytes)
    }
}
