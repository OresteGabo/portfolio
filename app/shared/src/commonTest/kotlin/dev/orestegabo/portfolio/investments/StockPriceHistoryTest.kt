package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class StockPriceHistoryTest {
    private val today = LocalDate(2026, 10, 24)
    private fun point(date: String, price: Double, ticker: String = "IMR") = JsonRecord()
        .put("ticker", ticker).put("date", date).put("closing", price).put("price", price)
    private fun history(closes: List<JsonRecord> = emptyList(), trades: List<JsonRecord> = emptyList()) = JsonRecord()
        .put("observations", JsonList(closes)).put("tradeObservations", JsonList(trades))
    private fun quote(stale: Boolean = false) = JsonRecord()
        .put("source", JsonRecord().put("publishedAt", "2026-10-23T12:00:00Z").put("stale", stale))
        .put("quotes", JsonList(listOf(JsonRecord().put("ticker", "IMR").put("closing", 115))))

    @Test fun imrContractAt90AndLatestCloseAt115ProduceRealDatedMovement() {
        val result = stockPriceHistory(history(trades = listOf(point("2026-07-22", 90.0))), quote(), "IMR", "year", today)
        assertEquals(listOf(90.0, 115.0), result.map { it.price })
        assertEquals(listOf(LocalDate(2026, 7, 22), LocalDate(2026, 10, 23)), result.map { it.date })
        assertTrue(result.first().contract)
        assertFalse(result.last().contract)
        assertEquals("Contract trade", result.first().note)
        // No fabricated January or today's point, and July isn't part of a one-month view.
        assertEquals(1, stockPriceHistory(history(trades = listOf(point("2026-07-22", 90.0))), quote(), "IMR", "month", today).size)
    }
    @Test fun officialClosesWinOverExecutionsWithoutChangingQuoteObjects() {
        val trades = point("2026-10-23", 90.0).put("low", 80).put("high", 100)
        val quotes = quote()
        val result = stockPriceHistory(history(listOf(point("2026-10-23", 110.0)), listOf(trades)), quotes, "IMR", "all", today)
        assertEquals(115.0, result.single().price)
        assertFalse(result.single().contract)
        assertEquals(115.0, quotes.rows("quotes").single().optDouble("closing"))
        val stored = stockPriceHistory(history(listOf(point("2026-10-23", 110.0)), listOf(trades)), quote(true), "IMR", "all", today)
        assertEquals(110.0, stored.single().price)
    }
    @Test fun invalidForeignFutureAndStalePointsCannotInventHistory() {
        val data = history(trades = listOf(point("2026-02-30", 90.0), point("2027-01-01", 90.0),
            point("2026-07-22", -1.0), point("2026-07-22", 90.0, "BOK")))
        assertTrue(stockPriceHistory(data, quote(true), "IMR", "year", today).isEmpty())
        assertTrue(stockPriceHistory(null, null, "IMR", "year", today).isEmpty())
        val missingPrice = quote().put("quotes", JsonList(listOf(JsonRecord().put("ticker", "IMR"))))
        assertTrue(stockPriceHistory(null, missingPrice, "IMR", "year", today).isEmpty())
    }
    @Test fun differingDailyContractPricesKeepTheRangeVisible() {
        val data = history(trades = listOf(point("2026-07-22", 90.0).put("low", 90).put("high", 95)))
        val result = stockPriceHistory(data, null, "IMR", "all", today).single()
        assertTrue(result.note.contains("median"))
        assertTrue(result.note.contains("95"))
    }
    @Test fun yearAxisLabelsEveryMonthEvenWhenThereAreOnlyTwoPriceRecords() {
        val range = yearlyPriceChartRange(today)
        val ticks = chartMonthTicks(range)
        assertEquals(LocalDate(2025, 10, 24), range.start)
        assertEquals(today, range.endInclusive)
        assertEquals(listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct"), ticks.map { it.second })
        assertEquals(LocalDate(2026, 1, 1), ticks[2].first)
        assertTrue(ticks.all { it.first in range })
        assertTrue(ticks.zipWithNext().all { (a, b) -> a.first < b.first })
    }

    @Test fun yearAxisHandlesLeapDayAndIncludesAnExactMonthBoundary() {
        val leap = yearlyPriceChartRange(LocalDate(2024, 2, 29))
        assertEquals(LocalDate(2023, 2, 28), leap.start)
        assertEquals(12, chartMonthTicks(leap).size)
        val boundary = chartMonthTicks(yearlyPriceChartRange(LocalDate(2026, 1, 1)))
        assertEquals(LocalDate(2025, 1, 1), boundary.first().first)
        assertEquals(LocalDate(2026, 1, 1), boundary.last().first)
    }

}
