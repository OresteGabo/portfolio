package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class TreasuryPrincipalCallSiteTest {
    private val recorded = LocalDate(2026, 10, 22)
    private val accepted = listOf(100_000.0, 200_000.0, 10_000_000_100_000.0)
    private val rejected = listOf(-100_000.0, -0.0, 0.0, 99_999.99, 100_000.004, 150_000.0)
    private fun calendarIssue() = JsonRecord().put("title", "Synthetic treasury").put("tenorYears", 10)
        .put("settlementDate", "2026-10-23").put("maturityDate", "2036-10-23")
    private fun personal(amount: Any) = JsonRecord().put("kind", "bond").put("missingFields", JsonList()).put("warnings", JsonList())
        .put("fields", guestBondFixture().put("issuer", "Government of Rwanda").put("faceValue", amount).put("tenorYears", 7).put("firstCouponDate", "2027-01-15"))
    private fun issuance() = JsonRecord().put("kind", "bond-issuance").put("missingFields", JsonList()).put("fields",
        JsonRecord().put("bondName", "Synthetic treasury").put("isin", "RWTEST000001/MN")
            .put("issuer", "Government of Rwanda").put("issuanceDate", "2026-10-21")
            .put("settlementDate", "2026-10-23").put("maturityDate", "2036-10-23").put("firstCouponDate", "2027-04-23")
            .put("couponDates", JsonList(listOf("2027-04-23", "2036-10-23"))).put("tenorYears", 10)
            .put("pricePercent", 100).put("couponRate", .1).put("yieldRate", .1)
            .put("amountOffered", 100_000_000).put("appliedAmount", 200_000_000)
            .put("amountSold", 100_000_000).put("bidCount", 10))
    private fun pending(amount: Any) = JsonRecord().put("status", "submitted").put("currency", "RWF")
        .put("issuer", "Government of Rwanda").put("market", "primary").put("settlementDate", "2026-10-23")
        .put("maturityDate", "2036-10-23").put("tenorYears", 10).put("faceValue", amount).put("feesPaid", 11_000)

    @Test fun pendingRequestKeepsItsIncrementRuleAndDoesNotAcquireTheBidScreensUpperLimit() {
        for (amount in accepted) assertEquals(amount, pendingBondInput(calendarIssue(), amount, recorded).optDouble("faceValue"))
        for (amount in rejected + listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals("Use an amount in RWF 100,000 increments.", assertFailsWith<IllegalArgumentException> {
                pendingBondInput(calendarIssue(), amount, recorded)
            }.message)
        }
    }

    @Test fun bidEligibilityKeepsItsSeparateCapAndOriginalStringParsing() {
        assertEquals(100_000L, ApplicationAmountStep)
        assertEquals(100_000.0, applicationBidAmount("100000"))
        assertEquals(100_000.0, applicationBidAmount("1e5"))
        assertEquals(10_000_000_000_000.0, applicationBidAmount(MaximumApplicationAmount.toString()))
        for (value in listOf("", "100,000", "100 000", "150000", "100000.004", "NaN", "Infinity", "10000000100000")) {
            assertNull(applicationBidAmount(value), value)
        }
    }

    @Test fun personalPdfValidationPreservesItsMessagePayloadAndInputOwnership() {
        for (amount in accepted) {
            val review = personal(amount); val original = review.toString()
            val input = importedPurchaseInput(review)
            assertEquals(amount, input.optDouble("faceValue")); assertEquals("active", input.text("status"))
            assertEquals(original, review.toString())
        }
        for (amount in rejected + listOf<Any>("NaN", "Infinity", "not a number")) {
            assertEquals("Some figures could not be read. Try a clearer PDF.", assertFailsWith<IllegalArgumentException> {
                importedPurchaseInput(personal(amount))
            }.message)
        }
    }

    @Test fun publicConfirmationKeepsTheSamePrincipalRuleWithoutChangingCostOrStoredInput() {
        for (amount in accepted) {
            val recorded = pending(amount); val original = recorded.toString()
            val input = pendingIssuanceConfirmation(issuance(), recorded)
            assertEquals(amount, input.optDouble("faceValue"))
            assertEquals(amount + 11_000, input.optDouble("amountInvested"))
            assertEquals(original, recorded.toString())
        }
        for (amount in rejected + listOf<Any>("NaN", "Infinity", "not a number")) {
            assertEquals("Use your personal PDF to confirm the final allocation and price.", assertFailsWith<IllegalArgumentException> {
                pendingIssuanceConfirmation(issuance(), pending(amount))
            }.message)
        }
    }
}
