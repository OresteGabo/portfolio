package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*

/** Shared ISO-date boundary; presentation and locale formatting stay in app:shared. */
fun calendarDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
fun shiftCalendarMonth(date: LocalDate, months: Int): LocalDate = date.plus(DatePeriod(months = months))

fun JsonRecord.terms(): BondTerms? = runCatching {
    val frequency = if (has("couponFrequency")) optDouble("couponFrequency") else 2.0
    require(frequency.isFinite() && frequency in 1.0..12.0 && frequency % 1.0 == 0.0)
    BondTerms(LocalDate.parse(text("purchaseDate")), LocalDate.parse(text("maturityDate")), optDouble("faceValue", 0.0),
        optDouble("couponRate", 0.0), optDouble("withholdingTaxRate", 0.0), frequency.toInt(),
        text("firstCouponDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        optJSONArray("couponDates")?.let { dates -> (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) } }.orEmpty())
}.getOrNull()

