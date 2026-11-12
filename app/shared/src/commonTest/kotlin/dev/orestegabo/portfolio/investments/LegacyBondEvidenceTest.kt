package dev.orestegabo.portfolio.investments

import kotlin.test.*

class LegacyBondEvidenceTest {
    private fun review() = JsonRecord("""{"kind":"bond","missingFields":[],"warnings":[],"fields":{"bondName":"FXDTEST/2028/2YR","isin":"RWTEST000001","issuer":"Government of Rwanda","currency":"RWF","tenorYears":2,"faceValue":1000000,"pricePercent":100,"couponRate":0.12,"purchaseDate":"2028-02-29","settlementDate":"2028-02-29","maturityDate":"2030-02-28","firstCouponDate":"2028-08-29","couponDates":["2028-08-29","2029-02-28","2029-08-29","2030-02-28"],"couponFrequency":2,"scheduleConfidence":"estimated","broker":"Test broker"}}""")
    private fun saved() = importedPurchaseInput(review()).put("id", "existing")
        .put("feesPaid", 11000).put("accruedInterestPaid", 100).put("withholdingTaxRate", 0.15)
        .put("scheduleConfidence", "confirmed")

    @Test fun legacyEvidenceKeepsUnreadCostsAndUsesServerCheckedPostRatherThanEditingPurchase() {
        val original = saved()
        val state = PurchaseReviewState(review(), emptyList(), listOf(original), true, null, null)
        assertTrue(state.ready)
        assertNull(state.selected) // Existing financial data must not be updated by a PUT.
        assertEquals("existing", state.legacyEvidenceTarget?.text("id"))
        val input = assertNotNull(state.confirmed)
        assertEquals(11000.0, input.optDouble("feesPaid"))
        assertEquals(100.0, input.optDouble("accruedInterestPaid"))
        assertEquals(0.15, input.optDouble("withholdingTaxRate"))
        assertFalse(review().getJSONObject("fields").has("feesPaid"))
        assertEquals("confirmed", original.text("scheduleConfidence"))
    }
    @Test fun explicitPdfCostsAreNotSilentlyReplacedBySavedCosts() {
        val source = review()
        source.put("fields", source.getJSONObject("fields").put("feesPaid", 0).put("accruedInterestPaid", 0).put("withholdingTaxRate", 0.05))
        val state = PurchaseReviewState(source, emptyList(), listOf(saved()), true, null, null)
        val input = assertNotNull(state.confirmed)
        assertEquals(0.0, input.optDouble("feesPaid"))
        assertEquals(0.0, input.optDouble("accruedInterestPaid"))
        assertEquals(0.05, input.optDouble("withholdingTaxRate"))
    }
    @Test fun differentIdentityAmountsAmbiguousOrAlreadyLinkedPurchasesCannotSupplyCosts() {
        val input = importedPurchaseInput(review())
        for (other in listOf(saved().put("isin", "OTHER"), saved().put("faceValue", 2000000),
            saved().put("pricePercent", 99), saved().put("couponRate", 0.13),
            saved().put("confirmationDocumentId", "owned-proof"), saved().put("status", "sold"))) {
            assertNull(legacyBondEvidenceTarget(input, listOf(other)))
        }
        assertNull(legacyBondEvidenceTarget(input, listOf(saved(), saved().put("id", "second"))))
        assertNull(PurchaseReviewState(review(), emptyList(), listOf(saved()), false, null, null).legacyEvidenceTarget)
    }
}
