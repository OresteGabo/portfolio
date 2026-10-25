package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.time.Clock

val Today: LocalDate get() = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
fun formatNumber(value: Double): String {
    if (!value.isFinite()) return localized("Unavailable")
    val scaled = round(abs(value) * 100).toLong()
    val whole = (scaled / 100).toString().reversed().chunked(3).joinToString(if (AppLanguage.code == "fr") "\u202f" else ",").reversed()
    val fraction = (scaled % 100).toString().padStart(2, '0').trimEnd('0')
    return (if (value < 0) "−" else "") + whole + if (fraction.isEmpty()) "" else (if (AppLanguage.code == "fr") ",$fraction" else ".$fraction")
}
fun money(amount: Double, currency: String = "RWF"): String {
    val number = if (currency.equals("RWF", ignoreCase = true) && amount.isFinite()) {
        val francs = floor(amount)
        (if (francs < 0) "−" else "") + abs(francs).toLong().toString()
            .reversed().chunked(3).joinToString(if (AppLanguage.code == "fr") "\u202f" else ",").reversed()
    } else formatNumber(amount)
    return "$currency $number"
}
fun percent(rate: Double) = formatNumber(rate * 100) + if (AppLanguage.code == "fr") "\u00a0%" else "%"

/** Accept the decimal comma from French keyboards without changing JSON/API serialization. */
internal fun String.toInputNumberOrNull(): Double? {
    val compact = filterNot { it.isWhitespace() || it == '\u00a0' || it == '\u202f' }
    if (',' in compact && '.' in compact) return null
    return compact.replace(',', '.').toDoubleOrNull()
}
