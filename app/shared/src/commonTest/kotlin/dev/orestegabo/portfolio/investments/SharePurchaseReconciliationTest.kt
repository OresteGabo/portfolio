package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SharePurchaseReconciliationTest {
    private fun order() = JsonRecord().put("id", "order").put("version", 3).put("status", "pending")
        .put("ticker", "BOK").put("currency", "RWF").put("shares", 1000).put("pricePerShare", 720).put("requestDate", "2026-10-31")
    private fun purchase() = JsonRecord().put("id", "existing-purchase").put("status", "active")
        .put("ticker", "BOK").put("currency", "RWF").put("shares", 1000).put("pricePerShare", 721)
        .put("tradeDate", "2026-11-02").put("settlementDate", "2026-11-04").put("securityName", "Synthetic company")
        .put("broker", "Synthetic broker").put("contractNote", "TEST-001").put("contractDocumentId", "existing-pdf")
        .put("netAmountPayable", 733329.1).put("totalCharges", 12329.1).put("grossConsideration", 721000)
    private class Api(var orders: List<JsonRecord>, var purchases: List<JsonRecord>) : PortfolioDataApi {
        val writes = mutableListOf<Pair<String, JsonRecord>>()
        var fail = false
        var cancel = false
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("No PDF download/upload needed")
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            if (cancel) throw CancellationException("cancelled")
            if (method == "GET") return when (path) {
                "/api/bonds/share-orders" -> JsonRecord().put("orders", JsonList(orders))
                "/api/bonds/equities" -> JsonRecord().put("holdings", JsonList(purchases))
                else -> error("Unexpected read: $path")
            }
            check(path == "/api/bonds/share-orders/order/confirm" && method == "POST")
            writes += path to requireNotNull(body)
            if (fail) error("Temporary failure")
            return JsonRecord().put("holding", purchases.single())
        }
    }
    @Test fun explicitLinkUsesExistingContractAndActualCostsEvenAcrossMonthsAndPriceChanges() = runTest {
        val order = order(); val purchase = purchase(); val before = purchase.toString()
        val api = Api(listOf(order), listOf(purchase))
        val result = linkImportedSharePurchase(api, order, purchase)
        assertEquals("existing-purchase", result.getJSONObject("holding").text("id"))
        val payload = api.writes.single().second
        assertTrue(payload.optBoolean("confirmed")); assertEquals(3, payload.optInt("version"))
        assertEquals(before, payload.getJSONObject("purchase").toString())
        assertEquals(before, purchase.toString())
    }
    @Test fun excludesMissingEvidenceAggregatesWrongSecuritiesCurrenciesAndQuantities() {
        val rows = listOf(purchase().put("contractNote", ""), purchase().put("contractDocumentId", ""),
            purchase().put("id", "aggregate:RWF:BOK"), purchase().put("shares", 500), purchase().put("ticker", "IMR"),
            purchase().put("currency", "USD"), purchase().put("status", "pending"), purchase().put("tradeDate", "invalid"))
        assertTrue(importedSharePurchasesForOrder(order(), rows).isEmpty())
        assertEquals(1, importedSharePurchasesForOrder(order(), listOf(purchase())).size)
    }
    @Test fun staleMissingOrChangedRecordsCannotWrite() = runTest {
        val selected = purchase(); val expected = order()
        for (api in listOf(Api(emptyList(), listOf(selected)), Api(listOf(order().put("version", 4)), listOf(selected)),
            Api(listOf(expected), emptyList()), Api(listOf(expected), listOf(purchase().put("netAmountPayable", 1))),
            Api(listOf(expected), listOf(purchase().put("contractDocumentId", "changed"))))) {
            val failure = try { linkImportedSharePurchase(api, expected, selected); null } catch (failure: Exception) { failure }
            assertTrue(failure is IllegalArgumentException || failure is IllegalStateException)
            assertTrue(api.writes.isEmpty())
        }
    }
    @Test fun ambiguousExistingContractRecordsAreNeverLinked() = runTest {
        val api = Api(listOf(order()), listOf(purchase(), purchase().put("id", "duplicate")))
        assertFailsWith<IllegalArgumentException> { linkImportedSharePurchase(api, order(), purchase()) }
        assertTrue(api.writes.isEmpty())
    }
    @Test fun networkFailureAndCancellationPropagate() = runTest {
        val failing = Api(listOf(order()), listOf(purchase())).apply { fail = true }
        assertFailsWith<IllegalStateException> { linkImportedSharePurchase(failing, order(), purchase()) }
        val cancelled = Api(listOf(order()), listOf(purchase())).apply { cancel = true }
        assertFailsWith<CancellationException> { linkImportedSharePurchase(cancelled, order(), purchase()) }
        assertTrue(cancelled.writes.isEmpty())
    }
}
