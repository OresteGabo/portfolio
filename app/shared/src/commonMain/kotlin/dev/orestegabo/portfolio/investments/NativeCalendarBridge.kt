package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*

/** Presents the OS event editor. The user chooses a calendar, alerts and whether to save. */
object NativeCalendarBridge {
    var available by mutableStateOf(false)
        private set
    private var open: ((String, String, String) -> Unit)? = null
    fun install(open: (String, String, String) -> Unit) {
        this.open = open
        available = true
    }
    fun present(date: String, title: String, notes: String) {
        if (calendarDate(date) == null || title.isBlank()) return
        open?.invoke(date, title.take(180), notes.take(1000))
    }
}

internal data class PhoneCalendarEvent(val date: String, val title: String, val notes: String)

/** Personal payments use generic labels: a phone calendar may sync to other services. */
internal fun phoneCalendarEvent(event: InvestmentCalendarEvent): PhoneCalendarEvent {
    val title = when (event.category) {
        CalendarCategory.ISSUANCE -> "${localized(event.label)} · ${localized(event.title)}"
        CalendarCategory.COUPON -> localized("Expected coupon payment")
        CalendarCategory.MATURITY -> localized("Expected principal repayment")
    }
    return PhoneCalendarEvent(event.date.toString(), "Struct Capital · $title",
        localized("Dates are indicative. Confirm subscription deadlines and payments with your broker.") +
            "\nstructcapital://calendar?date=${event.date}")
}
