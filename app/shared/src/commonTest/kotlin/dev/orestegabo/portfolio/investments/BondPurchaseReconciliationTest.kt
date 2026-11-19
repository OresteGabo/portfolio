package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioDataApi
import dev.orestegabo.portfolio.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BondPurchaseReconciliationTest {
    private fun pending() = JsonRecord().put("id", "pending-bond").put("status", "submitted").put("instrumentType", "treasury")
        .put("issuer", "Government of Rwanda").put("currency", "RWF").put("market", "primary").put("bondName", "Synthetic bond")
        .put("isin", "RWTEST123456/MN").put("settlementDate", "2026-10-23").put("maturityDate", "2031-10-23")
        .put("faceValue", 2000000).put("feesPaid", 11000).put("pricePercent", 100)
    private fun purchase() = JsonRecord(pending().toString()).put("id", "confirmed-bond").put("status", "active")
        .put("isin", "RWTEST123456").put("faceValue", 1000000).put("feesPaid", 23000).put("pricePercent", 99)
        .put("confirmationDocumentId", "saved-confirmation").put("purchaseDate", "2026-10-22")
    private class Api(var rows: List<JsonRecord>): PortfolioDataApi {
        val writes=mutableListOf<JsonRecord>()
        var cancel=false
        var unavailable=false
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("No PDF transfer")
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            if(cancel)throw CancellationException("cancelled")
            if(method=="GET") { check(path=="/api/bonds/purchases"); return JsonRecord().put("purchases", JsonList(rows)) }
            check(method=="POST" && path=="/api/bonds/purchases/pending-bond/link-imported")
            if(unavailable)throw ApiException(404,"Not found")
            writes+=requireNotNull(body)
            return JsonRecord().put("purchase",rows.single { it.text("id")==body.text("purchaseId") })
        }
    }
    @Test fun sendsOneAtomicLinkWithUnchangedSnapshotsAndNoNewPurchaseOrPdf() = runTest {
        val pending=pending();val purchase=purchase();val api=Api(listOf(pending,purchase))
        val before=purchase.toString()
        val result=linkImportedBondPurchase(api,pending,purchase)
        assertEquals("confirmed-bond",result.getJSONObject("purchase").text("id"))
        assertEquals(before,api.writes.single().getJSONObject("purchase").toString())
        assertEquals(pending.toString(),api.writes.single().getJSONObject("pending").toString())
        assertEquals(before,purchase.toString())
    }
    @Test fun rejectsOtherSecuritiesDatesOverAllocationAndMissingPersonalEvidence() {
        val invalid=listOf(purchase().put("isin","RWOTHER12345"),purchase().put("settlementDate","2026-10-24"),
            purchase().put("maturityDate","2032-10-23"),purchase().put("faceValue",3000000),purchase().put("confirmationDocumentId",""),
            purchase().put("status","submitted"),purchase().put("market","secondary"),purchase().put("currency","USD"))
        assertTrue(importedBondPurchasesForPending(pending(),invalid).isEmpty())
        assertEquals(1,importedBondPurchasesForPending(pending(),listOf(purchase())).size)
    }
    @Test fun changedRecordsAndMissingPendingCannotWrite() = runTest {
        val expected=pending();val selected=purchase()
        for(rows in listOf(listOf(selected),listOf(pending().put("faceValue",3000000),selected),
            listOf(expected,purchase().put("feesPaid",24000)),listOf(expected,purchase().put("confirmationDocumentId","changed")))) {
            val api=Api(rows)
            assertNotNull(runCatching { linkImportedBondPurchase(api,expected,selected) }.exceptionOrNull())
            assertTrue(api.writes.isEmpty())
        }
    }
    @Test fun unsupportedBackendHasAnExplicitErrorAndCancellationPropagates() = runTest {
        val api=Api(listOf(pending(),purchase())).apply { unavailable=true }
        val failure=assertFailsWith<IllegalStateException> { linkImportedBondPurchase(api,pending(),purchase()) }
        assertEquals("Linking imported bonds is not available on this server yet.",failure.message)
        api.cancel=true
        assertFailsWith<CancellationException> { linkImportedBondPurchase(api,pending(),purchase()) }
        assertTrue(api.writes.isEmpty())
    }
}
