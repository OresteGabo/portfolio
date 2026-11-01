package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class InvestmentDetailsTest {
    private val today = LocalDate(2026, 10, 31)
    private fun bond(status: String = "active") = JsonRecord("""{
        "id":"bond-a","currency":"RWF","status":"$status","purchaseDate":"2026-01-01",
        "maturityDate":"2027-10-31","faceValue":2200000,"couponRate":0.115,
        "withholdingTaxRate":0.05,"couponFrequency":2,
        "couponDates":["2026-10-31","2027-04-30","2027-10-31"]
    }""")
    private fun receipt(method: String, id: String = "bond-a", currency: String = "RWF", received: Boolean = true) =
        JsonRecord().put("purchaseId", id).put("currency", currency).put("dueDate", today.toString())
            .put("received", received).put("method", method)

    @Test fun dueTodayRemainsVisibleUntilManuallyConfirmedAndUsesPublishedDates() {
        assertEquals(today, nextDetailCoupon(bond(), emptyList(), today))
        assertEquals(today, nextDetailCoupon(bond(), listOf(receipt("automatic")), today))
        assertEquals(LocalDate(2027, 4, 30), nextDetailCoupon(bond(), listOf(receipt("manual")), today))
        assertEquals(LocalDate(2027, 4, 30), nextDetailCoupon(bond(), emptyList(), LocalDate(2026, 11, 1)))
        assertEquals(126500.0, bond().terms()!!.grossCouponPayment())
        assertEquals(120175.0, bond().terms()!!.netCouponPayment())
    }

    @Test fun anotherBondCurrencyOrNegativeReceiptCannotHideTodaysCoupon() {
        for (receipt in listOf(receipt("manual", id = "bond-b"), receipt("manual", currency = "USD"),
            receipt("manual", received = false))) {
            assertEquals(today, nextDetailCoupon(bond(), listOf(receipt), today))
        }
    }

    @Test fun pendingSoldMaturedAndInvalidTermsNeverInventANextPayment() {
        for (status in listOf("submitted", "sold", "matured")) assertNull(nextDetailCoupon(bond(status), emptyList(), today))
        assertNull(nextDetailCoupon(bond(), emptyList(), LocalDate(2027, 11, 1)))
        assertNull(nextDetailCoupon(bond().put("maturityDate", "invalid"), emptyList(), today))
        assertNull(nextDetailCoupon(bond().put("couponFrequency", 7).put("couponDates", JsonList()), emptyList(), today))
    }

    @Test fun shareHistoryStaysScopedToTickerAndCurrencyWithNewestTradeFirst() {
        fun lot(id: String, ticker: String, currency: String, date: String) = JsonRecord().put("id", id)
            .put("ticker", ticker).put("currency", currency).put("tradeDate", date)
        val selected = lot("a", "IMR", "RWF", "2026-01-01")
        val rows = listOf(selected, lot("b", "IMR", "USD", "2026-10-31"),
            lot("c", "BOK", "RWF", "2026-10-31"), lot("d", "IMR", "RWF", "2026-10-30"))
        assertEquals(listOf("d", "a"), shareDetailLots(rows, selected).map { it.text("id") })
    }
}
