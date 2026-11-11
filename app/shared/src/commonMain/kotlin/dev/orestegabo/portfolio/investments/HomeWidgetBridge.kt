package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*

/** Native home-widget bridge. Widgets receive a minimal projection, never credentials or amounts. */
object HomeWidgetBridge {
    var available by mutableStateOf(false)
        private set
    private var save: ((String?) -> Unit)? = null
    private var pin: ((Boolean) -> Unit)? = null
    var requestedDate by mutableStateOf<String?>(null)
        private set
    var routeRevision by mutableIntStateOf(0)
        private set
    fun install(save: (String?) -> Unit, pin: (Boolean) -> Unit) {
        this.save = save; this.pin = pin; available = true
    }
    fun publish(snapshot: String) { save?.invoke(snapshot) }
    fun clear() { save?.invoke(null) }
    fun requestPin(medium: Boolean) { pin?.invoke(medium) }
    fun openCalendar(date: String) {
        if (calendarDate(date) == null) return
        requestedDate = date; routeRevision++
    }
}

/** Public issuance facts and generic payment dates only; no holding names, amounts, or purchase IDs. */
internal fun homeWidgetSnapshot(model: PortfolioModel): String {
    val today = bondSubscriptionDate(kotlin.time.Clock.System.now())
    val issues = model.calendar.map { issue ->
        JsonRecord().put("id", issue.text("id")).put("title", issue.text("title"))
            .put("open", issue.text("openBookDate")).put("close", issue.text("closingBookDate"))
            .put("form", bondApplicationFor(issue, model.documents) != null)
            .put("reminder", issue.text("closingBookDate").take(7) in model.bondReminderMonths)
    }
    val payments = investmentCalendarEvents(emptyList(), model.portfolio)
        .filter { it.date >= today && it.category != CalendarCategory.ISSUANCE }
        .filter { it.category != CalendarCategory.COUPON || model.couponReceipt(it.record.text("id"), it.date.toString())?.optBoolean("received") != true }
        .distinctBy { it.category to it.date }.take(60).map {
            JsonRecord().put("date", it.date.toString())
                .put("kind", if (it.category == CalendarCategory.COUPON) "coupon" else "maturity")
        }
    return JsonRecord().put("language", AppLanguage.code).put("issues", issues).put("payments", payments).toString()
}
