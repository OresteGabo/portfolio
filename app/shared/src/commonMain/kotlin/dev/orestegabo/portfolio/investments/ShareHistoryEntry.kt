package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** Request dates order pending rows; only a confirmed contract supplies a purchase date. */
internal sealed interface ShareHistoryEntry {
    val record: JsonRecord
    val date: LocalDate?
    val key: String

    data class Purchase(override val record: JsonRecord) : ShareHistoryEntry {
        override val date get() = calendarDate(record.text("tradeDate"))
        override val key get() = "purchase:${record.text("id")}"
    }

    data class PendingOrder(override val record: JsonRecord) : ShareHistoryEntry {
        override val date get() = calendarDate(record.text("requestDate"))
        override val key get() = "order:${record.text("id")}"
    }
}

internal fun shareHistoryEntries(holdings: List<JsonRecord>, orders: List<JsonRecord>, selected: JsonRecord): List<ShareHistoryEntry> {
    fun matches(row: JsonRecord) = row.text("ticker") == selected.text("ticker") && row.text("currency") == selected.text("currency")
    val entries: List<ShareHistoryEntry> = holdings.filter(::matches).map { ShareHistoryEntry.Purchase(it) } +
        orders.filter { matches(it) && it.text("status") == "pending" }.map { ShareHistoryEntry.PendingOrder(it) }
    return entries.sortedByDescending { it.date }
}
