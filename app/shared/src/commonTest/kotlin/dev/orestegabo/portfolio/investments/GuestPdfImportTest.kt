@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import kotlin.test.*

class GuestPdfImportTest {
    private class Disk : GuestSnapshotStorage {
        var bytes: ByteArray? = null; var writes = 0; var failAt = -1
        override suspend fun read() = bytes?.copyOf()
        override suspend fun write(bytes: ByteArray) { writes++; check(writes != failAt); this.bytes = bytes.copyOf() }
    }
    private class Files : GuestDocumentStorage {
        val files = mutableMapOf<String,ByteArray>(); var failDelete = false
        override suspend fun read(id: String) = files[id]?.copyOf()
        override suspend fun write(id: String, bytes: ByteArray) { files[id] = bytes.copyOf() }
        override suspend fun delete(id: String) { check(!failDelete); files.remove(id) }
    }
    private fun repo(disk: Disk, files: Files) = LocalGuestPortfolioRepository(disk, documentStorage = files, workDispatcher = Dispatchers.Unconfined)
    private fun pdf(suffix: String = "") = "%PDF-1.7 synthetic envelope $suffix\n%%EOF".encodeToByteArray()
    private fun parser(kind: String = "bond") = object : InvestmentDocumentParser {
        override var onProgress: (String) -> Unit = {}
        override suspend fun parse(pdf: ByteArray) = JsonRecord().put("kind",kind).put("fields",guestBondFixture()).put("missingFields",JsonList()).put("warnings",JsonList())
        override fun cancel() {}
    }
    private fun actions(bytes: ByteArray) = object : NativeActions {
        override val pickedPdfName = "synthetic-private.pdf"
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes.copyOf() }
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = error("No export or network")
        override fun openUrl(url: String) = error("No remote URL")
        override fun shareText(text: String) = error("No sharing")
    }
    @Test fun parsingRequiresReviewAndCorrectionBeforeAnyFileOrInvestmentIsSaved() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files); val workflow=GuestPdfImport(repository)
        workflow.select(actions(pdf()),parser())
        assertNotNull(workflow.review); assertTrue(repository.load().portfolio.purchases.isEmpty()); assertTrue(files.files.isEmpty())
        assertFailsWith<IllegalStateException> { workflow.save(guestBondFixture()) }
        workflow.confirmed=true
        val corrected = guestBondFixture().put("feesPaid",1000)
        val saved = workflow.save(corrected)
        assertEquals(1000.0,saved.portfolio.purchases.single().optDouble("feesPaid"))
        val doc=saved.portfolio.documents.single()
        assertContentEquals(pdf(),repository.documentBytes(doc.text("id")))
        assertTrue(saved.portfolio.purchases.single().hasDocument(doc.text("id"))); assertNull(workflow.review)
    }
    @Test fun sameBytesAreIdempotentAndRetainedOriginalCanRestoreDeletedPurchaseAfterReview() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val first=repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture())
        val doc=first.portfolio.documents.single()
        val duplicate=repository.importPdf(pdf(),"different-name.pdf","bond",guestBondFixture())
        assertEquals(1,duplicate.portfolio.purchases.size); assertEquals(1,files.files.size); assertEquals(first.revision,duplicate.revision)
        val workflow=GuestPdfImport(repository); workflow.select(actions(pdf()),parser())
        assertNotNull(workflow.duplicate); assertNull(workflow.review)
        repository.deleteBond(first.portfolio.purchases.single().text("id"))
        workflow.select(actions(pdf()),parser()); assertNotNull(workflow.review); assertNull(workflow.duplicate)
        workflow.confirmed=true; val restored=workflow.save(guestBondFixture())
        assertEquals(doc.text("id"),restored.portfolio.documents.single().text("id")); assertEquals(doc.text("createdAt"),restored.portfolio.documents.single().text("createdAt"))
        assertEquals(1,restored.portfolio.purchases.size); assertEquals(1,files.files.size)
    }
    @Test fun aMissingOriginalCanBeRestoredAfterReviewWithoutDuplicatingItsInvestment() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val first=repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture())
        files.files.clear()
        val workflow=GuestPdfImport(repository); workflow.select(actions(pdf()),parser())
        assertNotNull(workflow.review); assertNull(workflow.duplicate)
        workflow.confirmed=true; val repaired=workflow.save(guestBondFixture())
        assertEquals(first.portfolio.purchases.single().text("id"),repaired.portfolio.purchases.single().text("id"))
        assertContentEquals(pdf(),repository.documentBytes(first.portfolio.documents.single().text("id")))
    }
    @Test fun matchingRecordRequiresExplicitSelectionAndDoesNotCreateANewHolding() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val id=repository.saveBond(guestBondFixture().put("notes","Keep original notes").put("status","submitted")).portfolio.purchases.single().text("id")
        assertFails { repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture()) }
        assertFails { repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture().put("feesPaid",999),id) }
        assertTrue(files.files.isEmpty())
        val attached=repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture(),id)
        assertEquals(id,attached.portfolio.purchases.single().text("id")); assertEquals(1,attached.portfolio.documents.size)
        assertEquals("Keep original notes",attached.portfolio.purchases.single().text("notes"))
        assertEquals("active",attached.portfolio.purchases.single().text("status")) // Reviewed allocation confirms the pending purchase once.
    }
    @Test fun similarDatesAndAmountsDoNotMatchAnotherSecurityAndAmbiguousLotsRequireAChoice() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        repository.saveBond(guestBondFixture())
        val existing=repository.saveBond(guestBondFixture()).portfolio.purchases
        assertFails { repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture()) }
        val linked=repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture(),existing.last().text("id"))
        assertEquals(2,linked.portfolio.purchases.size)
        assertFalse(linked.portfolio.purchases.first().hasDocument(linked.portfolio.documents.single().text("id")))
        val another=repository.importPdf(pdf("other"),"other.pdf","bond",guestBondFixture().put("isin","OTHER-SECURITY"))
        assertEquals(3,another.portfolio.purchases.size)
    }
    @Test fun fileAndManifestInterruptedSaveIsRecoveredWithoutPhantomFinancialRecords() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        disk.failAt=2
        assertFails { repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture()) }
        assertEquals(1,files.files.size)
        val reopened=repo(disk,files).load()
        assertTrue(reopened.portfolio.purchases.isEmpty()); assertTrue(reopened.portfolio.documents.isEmpty()); assertTrue(files.files.isEmpty())
    }
    @Test fun deletingPdfRetainsInvestmentsAndResumesInterruptedProtectedFileRemoval() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val first=repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture()); val id=first.portfolio.documents.single().text("id")
        files.failDelete=true; assertFails { repository.deleteDocument(id) }
        files.failDelete=false; val reopened=repo(disk,files).load()
        assertEquals(1,reopened.portfolio.purchases.size); assertTrue(reopened.portfolio.documents.isEmpty()); assertTrue(files.files.isEmpty())
        assertFalse(reopened.portfolio.purchases.single().hasDocument(id))
    }
    @Test fun versionTwoMigrationRetainsEveryExistingFinancialRecord() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val first=repository.saveBond(guestBondFixture())
        val root=kotlinx.serialization.json.Json.parseToJsonElement(requireNotNull(disk.bytes).decodeToString()) as kotlinx.serialization.json.JsonObject
        disk.bytes=kotlinx.serialization.json.JsonObject(root.filterKeys { it!="pendingFiles" } + ("schemaVersion" to kotlinx.serialization.json.JsonPrimitive(2))).toString().encodeToByteArray()
        val versionTwo = requireNotNull(disk.bytes).copyOf()
        disk.failAt = disk.writes + 1
        assertFails { repo(disk,files).load() }; assertContentEquals(versionTwo,disk.bytes)
        disk.failAt = -1
        val loaded=repo(disk,files).load()
        assertEquals(first.portfolio.purchases.single().toString(),loaded.portfolio.purchases.single().toString())
        assertEquals(4,JsonRecord(requireNotNull(disk.bytes).decodeToString()).getInt("schemaVersion"))
    }
    @Test fun malformedLargeUnsupportedAndCancelledImportsNeverPersistOrEchoExtractedContent() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        for (bytes in listOf("not-pdf-private-account".encodeToByteArray(),ByteArray(MAX_PDF_BYTES+1))) {
            val workflow=GuestPdfImport(repository); workflow.select(actions(bytes),parser())
            assertNull(workflow.review); assertNotNull(workflow.failure); assertFalse(requireNotNull(workflow.failure).contains("private-account"))
        }
        val unsupported=GuestPdfImport(repository); unsupported.select(actions(pdf()),parser("statement")); assertNotNull(unsupported.failure)
        assertTrue(files.files.isEmpty()); assertEquals(0,disk.writes)
    }
    @Test fun cancellingParsingDiscardsPrivateReviewAndCancelsTheLocalParser() = runTest {
        val disk=Disk(); val files=Files(); val workflow=GuestPdfImport(repo(disk,files), StandardTestDispatcher(testScheduler))
        val entered=CompletableDeferred<Unit>(); var cancelled=false
        val waiting=object:InvestmentDocumentParser {
            override var onProgress:(String)->Unit={}
            override suspend fun parse(pdf:ByteArray):JsonRecord { entered.complete(Unit); awaitCancellation() }
            override fun cancel() { cancelled=true }
        }
        val job=launch { workflow.select(actions(pdf()),waiting) }
        entered.await(); job.cancelAndJoin()
        assertTrue(cancelled); assertNull(workflow.review); assertNull(workflow.previewBytes); assertTrue(files.files.isEmpty()); assertEquals(0,disk.writes)
    }
    @Test fun localParserTimeoutShowsAnErrorWithoutSavingOrUploading() = runTest {
        val disk=Disk(); val files=Files(); val workflow=GuestPdfImport(repo(disk,files),StandardTestDispatcher(testScheduler))
        val timeout=object:InvestmentDocumentParser {
            override var onProgress:(String)->Unit={}
            override suspend fun parse(pdf:ByteArray):JsonRecord = kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.delay(2); error("unreachable") }
            override fun cancel() {}
        }
        workflow.select(actions(pdf()),timeout)
        assertNotNull(workflow.failure); assertNull(workflow.review); assertTrue(files.files.isEmpty()); assertEquals(0,disk.writes)
    }
    @Test fun previewVerifiesHashAndDoesNotUseRemoteFallback() = runTest {
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val saved=repository.importPdf(pdf(),"one.pdf","bond",guestBondFixture())
        val doc=saved.portfolio.documents.single(); files.files[doc.text("localFileId")]=pdf("changed")
        assertFails { repository.documentBytes(doc.text("id")) }
        assertEquals(1,repository.load().portfolio.purchases.size)
    }
    @Test fun guestModelImportAndSaveNeverConstructAnHttpClient() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val disk=Disk(); val files=Files(); val repository=repo(disk,files)
        val settings=object:ServerSettings {
            override fun readSessionChoice()=GuestPath.PERSONAL.stored
            override fun readOrigin():String?=null
            override fun writeOrigin(origin:String) {}
        }
        val model=PortfolioModel(settings,guestRepository=repository,importWorkDispatcher=StandardTestDispatcher(testScheduler),apiFactory={ error("No guest document HTTP traffic permitted") })
        val engine=object:InvestmentEngine {
            override var onProgress:(String)->Unit={}
            override suspend fun run(command:String,payload:JsonRecord,pdf:ByteArray?):JsonRecord { assertEquals("pdf",command); return parser().parse(requireNotNull(pdf)) }
            override fun cancel() {}
            override fun destroy() {}
        }
        try {
            advanceUntilIdle(); model.selectGuestPdf(actions(pdf()),engine); advanceUntilIdle()
            assertNotNull(model.guestImport?.review); assertTrue(repository.load().portfolio.documents.isEmpty())
            requireNotNull(model.guestImport).confirmed=true
            model.savePurchase(guestBondFixture(),null,model::finishGuestImport); advanceUntilIdle()
            assertEquals(1,model.guestPortfolio?.portfolio?.purchases?.size); assertEquals(1, model.documents.size); assertNull(model.user)
            val queued = pdf("queued-private")
            NativePdfInbox.offer(queued,"guest-test-private-transition","private-name.pdf")
            model.beginAccountSignIn()
            assertNull(NativePdfInbox.pending); assertTrue(queued.all { it == 0.toByte() })
            assertEquals(1,repository.load().portfolio.documents.size)
        } finally { ViewModelStore().apply { put("fixture",model); clear() }; Dispatchers.resetMain() }
    }
}
