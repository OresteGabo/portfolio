package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvestmentGoalTest {
    private val today = LocalDate(2026, 11, 21)
    @Test fun oldTargetsStayMonthlyAndYearlyAmountsAreSeparateBoundedAndRoundTrip() {
        val old = investmentGoalFromRecord(JsonRecord().put("monthlyTarget", 300000), InvestmentGoal(monthlyAmount = 300000))
        assertEquals(InvestmentGoalPeriod.MONTHLY, old.period); assertEquals(3600000L, old.yearlyAmount)
        val yearly = old.copy(yearlyAmount = 5000000, period = InvestmentGoalPeriod.YEARLY)
        assertEquals(yearly, investmentGoalFromRecord(JsonRecord(yearly.toRecord().toString())))
        assertEquals(300000L, yearly.copy(period = InvestmentGoalPeriod.MONTHLY).amount)
        assertEquals(5000000.0, yearly.annualReference)
        assertEquals(1000000000000L, InvestmentGoal(monthlyAmount = 1000000000000L).yearlyAmount)
        assertFailsWith<IllegalArgumentException> { yearly.copy(yearlyAmount = 0) }
    }
    @Test fun calendarYearUsesAllMonthlyAggregatesAndPendingOrdersExactlyOnce() {
        val snapshot = PortfolioSnapshot(recentPurchaseIds = emptySet(), recentHoldingIds = emptySet(),
            monthlyInvestmentTotals = listOf(JsonRecord().put("month", "2026-01").put("confirmed", 100000).put("pending", 10000),
                JsonRecord().put("month", "2026-11").put("confirmed", 200000).put("pending", 20000),
                JsonRecord().put("month", "2025-12").put("confirmed", 900000)),
            shareOrders = listOf(JsonRecord().put("status", "pending").put("currency", "RWF").put("requestDate", "2026-06-01").put("estimatedTotal", 50000)))
        val p = assertNotNull(yearlyInvestmentProgress(snapshot, 2026, 1000000, today))
        assertEquals(300000.0, p.confirmed); assertEquals(80000.0, p.pending)
        assertEquals(620000.0, p.remaining); assertEquals(.38f, p.fraction)
        assertNull(yearlyInvestmentProgress(snapshot, 2025, 1000000, today))
        assertNull(yearlyInvestmentProgress(PortfolioSnapshot(recentPurchaseIds = emptySet()), 2026, 1000000, today))
    }
    @Test fun januaryResetsTheYearWithoutCountingForeignFutureOrUnconfirmedDataTwice() {
        fun share(day: String, currency: String = "RWF") = JsonRecord().put("status", "active").put("currency", currency).put("tradeDate", day).put("netAmountPayable", 10000)
        val full = PortfolioSnapshot(holdings = listOf(share("2025-12-31"), share("2026-01-01"), share("2026-11-22"), share("2026-03-01", "USD")))
        assertEquals(10000.0, yearlyInvestmentProgress(full, 2026, 1000000, today)?.invested)
        assertEquals(0.0, yearlyInvestmentProgress(full, 2027, 1000000, LocalDate(2027, 1, 1))?.invested)
    }
}
