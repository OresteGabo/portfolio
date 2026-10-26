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
