package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Synthetic precision fixtures; no account records or assumed settlement conventions. */
class FinancialPrecisionAuditTest {
    private val today = LocalDate(2026, 11, 15)
    private fun lot() = JsonRecord().put("id", "lot").put("status", "active").put("ticker", "BOK")
        .put("currency", "RWF").put("tradeDate", "2026-01-01").put("shares", 1).put("netAmountPayable", .3)
    private fun sale(tax: Double = .2) = JsonRecord().put("kind", "sale").put("holdingId", "lot")
        .put("currency", "RWF").put("date", today.toString()).put("shares", 1).put("price", .3).put("fees", .1).put("tax", tax)

    @Test fun halfUpBoundariesIncludeNegativeAndSubCentValues() {
        for ((input, expected) in listOf(.004 to 0.0, .005 to .01, .006 to .01, 1.005 to 1.01, 2.675 to 2.68,
            -.004 to 0.0, -.005 to -.01, -1.005 to -1.01, -2.675 to -2.68, 0.0 to 0.0)) {
            assertEquals(expected, roundedMoney(input))
        }
        assertEquals(10_000_000_000_000.01, roundedMoney(10_000_000_000_000.01))
    }

    @Test fun repeatedSumsRoundOnceAndDoNotLoseSmallOffsetsToLargeAmounts() {
        assertEquals(100.0, sumMoney(List(10_000) { .01 }))
        assertEquals(.01, sumMoney(listOf(10_000_000_000_000.0, .01, -10_000_000_000_000.0)))
        assertEquals(-.01, sumMoney(listOf(-.005, -.005)))
        assertEquals(.01, sumMoney(listOf(.004, .004)))
        assertEquals(0.0, sumMoney(emptyList()))
    }

    @Test fun couponTaxesApplyBeforeRoundingGrossAndAllowZeroAndFullWithholding() {
        val terms = BondTerms(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1), 1000.0, .115, .05, 2)
        assertEquals(57.5, terms.grossCouponPayment())
        assertEquals(54.63, terms.netCouponPayment())
        assertEquals(57.5, terms.copy(taxRate = 0.0).netCouponPayment())
        assertEquals(0.0, terms.copy(taxRate = 1.0).netCouponPayment())
        assertEquals(0.0, terms.copy(faceValue = 0.0).netCouponPayment())
        assertEquals(0.0, terms.copy(faceValue = .01, couponRate = 1.0).netCouponPayment())
        assertEquals(109.26, trackBond(terms, terms.maturityDate).scheduledCouponsToDate)
    }

    @Test fun proportionalCostUsesDecimalDivisionAtCentBoundaries() {
        assertEquals(.17, proportionalCost(1.0, 1.0, 6.0))
        assertEquals(0.0, proportionalCost(.01, 1.0, 3.0))
        assertEquals(41_152_263.04, proportionalCost(123_456_789.12, 1.0, 3.0))
    }

    @Test fun recordedAndEstimatedCostsHaveDistinctPrecisionRules() {
        assertEquals(ShareAcquisitionCost(1.5, .01, 1.51), shareAcquisitionCost(100.0, .015, listOf(.004, .004)))
        assertFailsWith<IllegalArgumentException> { shareAcquisitionCost(.5, 100.0, emptyList()) }
        assertFailsWith<IllegalArgumentException> { shareOrderEstimate(100.0, .015) }
        assertEquals(ShareOrderEstimate(720_000.0, 12_312.0, 732_312.0), shareOrderEstimate(1000.0, 720.0))
        assertEquals(ShareOrderEstimate(10_000_000_000_000.0, 171_000_000_000.0, 10_171_000_000_000.0),
            shareOrderEstimate(1_000_000_000.0, 10_000.0))
    }

    @Test fun saleChargesEqualToProceedsAreAcceptedButUnroundedExcessIsRejected() {
        // Decimal .1 + .2 equals gross .3; no rounded/tolerant comparison is used.
        val entry = validateLedgerEntry(sale(), listOf(lot()), emptyList(), today)
        assertEquals(0.0, entry.getDouble("amount"))
        assertEquals(-.3, entry.getDouble("realizedGain"))
        assertFailsWith<IllegalArgumentException> { validateLedgerEntry(sale(.200000001), listOf(lot()), emptyList(), today) }
    }

    @Test fun chargeComparisonIsExactForSubCentAndExtremeFiniteInputs() {
        assertTrue(saleChargesWithinProceeds(.1, .2, .3))
        assertTrue(saleChargesWithinProceeds(0.0, 0.0, 0.0))
        assertFalse(saleChargesWithinProceeds(.1, .200000001, .3))
        assertFalse(saleChargesWithinProceeds(.004, .004, .007))
        assertFalse(saleChargesWithinProceeds(.3, 1e-300, .3))
        assertFalse(saleChargesWithinProceeds(1e300, 1e300, .3))
        assertTrue(saleChargesWithinProceeds(1e-300, 1e-300, .3))
        assertTrue(saleChargesWithinProceeds(1e-300, 1e-300, 2e-300))
        assertFalse(saleChargesWithinProceeds(1e300, 1e300, 1e300))
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.01)) {
            assertFailsWith<IllegalArgumentException> { saleChargesWithinProceeds(invalid, 0.0, 1.0) }
            assertFailsWith<IllegalArgumentException> { saleChargesWithinProceeds(0.0, invalid, 1.0) }
            assertFailsWith<IllegalArgumentException> { saleChargesWithinProceeds(0.0, 0.0, invalid) }
        }
    }

    @Test fun largeRawJsonRetainsDigitsButDoubleAccessCannotRecoverLostCents() {
        val record = JsonRecord("{\"amount\":1000000000000000.01}")
        assertEquals("1000000000000000.01", record.text("amount"))
        assertEquals(1_000_000_000_000_000.0, record.getDouble("amount"))
        assertEquals(record.toString(), JsonRecord(record.toString()).toString())
        assertNotEquals(record.text("amount"), JsonRecord().put("amount", record.getDouble("amount")).text("amount"))
    }

    @Test fun numericJsonRoundTripsPreserveSupportedAmountsWithoutDisplayRounding() {
        for (amount in listOf(0.0, -.01, .005, .1, .3, 732312.0, 10_000_000_000_000.01)) {
            val raw = JsonRecord().put("amount", amount).toString()
            assertEquals(amount, JsonRecord(raw).getDouble("amount"))
            assertEquals(raw, JsonRecord(raw).toString())
        }
        val original = JsonRecord("{\"amount\":0.100000000000000001}")
        assertEquals("0.100000000000000001", original.text("amount"))
        assertEquals(.1, original.getDouble("amount")) // Existing Double getter is a lossy boundary.
        assertEquals(original.toString(), JsonRecord(original.toString()).toString())
    }
}
