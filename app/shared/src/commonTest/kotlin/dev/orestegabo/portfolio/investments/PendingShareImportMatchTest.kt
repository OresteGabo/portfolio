package dev.orestegabo.portfolio.investments

import kotlin.test.*

class PendingShareImportMatchTest {
    private fun review() = JsonRecord().put("kind", "equity").put("missingFields", JsonList()).put("fields", JsonRecord()
        .put("securityName", "Synthetic company").put("ticker", "BOK").put("currency", "RWF")
        .put("shares", 1000).put("pricePerShare", 720).put("grossConsideration", 720000)
        .put("totalCharges", 12312).put("netAmountPayable", 732312)
        .put("tradeDate", "2026-11-18").put("contractNote", "TEST-001").put("broker", "Synthetic broker"))
    private fun order() = JsonRecord().put("id", "order").put("status", "pending").put("ticker", "BOK")
        .put("currency", "RWF").put("shares", 1000).put("pricePerShare", 720).put("requestDate", "2026-11-01")

    @Test fun matchesTheMonthNotTheExactDayAndNeverChangesTheContract() {
        val pdf = review(); val before = pdf.toString()
        assertEquals(1, matchingPendingShareOrders(pdf, listOf(order())).size)
        assertEquals(before, pdf.toString())
        assertFalse(pdf.has("pendingOrderId"))
    }
    @Test fun rejectsDifferentSecuritiesQuantitiesPricesCurrenciesYearsMonthsAndNonPendingOrders() {
        for ((key, value) in listOf("ticker" to "BLR", "shares" to 900, "pricePerShare" to 721,
            "currency" to "USD", "requestDate" to "2025-11-18", "requestDate" to "2026-10-18",
            "requestDate" to "", "status" to "confirmed", "id" to "")) {
            assertTrue(matchingPendingShareOrders(review(), listOf(order().put(key, value))).isEmpty(), "$key=$value")
        }
    }
    @Test fun returnsAllMatchingOrdersForExplicitChoiceRatherThanPickingOne() {
        assertEquals(listOf("first", "second"), matchingPendingShareOrders(review(),
            listOf(order().put("id", "first"), order().put("id", "second"))).map { it.text("id") })
    }
    @Test fun malformedOrOtherDocumentKindsOfferNoMatches() {
        assertTrue(matchingPendingShareOrders(JsonRecord().put("kind", "equity"), listOf(order())).isEmpty())
        assertTrue(matchingPendingShareOrders(review().put("kind", "bond"), listOf(order())).isEmpty())
        val pdf = review(); pdf.put("fields", pdf.getJSONObject("fields").put("tradeDate", "invalid"))
        assertTrue(matchingPendingShareOrders(pdf, listOf(order())).isEmpty())
    }
}
