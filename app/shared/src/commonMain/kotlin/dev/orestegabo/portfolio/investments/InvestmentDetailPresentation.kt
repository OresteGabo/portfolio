package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** Today's manually confirmed payment is complete; an automatic marker is not bank evidence. */
internal fun nextDetailCoupon(purchase: JsonRecord, receipts: List<JsonRecord>, today: LocalDate): LocalDate? {
    if (purchase.text("status") != "active") return null
    val terms = purchase.terms() ?: return null
    return couponSchedule(terms).firstOrNull { due ->
        due >= today && !(due == today && receipts.any {
            it.text("purchaseId") == purchase.text("id") && it.text("dueDate") == due.toString() &&
                it.text("currency").ifBlank { purchase.text("currency") } == purchase.text("currency") &&
                it.optBoolean("received") && it.text("method") == "manual"
        })
    }
}

internal fun shareDetailLots(holdings: List<JsonRecord>, selected: JsonRecord): List<JsonRecord> = holdings.filter {
    it.text("ticker") == selected.text("ticker") && it.text("currency") == selected.text("currency")
}.sortedByDescending { it.text("tradeDate") }

internal data class DetailQuote(val price: Double?, val date: LocalDate?, val cached: Boolean)

/** Presentation metadata follows the same RWF/date/positive-price rules as valuedShares. */
internal fun detailQuote(quotes: JsonRecord?, ticker: String, currency: String, today: LocalDate): DetailQuote {
    val source = quotes?.optJSONObject("source")
    val date = calendarDate(source?.text("publishedAt").orEmpty().take(10))?.takeIf { it <= today }
    val price = if (currency == "RWF" && date != null) quotes?.rows("quotes")?.firstOrNull {
        it.text("ticker").trim().uppercase() == ticker.trim().uppercase()
    }?.optDouble("closing", Double.NaN)?.takeIf { it.isFinite() && it > 0 } else null
    return DetailQuote(price, date, source?.optBoolean("stale") == true)
}

/** Missing numeric terms are unknown, not a zero coupon or tax exemption. */
internal fun bondDetailTerms(purchase: JsonRecord): BondTerms? = purchase.terms()?.takeIf {
    listOf("faceValue", "couponRate", "withholdingTaxRate").all { key ->
        purchase.optDouble(key, Double.NaN).isFinite()
    }
}
