@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class GuestMigrationTest {
    private class Disk: GuestSnapshotStorage {
        var bytes: ByteArray?=null
        override suspend fun read()=bytes?.copyOf()
        override suspend fun write(bytes: ByteArray) { this.bytes=bytes.copyOf() }
    }
    private class Files: GuestDocumentStorage {
        val saved=mutableMapOf<String,ByteArray>()
        override suspend fun read(id: String)=saved[id]?.copyOf()
        override suspend fun write(id: String,bytes: ByteArray) { saved[id]=bytes.copyOf() }
        override suspend fun delete(id: String) { saved.remove(id) }
    }
    private val account=MigrationAccount("https://fixture.example.test","owner","owner@example.test")
    private fun pdf()="%PDF-1.7 private synthetic fixture\n%%EOF".encodeToByteArray()
    private fun TestScope.repo(disk: Disk,files: Files)=LocalGuestPortfolioRepository(disk,documentStorage=files,
        workDispatcher=StandardTestDispatcher(testScheduler),today={LocalDate.parse("2026-11-02")})
    private class Remote: GuestMigrationRemote {
        val calls=mutableListOf<Pair<String,String>>()
        val saved=mutableMapOf<String,JsonRecord>()
        val attachments=mutableMapOf<String,List<String>>()
        var loseResponse=false
        var failDocument=false
        var wait: CompletableDeferred<Unit>?=null
        private var next=0
        override suspend fun status(account: MigrationAccount)=JsonRecord().put("accountId",account.id).put("items",JsonList(saved.filterKeys { it.startsWith(account.id+"|") }.values.toList()))
        private suspend fun ack(account: MigrationAccount,row: MigrationRow): JsonRecord {
            wait?.await()
            val key=account.id+"|"+row.id
            val prior=saved[key]
            val result=prior?.let { JsonRecord(it.toString()).put("status","already-transferred") } ?: JsonRecord().put("sourceId",row.id).put("kind",row.kind)
                .put("targetId","00000000-0000-4000-8000-${(++next).toString().padStart(12,'0')}").put("status","created")
            saved[key]=result
            if(loseResponse) { loseResponse=false;error("connection lost after commit") }
            return result
        }
        override suspend fun document(account: MigrationAccount,row: MigrationRow,date: String,bytes: ByteArray,progress: (Int,Int)->Unit): JsonRecord {
            calls+="document" to row.id
            check(bytes.contentEquals("%PDF-1.7 private synthetic fixture\n%%EOF".encodeToByteArray()))
            if(failDocument) throw ApiException(409,"synthetic conflict")
            return ack(account,row).put("documentId",saved[account.id+"|"+row.id]?.text("targetId"))
        }
        override suspend fun item(account: MigrationAccount,row: MigrationRow,record: JsonRecord,documents: List<String>): JsonRecord {
            calls+=row.kind to row.id
            attachments[row.id]=documents
            check(!record.has("id") && !record.has("documentIds"))
            return ack(account,row)
        }
    }
    @Test fun preparationDeclineAndMissingConsentNeverUploadAndAlwaysRetainLocalOriginals()=runTest {
        val disk=Disk();val files=Files();val repo=repo(disk,files)
        repo.importPdf(pdf(),"private-name.pdf","bond",guestBondFixture())
        val original=requireNotNull(disk.bytes).copyOf();val remote=Remote()
        val flow=GuestMigration(repo,remote,account){true};flow.prepare()
        assertTrue(flow.prepared);assertTrue(flow.selected.isEmpty());assertTrue(remote.calls.isEmpty())
        assertFails { flow.transfer() };flow.clear()
        assertContentEquals(original,disk.bytes);assertEquals(1,files.saved.size);assertTrue(remote.calls.isEmpty())
    }
    @Test fun onlySelectedFinancialRecordsTransferAndUnselectedPdfsNeverLeaveTheDevice()=runTest {
        val disk=Disk();val files=Files();val repo=repo(disk,files)
        repo.importPdf(pdf(),"unselected.pdf","bond",guestBondFixture());repo.saveShare(guestShareFixture())
        val original=disk.bytes?.copyOf();val remote=Remote();val flow=GuestMigration(repo,remote,account){true};flow.prepare()
        val bond=flow.rows.single { it.kind=="bond" };flow.choose(bond.id,true);flow.consent=true;flow.transfer()
        assertEquals(listOf("bond" to bond.id),remote.calls);assertEquals("created",flow.results[bond.id]?.status)
        assertContentEquals(original,disk.bytes);assertEquals(1,files.saved.size)
    }
    @Test fun selectedPdfsRequireVerifiedDatesAndDependencyFailuresDoNotCreateUnprovenHoldings()=runTest {
        val disk=Disk();val files=Files();val repo=repo(disk,files)
        repo.importPdf(pdf(),"selected.pdf","bond",guestBondFixture());repo.saveShare(guestShareFixture())
        val remote=Remote().apply { failDocument=true };val flow=GuestMigration(repo,remote,account){true};flow.prepare()
        flow.rows.forEach { flow.choose(it.id,true) };flow.consent=true
        assertFails { flow.transfer() };assertTrue(remote.calls.isEmpty())
        val doc=flow.rows.single { it.kind=="document" };flow.date(doc.id,"2026-07-16");assertFalse(flow.consent)
        flow.consent=true;flow.transfer()
        assertFalse(remote.calls.any { it.first=="bond" });assertTrue(remote.calls.any { it.first=="equity" })
        assertEquals("review",flow.results[doc.id]?.status);assertEquals(1,files.saved.size)
    }
    @Test fun selectedOriginalPrecedesItsInvestmentAndIsLinkedWithoutSendingOtherOriginals()=runTest {
        val disk=Disk();val files=Files();val repo=repo(disk,files)
        val original=repo.importPdf(pdf(),"selected.pdf","bond",guestBondFixture())
        repo.saveShare(guestShareFixture())
        val doc=original.portfolio.documents.single();val bond=original.portfolio.purchases.single()
        val remote=Remote();val flow=GuestMigration(repo,remote,account){true};flow.prepare()
        flow.choose(bond.text("id"),true);flow.choose(doc.text("id"),true);flow.date(doc.text("id"),"2026-07-16")
        flow.consent=true;flow.transfer()
        assertEquals(listOf("document","bond"),remote.calls.map { it.first })
        assertEquals(listOf(requireNotNull(flow.results[doc.text("id")]).target),remote.attachments[bond.text("id")])
        assertEquals(1,repo.load().portfolio.holdings.size);assertEquals(1,files.saved.size)
    }
    @Test fun lostAcknowledgmentsAndRecreatedReviewRetryTheSameSourcesWithoutDuplicateServerItems()=runTest {
        val disk=Disk();val files=Files();val repo=repo(disk,files);repo.saveBond(guestBondFixture());repo.saveShare(guestShareFixture())
        val original=disk.bytes?.copyOf();val remote=Remote().apply { loseResponse=true }
        val flow=GuestMigration(repo,remote,account){true};flow.prepare();flow.rows.forEach { flow.choose(it.id,true) };flow.consent=true;flow.transfer()
        assertNotNull(flow.failure);assertEquals(1,remote.saved.size);assertContentEquals(original,disk.bytes)
        val recovered=GuestMigration(repo,remote,account){true};recovered.prepare();assertEquals(1,recovered.previous.size)
        assertTrue(recovered.selected.isEmpty());assertFails { recovered.transfer() }
        recovered.rows.forEach { recovered.choose(it.id,true) };recovered.consent=true;recovered.transfer()
        assertEquals(2,remote.saved.size);assertTrue(recovered.results.values.any { it.status=="already-transferred" });assertContentEquals(original,disk.bytes)
    }
    @Test fun receiptsRequireTheirBondAndRemovingSelectionResetsConsentAndDependentSelection()=runTest {
        val repo=repo(Disk(),Files())
        val bond=repo.saveBond(guestBondFixture().put("couponDates",JsonList(listOf("2026-10-15","2027-04-15","2033-07-15")))).portfolio.purchases.single()
        repo.setReceipt(bond.text("id"),"2026-10-15",true)
        val remote=Remote();val flow=GuestMigration(repo,remote,account){true};flow.prepare()
        val receipt=flow.rows.single { it.kind=="receipt" };assertFalse(flow.selectable(receipt));assertTrue(receipt.title.contains(bond.text("bondName")));assertTrue(receipt.title.contains("2026-10-15"))
        flow.choose(bond.text("id"),true);flow.choose(receipt.id,true);flow.consent=true
        flow.choose(bond.text("id"),false);assertFalse(flow.consent);assertTrue(flow.selected.isEmpty())
        flow.choose(bond.text("id"),true);flow.choose(receipt.id,true);flow.consent=true;flow.transfer()
        assertEquals(listOf("bond","receipt"),remote.calls.map { it.first })
        assertEquals(1,repo.load().receipts.size)
    }
    @Test fun accountSwitchAndConcurrentTransferAttemptsCannotSendToTheWrongAccount()=runTest {
        val repo=repo(Disk(),Files());repo.saveBond(guestBondFixture())
        val gate=CompletableDeferred<Unit>();val remote=Remote().apply { wait=gate };var active=true
        val flow=GuestMigration(repo,remote,account){active};flow.prepare();flow.choose(flow.rows.single().id,true);flow.consent=true
        val running=launch { flow.transfer() };runCurrent();assertTrue(flow.running)
        flow.transfer();assertEquals(1,remote.calls.size)
        active=false;gate.complete(Unit);running.join()
        assertNull(flow.results.values.firstOrNull());assertNotNull(flow.failure)
        assertFails { flow.consent=true;flow.transfer() };assertEquals(1,remote.calls.size)
        assertEquals(1,repo.load().portfolio.purchases.size)
    }
    @Test fun closingAnInFlightReviewPreventsFollowingTransfersWithoutDeletingSources()=runTest {
        val repo=repo(Disk(),Files());repo.saveBond(guestBondFixture());repo.saveShare(guestShareFixture())
        val gate=CompletableDeferred<Unit>();val remote=Remote().apply { wait=gate }
        val flow=GuestMigration(repo,remote,account){true};flow.prepare();flow.rows.forEach { flow.choose(it.id,true) };flow.consent=true
        val task=launch { flow.transfer() };runCurrent();assertEquals(1,remote.calls.size)
        flow.clear();gate.complete(Unit);task.join()
        assertEquals(1,remote.calls.size);assertTrue(flow.results.isEmpty());assertEquals(1,repo.load().portfolio.purchases.size)
        assertEquals(1,repo.load().portfolio.holdings.size)
    }
    @Test fun localChangesAfterReviewAndCancellationPreserveDataWithoutAutomaticContinuation()=runTest {
        val repo=repo(Disk(),Files());repo.saveBond(guestBondFixture())
        val remote=Remote();val flow=GuestMigration(repo,remote,account){true};flow.prepare();flow.choose(flow.rows.single().id,true);flow.consent=true
        repo.setMonthlyTarget(150000);flow.transfer();assertNotNull(flow.failure);assertTrue(remote.calls.isEmpty())
        flow.prepare();flow.choose(flow.rows.single().id,true);flow.consent=true;remote.wait=CompletableDeferred()
        val operation=launch { flow.transfer() };runCurrent();operation.cancelAndJoin();assertFalse(flow.running)
        assertEquals(1,repo.load().portfolio.purchases.size)
        flow.clear();assertTrue(flow.rows.isEmpty());assertFalse(flow.consent)
    }
    @Test fun realAdapterUsesAccountBoundPrivateChunksAndWhitelistedItemBodiesOnly()=runTest {
        val requests=mutableListOf<HttpRequestData>();val id="00000000-0000-4000-8000-000000000001"
        val api=PortfolioApi(account.origin,MockEngine { request ->
            requests+=request
            respond(if(request.url.encodedPath.endsWith("status")) """{"accountId":"owner","items":[]}"""
                else """{"sourceId":"guest:document:fixture","kind":"document","targetId":"$id","documentId":"$id","status":"created"}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val remote=ApiGuestMigrationRemote(api);remote.status(account)
            assertEquals(1,requests.size);assertEquals(HttpMethod.Get,requests.single().method)
            val bytes=pdf();val row=MigrationRow("guest:document:fixture","document","private-local-name.pdf",JsonRecord().put("kind","bond").put("sha256",importPdfDigest(bytes)))
            remote.document(account,row,"2026-07-16",bytes){_,_->}
            val body=(requests.last().body as io.ktor.http.content.TextContent).text
            assertTrue(body.contains("\"accountId\":\"owner\""));assertFalse(body.contains("private-local-name.pdf"));assertFalse(body.contains("Bond issuance"))
            val item=migrationRecord(MigrationRow("guest:bond:fixture","bond","fixture",guestBondFixture().put("id","guest:bond:fixture").put("documentIds",JsonList(listOf("unselected-private-reference")))))
            assertFalse(item.has("id"));assertFalse(item.has("documentIds"));assertFalse(item.has("sha256"))
        } finally { api.close() }
    }
    @Test fun normalLoginDoesNotInspectOrUploadGuestDataUntilTheUserOpensReview()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val disk=Disk();val files=Files();val repo=repo(disk,files);repo.importPdf(pdf(),"private.pdf","bond",guestBondFixture())
        val original=disk.bytes?.copyOf();val paths=mutableListOf<Pair<HttpMethod,String>>()
        val settings=object:ServerSettings {
            var choice: String?=GuestPath.PERSONAL.stored
            override fun readSessionChoice()=choice
            override fun writeSessionChoice(value: String?) { choice=value }
            override fun readOrigin()=account.origin
            override fun writeOrigin(origin: String) {}
        }
        val model=PortfolioModel(settings,guestRepository=repo,assets={"[]".encodeToByteArray()},apiFactory={origin->PortfolioApi(origin,MockEngine(MockEngineConfig().apply {
            dispatcher=StandardTestDispatcher(testScheduler)
            addHandler {
            paths+=it.method to it.url.encodedPath
            respond(when {
                it.url.encodedPath.endsWith("/auth/login")->"""{"user":{"id":"owner","email":"owner@example.test"}}"""
                it.url.encodedPath.endsWith("/guest-migration/status")->"""{"accountId":"owner","items":[]}"""
                it.url.encodedPath.endsWith("/mobile-overview")->"""{"version":1,"purchases":[],"holdings":[]}"""
                else->"{}"
            },headers=headersOf(HttpHeaders.ContentType,"application/json"))
        } }))})
        try {
            advanceUntilIdle();model.beginAccountSignIn();model.login(account.label,"synthetic-test-password");advanceUntilIdle()
            assertNotNull(model.user);assertNull(model.guestMigration)
            assertTrue(paths.none { it.second.contains("guest-migration") });assertContentEquals(original,disk.bytes)
            model.reviewGuestMigration();advanceUntilIdle();assertTrue(requireNotNull(model.guestMigration).prepared)
            assertTrue(paths.filter { it.second.contains("guest-migration") }.all { it.first==HttpMethod.Get })
            model.closeGuestMigration();assertNull(model.guestMigration);assertContentEquals(original,disk.bytes);assertEquals(1,files.saved.size)
        } finally { ViewModelStore().apply { put("test",model);clear() };Dispatchers.resetMain() }
    }
}
