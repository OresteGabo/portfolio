package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class CouponReceiptPresentationTest {
    private val today = LocalDate(2026, 10, 30)
    private fun receipt(id: String = "bond-1", date: String = "2026-04-23", method: String = "manual", amount: Double = 60000.0, received: Boolean = true, currency: String = "RWF") =
        JsonRecord().put("purchaseId", id).put("dueDate", date).put("method", method).put("amount", amount).put("received", received).put("currency", currency)

    @Test fun dueDatesNeverImplyReceivedAndUnknownTrackingIsNotAnUnreceivedPayment() {
        assertEquals(CouponReceiptState.UNCONFIRMED, couponReceiptState(today, today, true, null))
        assertEquals(CouponReceiptState.UNAVAILABLE, couponReceiptState(today, today, false, null))
        assertEquals(CouponReceiptState.NOT_RECEIVED, couponReceiptState(today, today, true, receipt(received = false)))
        assertEquals(CouponReceiptState.UPCOMING, couponReceiptState(LocalDate(2027, 4, 23), today, true, receipt()))
    }

    @Test fun automaticAndManualAndUnknownEvidenceRemainDistinct() {
        assertEquals(CouponReceiptState.CONFIRMED, couponReceiptState(today, today, true, receipt()))
        assertEquals(CouponReceiptState.AUTOMATIC, couponReceiptState(today, today, true, receipt(method = "automatic")))
        assertEquals(CouponReceiptState.UNKNOWN_METHOD, couponReceiptState(today, today, true, receipt(method = "")))
        assertEquals(CouponReceiptState.UNAVAILABLE, couponReceiptState(today, today, false, receipt()))
    }

    @Test fun summaryUsesRecordedAmountsWithinTheBondCurrencyAndCutoff() {
        val purchase = JsonRecord().put("id", "bond-1").put("currency", "RWF")
        val rows = listOf(receipt(amount = 60.15), receipt(date = "2026-10-23", method = "automatic", amount = 61.25),
            receipt(id = "another-bond", amount = 999.0), receipt(date = "2027-04-23", amount = 999.0),
            receipt(date = "2026-01-23", currency = "USD", amount = 999.0), receipt(date = "bad-date"),
            receipt(date = "2026-02-23", received = false), receipt(date = "2026-03-23", method = "unknown"),
            receipt(date = "2026-05-23", amount = -1.0), receipt(date = "2026-06-23").put("amount", "not-a-number"))
        assertEquals(CouponReceiptTotals(60.15, 61.25), couponReceiptTotals(purchase, rows, today))
        assertEquals(CouponReceiptTotals(0.0, 0.0), couponReceiptTotals(purchase, emptyList(), today))
    }

    @Test fun recordedReceiptAmountIsIndependentOfTheCurrentScheduleEstimate() {
        assertEquals(54321.15, recordedCouponAmount(receipt(amount = 54321.15), "RWF"))
        assertNull(recordedCouponAmount(receipt(received = false), "RWF"))
        assertNull(recordedCouponAmount(receipt(currency = "USD"), "RWF"))
        assertNull(recordedCouponAmount(receipt().put("amount", "not-a-number"), "RWF"))
        assertNull(recordedCouponAmount(null, "RWF"))
    }

    @Test fun displayTotalsKeepCentsAndIgnoreRepeatedDueDates() {
        val purchase = JsonRecord().put("id", "bond-1").put("currency", "RWF")
        val first = receipt(amount = .1)
        val second = receipt(date = "2026-10-23", amount = .2)
        assertEquals(.3, couponReceiptTotals(purchase, listOf(first, second, first), today).confirmedByUser)
    }
}
