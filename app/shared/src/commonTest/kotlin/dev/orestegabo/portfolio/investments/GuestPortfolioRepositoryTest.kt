@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Synthetic evidence only; no real account or investor data. */
internal fun guestBondFixture() = JsonRecord().put("bondName", "Synthetic 7-year bond").put("isin", "FIXTURE-BOND")
    .put("instrumentType", "treasury").put("currency", "RWF").put("status", "active").put("purchaseDate", "2026-07-15")
    .put("settlementDate", "2026-07-16").put("maturityDate", "2033-07-15").put("faceValue", 2200000)
    .put("pricePercent", 100).put("feesPaid", 0).put("accruedInterestPaid", 0).put("couponRate", .115)
    .put("withholdingTaxRate", .05).put("couponFrequency", 2).put("scheduleConfidence", "confirmed")
    .put("couponDates", JsonList(listOf("2027-01-15", "2027-07-15", "2033-07-15")))
internal fun guestShareFixture() = JsonRecord().put("securityName", "Synthetic I&M purchase").put("ticker", "FIXTURE")
    .put("currency", "RWF").put("status", "active").put("tradeDate", "2026-07-15").put("settlementDate", "2026-07-16")
    .put("shares", 400).put("pricePerShare", 90).put("brokerageCommission", 615.60)
    .put("rseTransactionFee", 0).put("csdTransactionLevy", 0).put("cmaTransactionFee", 0)

