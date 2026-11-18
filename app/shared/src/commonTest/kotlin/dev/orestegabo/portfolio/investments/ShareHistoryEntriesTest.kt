package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class ShareHistoryEntriesTest {
    private val selected = JsonRecord().put("ticker", "BOK").put("currency", "RWF")
    private fun purchase(id: String, date: String) = JsonRecord(selected.toString()).put("id", id).put("tradeDate", date)
    private fun order(id: String, date: String, status: String = "pending") =
        JsonRecord(selected.toString()).put("id", id).put("requestDate", date).put("status", status)

    @Test fun pendingRequestsAndPurchasesShareAChronologyWithoutSharingIdentityOrInventingATradeDate() {
        val pending = order("same-id", "2000-01-10")
        val entries = shareHistoryEntries(listOf(purchase("same-id", "2000-01-04")), listOf(pending), selected)
        assertIs<ShareHistoryEntry.PendingOrder>(entries.first())
        assertIs<ShareHistoryEntry.Purchase>(entries.last())
        assertEquals(listOf("order:same-id", "purchase:same-id"), entries.map { it.key })
        assertEquals(LocalDate(2000, 1, 10), entries.first().date)
        assertEquals("", pending.text("tradeDate"))
    }

    @Test fun contractDateReordersTheConfirmedPurchaseAndCompletedOrdersDoNotRemainInHistory() {
        val existing = purchase("older", "2000-01-04")
        val pending = order("order-id", "2000-01-10")
        assertEquals("order:order-id", shareHistoryEntries(listOf(existing), listOf(pending), selected).first().key)
        val actual = purchase("executed", "2000-01-02")
        val entries = shareHistoryEntries(listOf(existing, actual), listOf(order("order-id", "2000-01-10", "confirmed")), selected)
        assertEquals(listOf("purchase:older", "purchase:executed"), entries.map { it.key })
        assertTrue(entries.all { it is ShareHistoryEntry.Purchase })
        assertEquals(LocalDate(2000, 1, 2), entries.last().date)
    }

    @Test fun historyExcludesOtherCompaniesCurrenciesAndCancelledOrdersAndKeepsUnknownDatesUnknown() {
        val unknown = order("unknown", "").put("tradeDate", "2099-01-01")
        val entries = shareHistoryEntries(listOf(purchase("saved", "2000-01-04")), listOf(
            order("cancelled", "2001-01-01", "cancelled"), order("other", "2001-01-01").put("ticker", "BLR"),
            order("foreign", "2001-01-01").put("currency", "USD"), unknown), selected)
        assertEquals(listOf("purchase:saved", "order:unknown"), entries.map { it.key })
        assertNull(entries.last().date)
    }
}
