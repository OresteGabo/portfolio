package dev.orestegabo.portfolio.investments

import kotlin.test.*

class BondIssuanceTest {
    private fun issue() = JsonRecord().put("bondName", "FXD5/2026/20YR").put("isin", "RWBO20Y000686/MN")
        .put("issuer", "Government of Rwanda").put("currency", "RWF").put("issuanceDate", "2026-10-21")
        .put("settlementDate", "2026-10-23").put("maturityDate", "2046-09-28").put("firstCouponDate", "2027-04-23")
        .put("couponDates", JsonList(listOf("2027-04-23", "2046-09-28"))).put("tenorYears", 20)
        .put("pricePercent", 100).put("couponRate", .1315).put("yieldRate", .1315)
        .put("amountOffered", 10_000_000_000.0).put("appliedAmount", 45_106_600_000.0)
        .put("amountSold", 10_000_000_000.0).put("bidCount", 101)
    @Test fun publicIssuanceCannotBecomeAPurchase() {
        val review = JsonRecord().put("kind", "bond-issuance").put("fields", issue()).put("missingFields", JsonList())
        assertEquals("RWBO20Y000686/MN", importedIssuanceInput(review).text("isin"))
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(review) }
        assertFailsWith<IllegalArgumentException> { importedIssuanceInput(JsonRecord(review.toString()).put("fields", issue().put("faceValue", 10_000_000_000.0))) }
    }
    private fun personal() = JsonRecord().put("kind", "bond").put("warnings", JsonList())
        .put("missingFields", JsonList(listOf("Tenor", "Maturity date", "Coupon rate", "Coupon schedule")))
        .put("fields", JsonRecord().put("issuer", "Government of Rwanda").put("currency", "RWF")
            .put("bondName", "FXD5/2026/20YR").put("isin", "RWBO20Y000686/MN")
            .put("settlementDate", "2026-10-23").put("purchaseDate", "2026-10-23")
            .put("faceValue", 900_000).put("pricePercent", 101.5))
    private fun document() = JsonRecord().put("id", "shared").put("category", "Bond issuance").put("issuance", issue())
    @Test fun personalAllocationUsesSavedPublicTermsButKeepsActualQuantityAndPrice() {
        val review = personal()
        val supplemented = purchaseReviewWithPublishedTerms(review, listOf(document()))
        val body = importedPurchaseInput(supplemented)
        assertEquals(.1315, body.optDouble("couponRate"))
        assertEquals("2027-04-23", body.text("firstCouponDate"))
        assertEquals(issue().getJSONArray("couponDates").toString(), body.getJSONArray("couponDates").toString())
        assertEquals(900_000.0, body.optDouble("faceValue"))
        assertEquals(101.5, body.optDouble("pricePercent"))
        assertFalse(review.getJSONObject("fields").has("couponRate"))
        val pending = pendingBondInput(JsonRecord().put("title", "October bond").put("tenorYears", 20)
            .put("settlementDate", "2026-10-23").put("maturityDate", "2046-09-28"), 1_000_000.0, kotlinx.datetime.LocalDate.parse("2026-10-22"))
        assertEquals(11_000.0, completePendingPurchase(supplemented, pending).optDouble("feesPaid"))
    }
    @Test fun missingPersonalAllocationCannotBeReplacedWithAuctionTotals() {
        val review = personal().put("fields", JsonRecord().put("issuer", "Government of Rwanda").put("currency", "RWF")
            .put("isin", "RWBO20Y000686/MN").put("settlementDate", "2026-10-23"))
            .put("missingFields", JsonList(listOf("Your allocated principal", "Executed price", "Coupon rate", "Coupon schedule")))
        val supplemented = purchaseReviewWithPublishedTerms(review, listOf(document()))
        assertFalse(supplemented.getJSONObject("fields").has("faceValue"))
        assertFalse(supplemented.getJSONObject("fields").has("pricePercent"))
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(supplemented) }
        assertSame(review, purchaseReviewWithPublishedTerms(review, emptyList()))
    }
    @Test fun sharedTermsNeedOneExactSecurityAndSettlementMatchAndNeverOverwriteReadValues() {
        val review = personal()
        for (key in listOf("isin", "settlementDate")) {
            val wrong = document().put("issuance", issue().put(key, if (key == "isin") "RWOTHER000001/MN" else "2026-11-23"))
            assertSame(review, purchaseReviewWithPublishedTerms(review, listOf(wrong)))
        }
        assertSame(review, purchaseReviewWithPublishedTerms(review, listOf(document(), document().put("id", "another"))))
        val supplied = personal().apply { getJSONObject("fields").let { fields -> put("fields", fields.put("couponRate", .12)) } }
        assertEquals(.12, purchaseReviewWithPublishedTerms(supplied, listOf(document())).getJSONObject("fields").optDouble("couponRate"))
    }
    @Test fun actualTermsOverlayCalendarOnceAndPreserveSubscriptionDates() {
        val published = JsonRecord().put("id", "october").put("title", "New 20-year bond")
            .put("settlementDate", "2026-10-23").put("maturityDate", "2046-09-28").put("openBookDate", "2026-10-19")
        val document = JsonRecord().put("id", "report").put("category", "Bond issuance").put("issuance", issue())
        val calendar = mergeBondIssuances(listOf(published), listOf(document, document))
        assertEquals(1, calendar.size)
        assertEquals("october", calendar.single().text("id"))
        assertEquals("2026-10-19", calendar.single().text("openBookDate"))
        val pending = pendingBondInput(calendar.single(), 100_000.0, kotlinx.datetime.LocalDate.parse("2026-10-22"))
        assertEquals("RWBO20Y000686/MN", pending.text("isin"))
        assertEquals("submitted", pending.text("status"))
        assertEquals(100_000.0, pending.optDouble("faceValue"))
        assertEquals(0.0, pending.optDouble("couponRate"))
        assertTrue(mergeBondIssuances(listOf(published), emptyList()).single().text("isin").isBlank())
    }
}