class GuestPortfolioRepositoryTest {
    private class Disk : GuestSnapshotStorage {
        var bytes: ByteArray? = null
        var failWrite = false; var failRead = false; var writes = 0
        override suspend fun read(): ByteArray? { check(!failRead); return bytes?.copyOf() }
        override suspend fun write(bytes: ByteArray) { check(!failWrite); this.bytes = bytes.copyOf(); writes++ }
    }
    private fun repo(disk: Disk) = LocalGuestPortfolioRepository(disk, now = { "2027-02-01T12:00:00Z" }, today = { LocalDate(2027,2,1) }, workDispatcher = Dispatchers.Unconfined)
    @Test fun crudSurvivesRepositoryReconstructionWithStableIdentifiers() = runTest {
        val disk = Disk(); val repository = repo(disk)
        assertTrue(repository.load().portfolio.purchases.isEmpty())
        val added = repository.saveBond(guestBondFixture()); val id = added.portfolio.purchases.single().text("id")
        val updated = repo(disk).saveBond(guestBondFixture().put("faceValue", 1000000), id)
        assertEquals(id, updated.portfolio.purchases.single().text("id")); assertEquals(2, updated.revision)
        assertEquals(1000000.0, repo(disk).load().portfolio.purchases.single().optDouble("faceValue"))
        repository.deleteBond(id)
        assertTrue(repo(disk).load().portfolio.purchases.isEmpty())
    }
    @Test fun shareLotsRemainSeparateAndSupportEditDelete() = runTest {
        val disk = Disk(); val repository = repo(disk)
        repository.saveShare(guestShareFixture())
        val added = repository.saveShare(guestShareFixture().put("pricePerShare", 91)); val id = added.portfolio.holdings.last().text("id")
        assertEquals(2, added.portfolio.holdings.size)
        val changed = repo(disk).saveShare(guestShareFixture().put("shares", 500), id)
        assertEquals(2, changed.portfolio.holdings.size)
        assertEquals(45615.60, changed.portfolio.holdings.single { it.text("id") == id }.optDouble("netAmountPayable"))
        assertEquals(1, repository.deleteShare(id).portfolio.holdings.size)
    }
    @Test fun financialResultsReuseExistingCalculationsAndPreserveOfficialDates() = runTest {
        val repository = repo(Disk())
        val bond = guestBondFixture(); val equity = guestShareFixture().put("grossConsideration", 36000).put("totalCharges", 615.60).put("netAmountPayable", 36615.60)
        repository.saveBond(bond); val loaded = repository.saveShare(equity)
        val date = LocalDate(2027,2,1)
        assertEquals(PortfolioSnapshot(listOf(bond), listOf(equity)).summary(date), loaded.portfolio.summary(date))
        val terms = requireNotNull(loaded.portfolio.purchases.single().terms())
        assertEquals(126500.0, terms.grossCouponPayment()); assertEquals(120175.0, terms.netCouponPayment())
        assertEquals(listOf(LocalDate(2027,1,15), LocalDate(2027,7,15), LocalDate(2033,7,15)), couponSchedule(terms))
        val tenYear = bond.put("faceValue", 1000000).put("couponRate", .12).put("maturityDate", "2036-08-15")
        assertEquals(60000.0, requireNotNull(repository.saveBond(tenYear).portfolio.purchases.last().terms()).grossCouponPayment())
        val bk = guestShareFixture().put("shares",500).put("pricePerShare",670).put("brokerageCommission",5728.50)
        assertEquals(340728.50, repository.saveShare(bk).portfolio.holdings.last().optDouble("netAmountPayable"))
    }
    @Test fun statedDocumentTotalsArePreservedWithinTheExistingAccountValidationTolerance() = runTest {
        val raw = guestShareFixture().put("grossConsideration",36000.01).put("totalCharges",615.61).put("netAmountPayable",36615.62)
        val disk = Disk()
        val saved = repo(disk).saveShare(raw).portfolio.holdings.single()
        for (key in listOf("grossConsideration","totalCharges","netAmountPayable")) assertEquals(raw.optDouble(key),saved.optDouble(key))
        assertEquals(PortfolioSnapshot(holdings=listOf(raw)).summary(LocalDate(2027,2,1)),repo(disk).load().portfolio.summary(LocalDate(2027,2,1)))
    }
    @Test fun receiptUpdatesAreIdempotentAndNeverAutomaticallyMarkIncomeReceived() = runTest {
        val disk = Disk(); val repository = repo(disk)
        val id = repository.saveBond(guestBondFixture()).portfolio.purchases.single().text("id")
        assertTrue(repo(disk).load().receipts.isEmpty())
        val received = repository.setReceipt(id,"2027-01-15",true).receipts.single()
        assertEquals(120175.0, received.optDouble("amount")); assertEquals("manual", received.text("method"))
        val notReceived = repo(disk).setReceipt(id,"2027-01-15",false).receipts.single()
        assertEquals(received.text("id"),notReceived.text("id")); assertFalse(notReceived.optBoolean("received"))
        assertFailsWith<IllegalArgumentException> { repository.setReceipt(id,"2027-07-15",true) }
        assertFailsWith<IllegalArgumentException> { repository.setReceipt(id,"2027-01-16",true) }
        assertTrue(repository.deleteBond(id).receipts.isEmpty())
    }
    @Test fun recordedReceiptAmountDoesNotChangeWhenBondTermsAreCorrected() = runTest {
        val disk = Disk(); val repository = repo(disk)
        val id = repository.saveBond(guestBondFixture()).portfolio.purchases.single().text("id")
        repository.setReceipt(id,"2027-01-15",true)
        repository.saveBond(guestBondFixture().put("faceValue",1000000),id)
        assertEquals(120175.0, repository.setReceipt(id,"2027-01-15",false).receipts.single().optDouble("amount"))
    }
    @Test fun versionOneMigrationIsAtomicPreservesFactsAndRunsOnce() = runTest {
        val disk = Disk(); val original = repo(disk).saveBond(guestBondFixture())
        val v1 = JsonRecord().put("schemaVersion",1).put("workspace","guest-personal").put("metadata",original.metadata)
            .put("purchases",JsonList(original.portfolio.purchases)).put("holdings",JsonList()).toString().encodeToByteArray()
        disk.bytes = v1.copyOf(); disk.failWrite = true
        assertFailsWith<IllegalStateException> { repo(disk).load() }; assertContentEquals(v1,disk.bytes)
        disk.failWrite = false
        val migrated = repo(disk).load()
        assertEquals(original.portfolio.purchases.single().toString(),migrated.portfolio.purchases.single().toString())
        assertEquals(4,JsonRecord(requireNotNull(disk.bytes).decodeToString()).getInt("schemaVersion"))
        val writes = disk.writes; repo(disk).load(); assertEquals(writes,disk.writes)
    }
    @Test fun corruptionUnknownVersionsAndWrongWorkspaceNeverOverwriteExistingBytes() = runTest {
        val disk = Disk(); repo(disk).saveBond(guestBondFixture())
        val original = requireNotNull(disk.bytes).decodeToString()
        for (broken in listOf("{broken", original.replace("\"schemaVersion\":4","\"schemaVersion\":99"), original.replace("guest-personal","account-cloud"), original.replace("guest:bond:","sample:bond:"))) {
            disk.bytes = broken.encodeToByteArray(); val writes = disk.writes
            assertFails { repo(disk).load() }; assertFails { repo(disk).saveBond(guestBondFixture()) }
            assertEquals(broken,requireNotNull(disk.bytes).decodeToString()); assertEquals(writes,disk.writes)
        }
    }
    @Test fun writeAndReadFailuresPreservePriorCommit() = runTest {
        val disk = Disk(); repo(disk).saveBond(guestBondFixture()); val original = disk.bytes?.copyOf()
        disk.failWrite = true; assertFails { repo(disk).saveShare(guestShareFixture()) }; assertContentEquals(original,disk.bytes)
        disk.failWrite = false; disk.failRead = true
        assertFails { repo(disk).saveShare(guestShareFixture()) }; assertContentEquals(original,disk.bytes)
    }
    @Test fun mutationsOfReturnedRecordsNeverModifyDurableData() = runTest {
        val disk = Disk(); val added = repo(disk).saveBond(guestBondFixture())
        added.portfolio.purchases.single().put("faceValue",1); added.metadata.put("createdAt","bad")
        assertEquals(2200000.0,repo(disk).load().portfolio.purchases.single().optDouble("faceValue"))
    }
    @Test fun concurrentWritesAreSerializedWithoutLosingLots() = runTest {
        val disk = Disk(); val repository = repo(disk)
        (1..25).map { async { repository.saveShare(guestShareFixture()) } }.awaitAll()
        val loaded = repository.load(); assertEquals(25,loaded.portfolio.holdings.size)
        assertEquals(25, loaded.portfolio.holdings.map { it.text("id") }.distinct().size)
    }
    @Test fun foreignIdsReferencesMalformedAmountsAndInconsistentFeesAreRejected() = runTest {
        val disk = Disk(); val repository = repo(disk)
        assertFails { repository.saveBond(guestBondFixture().put("id","sample:bond:example")) }
        assertFails { repository.saveBond(guestBondFixture().put("confirmationDocumentId","cloud-document")) }
        assertFails { repository.saveBond(guestBondFixture().put("couponFrequency",2.5)) }
        assertFails { repository.saveShare(guestShareFixture().put("netAmountPayable",36000)) }
        assertFails { repository.saveShare(guestShareFixture().put("shares",.5)) }
        assertFails { repository.saveBond(guestBondFixture(),"cloud-id") }
        assertEquals(0,disk.writes)
    }
    @Test fun localDocumentReferencesAreValidatedRetainedOnEditsAndNotRemoteUrls() = runTest {
        val disk = Disk(); val repository = repo(disk)
        val doc = JsonRecord().put("fileName","synthetic.pdf").put("mimeType","application/pdf").put("sha256","a".repeat(64)).put("localFileId","fixture-original")
        val docId = repository.saveDocumentReference(doc).portfolio.documents.single().text("id")
        val id = repository.saveBond(guestBondFixture().put("documentIds",JsonList(listOf(docId)))).portfolio.purchases.single().text("id")
        assertEquals(docId,repository.saveBond(guestBondFixture(),id).portfolio.purchases.single().getJSONArray("documentIds").getString(0))
        assertFails { repository.saveDocumentReference(doc.put("localFileId","https://private.example/statement")) }
        assertFails { repository.saveShare(guestShareFixture().put("documentIds",JsonList(listOf("account-file")))) }
        assertEquals(1,repo(disk).load().portfolio.documents.size)
    }
    @Test fun modelRoutesGuestCrudLocallyAndDoesNotMixSampleOrCloudCaches() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val disk = Disk(); val repository = repo(disk)
        val settings = object : ServerSettings {
            var choice: String? = GuestPath.PERSONAL.stored
            override fun readSessionChoice() = choice
            override fun writeSessionChoice(value: String?) { choice = value }
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) {}
        }
        val model = PortfolioModel(settings, guestRepository = repository, apiFactory = { error("Guest must not construct an account HTTP client") })
        try {
            advanceUntilIdle(); model.savePurchase(guestBondFixture(),null) {}; advanceUntilIdle()
            assertEquals(1,model.guestPortfolio?.portfolio?.purchases?.size); assertEquals(1, model.purchases.size); assertNull(model.user)
            model.saveEquity(guestShareFixture(),{}); advanceUntilIdle()
            val id = requireNotNull(model.guestPortfolio).portfolio.purchases.single().text("id")
            model.confirmCoupon(id,"2027-01-15"); advanceUntilIdle()
            assertEquals(1,model.guestPortfolio?.receipts?.size)
            model.enterGuest(GuestPath.SAMPLE); advanceUntilIdle(); assertNull(model.guestPortfolio)
            model.savePurchase(guestBondFixture(),null) {}; advanceUntilIdle()
            assertEquals(1,repo(disk).load().portfolio.purchases.size)
            model.enterGuest(GuestPath.PERSONAL); advanceUntilIdle(); assertEquals(1,model.guestPortfolio?.portfolio?.purchases?.size)
            model.background(); assertNull(model.guestPortfolio); model.foreground(); advanceUntilIdle()
            assertEquals(1,model.guestPortfolio?.portfolio?.holdings?.size)
            model.beginAccountSignIn(); assertNull(model.guestPortfolio); assertTrue(model.purchases.isEmpty())
        } finally { ViewModelStore().apply { put("fixture",model); clear() }; Dispatchers.resetMain() }
    }
}
