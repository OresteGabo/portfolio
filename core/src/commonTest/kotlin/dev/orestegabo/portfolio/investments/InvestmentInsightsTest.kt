package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvestmentInsightsTest {
    private val today = LocalDate(2026, 10, 30)
    private fun bond(id: String = "bond", face: Double = 1_000_000.0, rate: Double = .12, tax: Double = .05,
        maturity: String = "2027-10-30", dates: List<String> = listOf("2026-10-30", "2027-04-30", "2027-10-30")) =
        JsonRecord().put("id", id).put("bondName", "Synthetic bond").put("currency", "RWF").put("status", "active")
            .put("purchaseDate", "2026-07-01").put("maturityDate", maturity).put("faceValue", face)
            .put("couponRate", rate).put("withholdingTaxRate", tax).put("couponFrequency", 2)
            .put("couponDates", JsonList(dates)).put("scheduleConfidence", "confirmed")
            .put("pricePercent", 100).put("feesPaid", 11000).put("accruedInterestPaid", 0)
    private fun equity(id: String, gross: Double, fees: Double, payable: Double) =
        JsonRecord().put("id", id).put("securityName", "Synthetic shares").put("currency", "RWF").put("status", "active")
            .put("tradeDate", "2026-09-24").put("grossConsideration", gross).put("totalCharges", fees).put("netAmountPayable", payable)

    @Test fun referenceBondsUseExistingGrossNetCalculationsAndKeepCurrenciesSeparate() {
        val seven = bond("seven", 2_200_000.0, .115, maturity = "2033-07-31", dates = listOf("2027-01-31", "2027-07-31"))
        val ten = bond("ten", maturity = "2036-08-31", dates = listOf("2027-02-28", "2027-08-31"))
        val usd = bond("usd", 1000.0, .10).put("currency", "USD")
        val result = PortfolioSnapshot(listOf(seven, ten, usd)).bondCashFlowOutlook(today)
        val january = result.months.single { it.currency == "RWF" && it.month == LocalDate(2027, 1, 1) }
        assertEquals(126500.0, january.grossCoupons); assertEquals(120175.0, january.netCoupons); assertEquals(6325.0, january.withholding)
        val february = result.months.single { it.currency == "RWF" && it.month == LocalDate(2027, 2, 1) }
        assertEquals(60000.0, february.grossCoupons); assertEquals(57000.0, february.netCoupons)
        assertEquals(setOf("RWF", "USD"), result.months.map { it.currency }.toSet()); assertEquals(24, result.months.size)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun currentPartialMonthIsInclusiveAndEndBoundaryExclusive() {
        val p = bond(maturity = "2028-01-01", dates = listOf("2026-10-29", "2026-10-30", "2026-10-31", "2027-09-30", "2027-10-01"))
        val result = PortfolioSnapshot(listOf(p)).bondCashFlowOutlook(today)
        assertEquals(today, result.from); assertEquals(LocalDate(2027, 10, 1), result.untilExclusive)
        assertEquals(12, result.months.size)
        assertEquals(114000.0, result.months.first().netCoupons)
        assertEquals(listOf("2026-10-30", "2026-10-31", "2027-09-30"), result.months.flatMap { it.flows }.map { it.date.toString() })
        assertEquals(0.0, result.months[1].netCoupons) // Empty months remain visible.
    }

    @Test fun maturityPrincipalAndFinalCouponAreDifferentFlowsAndPrincipalIsNotTaxed() {
        val p = bond(maturity = "2027-04-30", dates = listOf("2027-04-30"))
        val month = PortfolioSnapshot(listOf(p)).bondCashFlowOutlook(today).months.single { it.month == LocalDate(2027, 4, 1) }
        assertEquals(57000.0, month.netCoupons); assertEquals(1_000_000.0, month.principal)
        assertEquals(2, month.flows.size); assertEquals(setOf(BondCashFlowKind.COUPON, BondCashFlowKind.PRINCIPAL), month.flows.map { it.kind }.toSet())
    }

    @Test fun allContractualDatesWinWithoutInventingExtraCouponsAndDuplicateDatesDoNotDoubleIncome() {
        val p = bond(maturity = "2027-08-31", dates = listOf("2027-02-19", "2027-02-19"))
        val flows = PortfolioSnapshot(listOf(p)).bondCashFlowOutlook(today).months.flatMap { it.flows }
        assertEquals(listOf(LocalDate(2027, 2, 19)), flows.filter { it.kind == BondCashFlowKind.COUPON }.map { it.date })
        assertTrue(flows.none { it.estimatedDate }); assertEquals(1, flows.count { it.kind == BondCashFlowKind.PRINCIPAL })
    }

    @Test fun estimatedSchedulesRetainMonthEndAndLeapDayAndAreLabeled() {
        val p = bond(maturity = "2028-08-31", dates = emptyList()).put("scheduleConfidence", "estimated")
        val result = PortfolioSnapshot(listOf(p)).bondCashFlowOutlook(LocalDate(2028, 1, 1))
        val coupons = result.months.flatMap { it.flows }.filter { it.kind == BondCashFlowKind.COUPON }
        assertEquals(listOf("2028-02-29", "2028-08-31"), coupons.map { it.date.toString() }); assertTrue(coupons.all { it.estimatedDate })
    }

    @Test fun incompleteCouponTermsPreserveKnownPrincipalWithoutAssumingATaxOrZeroIncome() {
        for (broken in listOf(bond().put("couponRate", "invalid"), bond().put("withholdingTaxRate", null), bond().put("couponFrequency", 0))) {
            val result = PortfolioSnapshot(listOf(broken)).bondCashFlowOutlook(today, 13)
            assertTrue(result.months.flatMap { it.flows }.all { it.kind == BondCashFlowKind.PRINCIPAL })
            assertEquals(InsightIssueReason.INCOMPLETE_COUPON_TERMS, result.issues.single().reason)
            assertEquals(1_000_000.0, result.months.sumOf { it.principal })
        }
    }

    @Test fun irregularFrequencyNeedsExplicitDatesAndZeroCouponPrincipalStillAppears() {
        val irregular = bond(dates = emptyList()).put("couponFrequency", 7)
        val missing = PortfolioSnapshot(listOf(irregular)).bondCashFlowOutlook(today, 13)
        assertEquals(InsightIssueReason.UNAVAILABLE_SCHEDULE, missing.issues.single().reason)
        val explicit = PortfolioSnapshot(listOf(irregular.put("couponDates", JsonList(listOf("2027-01-11"))))).bondCashFlowOutlook(today)
        assertEquals(1, explicit.months.flatMap { it.flows }.size); assertTrue(explicit.issues.isEmpty())
        val zero = PortfolioSnapshot(listOf(bond(rate = 0.0, dates = emptyList()))).bondCashFlowOutlook(today, 13)
        assertEquals(1_000_000.0, zero.months.sumOf { it.principal }); assertTrue(zero.months.all { it.netCoupons == 0.0 })
    }

    @Test fun cashFlowScopeExcludesPendingSoldMaturedFuturePurchasesAndPastMaturities() {
        val rows = listOf(bond("pending").put("status", "submitted"), bond("sold").put("status", "sold"),
            bond("matured").put("status", "matured"), bond("future").put("purchaseDate", "2026-11-01"),
            bond("past", maturity = "2026-10-29"))
        val result = PortfolioSnapshot(rows).bondCashFlowOutlook(today)
        assertTrue(result.months.isEmpty()); assertTrue(result.issues.isEmpty())
    }

    @Test fun invalidRecordsAreReportedAndMissingCurrencyNeverBecomesRwf() {
        for (p in listOf(bond().put("maturityDate", "bad"), bond().put("currency", ""), bond().put("faceValue", -1), bond().put("purchaseDate", "bad"))) {
            val result = PortfolioSnapshot(listOf(p)).bondCashFlowOutlook(today)
            assertTrue(result.months.isEmpty()); assertEquals(InsightIssueReason.INVALID_RECORD, result.issues.single().reason)
        }
        assertFailsWith<IllegalArgumentException> { PortfolioSnapshot().bondCashFlowOutlook(today, 0) }
        assertFailsWith<IllegalArgumentException> { PortfolioSnapshot().bondCashFlowOutlook(today, 121) }
    }

    @Test fun withholdingAndRoundingUseRecordedRatesPerPaymentNotAnAnnualAverage() {
        val p = bond(face = 1_000_017.54, tax = .05)
        val noTax = bond("exempt", face = 2_200_000.0, rate = .115, tax = 0.0)
        val month = PortfolioSnapshot(listOf(p, noTax)).bondCashFlowOutlook(today).months.first()
        assertEquals(183501.0, month.netCoupons) // 57,001 + 126,500
        assertEquals(186501.05, month.grossCoupons)
        assertEquals(3000.05, month.withholding)
    }

    @Test fun purchaseFeeReferencesIncludeChargesOnceAndUseConsiderationAsDenominator() {
        val shares = listOf(equity("imr", 36000.0, 615.60, 36615.60), equity("bok", 335000.0, 5728.50, 340728.50))
        val result = PortfolioSnapshot(holdings = shares).purchaseFeeAnalysis(today)
        val totals = result.currencies.single()
        assertEquals(371000.0, totals.consideration); assertEquals(6344.10, totals.equityFees)
        assertEquals(377344.10, totals.cashCost); assertEquals(6344.10 / 371000.0, totals.feeRate)
        assertTrue(result.issues.isEmpty()); assertEquals(0.0, totals.bondFees)
    }

    @Test fun bondPremiumAccruedInterestAndCommissionAreNotMixed() {
        val p = bond().put("pricePercent", 101).put("accruedInterestPaid", 5000)
        val totals = PortfolioSnapshot(listOf(p)).purchaseFeeAnalysis(today).currencies.single()
        assertEquals(1010000.0, totals.consideration); assertEquals(5000.0, totals.accruedInterest)
        assertEquals(11000.0, totals.bondFees); assertEquals(1026000.0, totals.cashCost)
        assertEquals(11000.0 / 1010000.0, totals.feeRate)
    }

    @Test fun feesRemainHistoricalForSoldAndMaturedButIgnorePendingAndFuture() {
        val bonds = listOf(bond("old").put("status", "matured"), bond("sold").put("status", "sold"),
            bond("pending").put("status", "submitted"), bond("future").put("purchaseDate", "2027-01-01"))
        val shares = listOf(equity("sold-share", 100.0, 1.0, 101.0).put("status", "sold"))
        val totals = PortfolioSnapshot(bonds, shares).purchaseFeeAnalysis(today).currencies.single()
        assertEquals(3, totals.purchases.size); assertEquals(22001.0, totals.fees)
    }

    @Test fun unknownFeesAndInconsistentPayableAreExcludedInsteadOfAssumedZero() {
        val snapshot = PortfolioSnapshot(listOf(bond().put("feesPaid", null)),
            listOf(equity("broken", 100.0, 1.0, 200.0), equity("missing", 100.0, 0.0, 100.0).put("totalCharges", null)))
        val result = snapshot.purchaseFeeAnalysis(today)
        assertTrue(result.currencies.isEmpty()); assertEquals(3, result.issues.size)
        assertTrue(result.issues.all { it.reason == InsightIssueReason.INCOMPLETE_COSTS })
    }

    @Test fun partialOverviewCannotPretendToBeCompleteFeeHistory() {
        assertFailsWith<IllegalArgumentException> { PortfolioSnapshot(listOf(bond()), recentPurchaseIds = emptySet()).purchaseFeeAnalysis(today) }
        val aggregate = equity("aggregate:old", 100.0, 0.0, 100.0)
        assertEquals(1, PortfolioSnapshot(holdings = listOf(aggregate)).purchaseFeeAnalysis(today).issues.size)
    }

    @Test fun feeCurrenciesDecimalCentsAndExplicitZeroChargesArePreservedWithoutFx() {
        val snapshot = PortfolioSnapshot(holdings = listOf(equity("a", .1, .2, .3), equity("b", .2, .1, .3),
            equity("usd", 100.0, 0.0, 100.0).put("currency", "USD")))
        val totals = snapshot.purchaseFeeAnalysis(today).currencies
        assertEquals(listOf("RWF", "USD"), totals.map { it.currency })
        assertEquals(.3, totals[0].fees); assertEquals(.6, totals[0].cashCost); assertEquals(0.0, totals[1].fees)
    }

    @Test fun emptyPortfolioIsValidAndAllCalculationsLeaveRecordsUnchanged() {
        assertTrue(PortfolioSnapshot().purchaseFeeAnalysis(today).currencies.isEmpty())
        assertTrue(PortfolioSnapshot().bondCashFlowOutlook(today).months.isEmpty())
        val p = bond(); val before = p.toString()
        val snapshot = PortfolioSnapshot(listOf(p))
        snapshot.purchaseFeeAnalysis(today); snapshot.bondCashFlowOutlook(today)
        assertEquals(before, p.toString())
    }
}
