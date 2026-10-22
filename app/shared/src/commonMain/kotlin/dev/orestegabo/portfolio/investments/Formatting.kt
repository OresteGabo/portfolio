package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Clock

val Today: LocalDate get() = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
fun formatNumber(value: Double): String {
    if (!value.isFinite()) return "Unavailable"
    val scaled = round(abs(value) * 100).toLong()
    val whole = (scaled / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val fraction = (scaled % 100).toString().padStart(2, '0').trimEnd('0')
    return (if (value < 0) "−" else "") + whole + if (fraction.isEmpty()) "" else ".$fraction"
}
fun money(amount: Double, currency: String = "RWF") = "$currency ${formatNumber(amount)}"
fun percent(rate: Double) = "${formatNumber(rate * 100)}%"
