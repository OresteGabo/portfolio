@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class GuestExperienceTest {
    private class Disk : GuestSnapshotStorage {
        var bytes: ByteArray? = null
        var reject = false
        override suspend fun read() = bytes?.copyOf()
        override suspend fun write(bytes: ByteArray) { check(!reject); this.bytes = bytes.copyOf() }
    }
    private class Files : GuestDocumentStorage {
        val rows = mutableMapOf<String,ByteArray>()
        var fail = false
        var deletes = 0
        var failAt: Int? = null
        override suspend fun read(id: String) = rows[id]?.copyOf()
        override suspend fun write(id: String, bytes: ByteArray) { rows[id] = bytes.copyOf() }
        override suspend fun delete(id: String) { check(!fail && ++deletes != failAt); rows.remove(id) }
    }
    private fun repo(disk: Disk, files: Files) = LocalGuestPortfolioRepository(disk, documentStorage = files, workDispatcher = Dispatchers.Unconfined,
        today = { LocalDate.parse("2026-11-02") })
    private fun pdf() = "%PDF-1.7 synthetic original\n%%EOF".encodeToByteArray()

    @Test fun confirmedDeletionRemovesHoldingsReceiptsOriginalsAndTargetWithoutChangingAnotherWorkspace() = runTest {
        val disk = Disk(); val files = Files(); val repo = repo(disk,files)
        val saved = repo.importPdf(pdf(),"synthetic.pdf","bond",guestBondFixture().put("couponDates", JsonList(listOf("2026-10-15", "2027-04-15", "2033-07-15"))))
        repo.setReceipt(saved.portfolio.purchases.single().text("id"), "2026-10-15", true)
        assertEquals(1, repo.load().receipts.size)
        repo.setMonthlyTarget(250000)
        val other = Disk(); repo(other, Files()).saveBond(guestBondFixture())
        val before = other.bytes?.copyOf()
        val cleared = repo.deleteAll()
        assertTrue(cleared.portfolio.purchases.isEmpty()); assertTrue(cleared.portfolio.documents.isEmpty())
        assertTrue(cleared.receipts.isEmpty()); assertFalse(cleared.metadata.has("monthlyTarget"))
        assertTrue(files.rows.isEmpty()); assertContentEquals(before, other.bytes)
        assertFails { repo.documentBytes(saved.portfolio.documents.single().text("id")) }
        assertTrue(repo(disk,files).load().portfolio.purchases.isEmpty())
    }
    @Test fun deletionResumesFromDurableIntentAfterAnInterruptedProtectedFileCleanup() = runTest {
        val disk = Disk(); val files = Files(); val repo = repo(disk,files)
        repo.importPdf(pdf(),"synthetic.pdf","bond",guestBondFixture())
        files.fail = true
        assertFails { repo.deleteAll() }
        assertTrue(JsonRecord(requireNotNull(disk.bytes).decodeToString()).getJSONObject("metadata").optBoolean("deletionRequested"))
        files.fail = false
        val recovered = repo(disk,files).load()
        assertTrue(recovered.portfolio.documents.isEmpty()); assertTrue(recovered.portfolio.purchases.isEmpty())
        assertFalse(recovered.metadata.has("deletionRequested")); assertTrue(files.rows.isEmpty())
    }
    @Test fun failedInitialDeletionCommitAndCorruptManifestNeverRemoveOriginals() = runTest {
        val disk = Disk(); val files = Files(); val repo = repo(disk,files)
        repo.importPdf(pdf(),"synthetic.pdf","bond",guestBondFixture())
        val before = disk.bytes?.copyOf(); disk.reject = true
        assertFails { repo.deleteAll() }; assertContentEquals(before,disk.bytes); assertEquals(1,files.rows.size)
        disk.reject = false; disk.bytes = "broken".encodeToByteArray()
        assertFails { repo.deleteAll() }; assertEquals(1,files.rows.size)
    }
    @Test fun deletionAcrossMoreThanOneJournalBatchResumesWithoutLosingTheRemainingFileIntent() = runTest {
        val disk = Disk(); val files = Files(); val repo = repo(disk, files)
        repeat(101) { index ->
            val file = "synthetic-$index"
            repo.saveDocumentReference(JsonRecord().put("fileName", "synthetic.pdf").put("mimeType", "application/pdf")
                .put("sha256", "a".repeat(64)).put("localFileId", file))
            files.rows[file] = pdf()
        }
        files.failAt = 101
        assertFails { repo.deleteAll() }
        assertEquals(1, files.rows.size)
        files.failAt = null
        assertTrue(repo(disk,files).load().portfolio.documents.isEmpty())
        assertTrue(files.rows.isEmpty())
    }
    @Test fun existingScreensReadLocalDataAndProvenanceWithoutAnyAccountClient() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val disk = Disk(); val files = Files(); val repository = repo(disk,files)
        val saved = repository.importPdf(pdf(),"synthetic.pdf","bond",guestBondFixture())
        val settings = object: ServerSettings {
            override fun readSessionChoice() = GuestPath.PERSONAL.stored
            override fun writeSessionChoice(value: String?) {}
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) {}
        }
        val model = PortfolioModel(settings, guestRepository = repository, apiFactory = { error("No guest HTTP allowed") })
        try {
            advanceUntilIdle()
            assertTrue(model.portfolioReady); assertTrue(model.historyComplete); assertTrue(model.couponTrackingAvailable)
            assertTrue(model.calendar.isEmpty())
            assertTrue(investmentCalendarEvents(model.calendar, model.portfolio).any { it.category == CalendarCategory.COUPON })
            assertEquals(saved.portfolio.purchases.single().text("id"), model.purchases.single().text("id"))
            val document = model.documents.single()
            assertFalse(document.has("downloadUrl")); assertEquals("synthetic.pdf",document.text("originalFileName"))
            assertEquals(1,investmentEvidence(model.purchases.single(),model.documents).size)
            assertEquals(1,documentBonds(document,model.purchases).size)
            model.openDocumentPreview(document); advanceUntilIdle()
            assertContentEquals(pdf(),model.documentPreview?.bytes)
            val transientBytes = requireNotNull(model.documentPreview?.bytes)
            model.beginAccountSignIn(); assertNull(model.documentPreview); assertTrue(transientBytes.all { it == 0.toByte() })
            model.returnToGuest(); advanceUntilIdle(); assertContentEquals(pdf(), model.documentBytes(document))
            model.loadInvestmentHistory(true); model.refresh(); advanceUntilIdle()
            model.updateMonthlyInvestmentTarget(300000); advanceUntilIdle(); assertEquals(300000,model.monthlyInvestmentTarget)
            model.enterGuest(GuestPath.SAMPLE); advanceUntilIdle()
            assertTrue(model.purchases.all { it.text("id").startsWith("sample:") }); assertFalse(model.canEditInvestments)
            val revision = model.guestViewRevision; model.resetSample(); assertEquals(revision+1,model.guestViewRevision)
            assertEquals(1, repository.load().portfolio.purchases.size)
            model.enterGuest(GuestPath.PERSONAL); advanceUntilIdle(); assertEquals(saved.portfolio.purchases.single().text("id"), model.purchases.single().text("id"))
            model.deleteGuestData(); advanceUntilIdle()
            assertFalse(model.busy); assertNull(model.guestStorageError); assertTrue(model.purchases.isEmpty()); assertTrue(files.rows.isEmpty())
            model.beginAccountSignIn(); assertTrue(model.purchases.isEmpty()); assertTrue(model.documents.isEmpty())
            assertFails { model.documentBytes(document) } // A local reference cannot fall through after a mode change.
        } finally { ViewModelStore().apply { put("test",model); clear() }; Dispatchers.resetMain() }
    }
    @Test fun sampleDatesAndCouponsArePlausibleButNeverClaimRealReceiptsOrMarketPrices() {
        val today = LocalDate.parse("2026-11-02")
        val sample = guestSamplePortfolio(today)
        val bond = sample.purchases.single()
        assertTrue(couponSchedule(requireNotNull(bond.terms())).any { it > today })
        assertTrue(sample.recentBonds(today).isNotEmpty())
        assertEquals("estimated",bond.text("scheduleConfidence"))
        assertTrue(sample.valuedShares(today,null).all { it.closingPrice == null && it.gainLoss == null })
    }
}
