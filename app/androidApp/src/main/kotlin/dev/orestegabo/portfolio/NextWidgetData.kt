package dev.orestegabo.portfolio

import org.json.JSONObject
import java.time.LocalDate

internal data class NextWidgetEvent(
    val key: String, val title: String, val status: String, val date: LocalDate,
    val detail: String, val priority: Int = 2,
)

/** Pure selection: prioritize an active subscription/form, then the nearest dated event. */
internal fun nextWidgetEvents(snapshot: JSONObject, today: LocalDate, french: Boolean): List<NextWidgetEvent> {
    fun text(en: String, fr: String) = if (french) fr else en
    fun date(raw: String) = runCatching { LocalDate.parse(raw) }.getOrNull()
    val candidates = mutableListOf<NextWidgetEvent>()
    val issues = snapshot.optJSONArray("issues")
    for (i in 0 until (issues?.length() ?: 0)) {
        val issue = issues?.optJSONObject(i) ?: continue
        val open = date(issue.optString("open")) ?: continue
        val close = date(issue.optString("close")) ?: continue
        if (close < open || close < today) continue
        val active = today >= open
        val form = issue.optBoolean("form") && today >= close.withDayOfMonth(1)
        val status = when {
            active && today == close -> text("Closing today", "Clôture aujourd’hui")
            active -> text("Subscriptions open", "Souscription ouverte")
            form -> text("Form available", "Formulaire disponible")
            else -> text("Next subscription", "Prochaine souscription")
        }
        val detail = if (active) text("Closes", "Clôture") else text("Opens", "Ouverture")
        candidates += NextWidgetEvent("issue:${issue.optString("id")}", issue.optString("title"), status,
            if (active) close else open,
            "$detail · ${if (active) close.year else open.year}" + if (issue.optBoolean("reminder")) text(" · Reminder set", " · Rappel activé") else "",
            if (active) 0 else if (form) 1 else 2)
    }
    val payments = snapshot.optJSONArray("payments")
    for (i in 0 until (payments?.length() ?: 0)) {
        val payment = payments?.optJSONObject(i) ?: continue
        val due = date(payment.optString("date")) ?: continue
        if (due < today) continue
        val maturity = payment.optString("kind") == "maturity"
        candidates += NextWidgetEvent("payment:${payment.optString("kind")}:$due",
            if (maturity) text("Principal repayment", "Remboursement du capital") else text("Coupon payment", "Paiement du coupon"),
            text("Expected payment", "Paiement prévu"), due,
            text("Estimated · ${due.year}", "Estimation · ${due.year}"))
    }
    val ordered = candidates.distinctBy { it.key }.sortedWith(compareBy({ it.priority }, { it.date }, { it.key }))
    val first = ordered.firstOrNull() ?: return emptyList()
    val second = ordered.filter { it.key != first.key }.minWithOrNull(compareBy({ it.date }, { it.key }))
    return listOfNotNull(first, second)
}
