package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Presentation only: API payloads, date inputs, sorting and persisted values remain ISO. */
internal fun displayDate(raw: String, full: Boolean = false): String {
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
    return if (full) date.calendarLabel() else detailDate(date)
}

internal fun displayMonth(raw: String): String = runCatching {
    require(isoDisplayMonth.matches(raw))
    val date = LocalDate.parse("$raw-01")
    "${date.monthLabel()} ${date.year}"
}.getOrDefault(raw)

internal fun displayTimestamp(raw: String, zone: TimeZone = TimeZone.currentSystemDefault()): String = runCatching {
    val value = Instant.parse(raw).toLocalDateTime(zone)
    "${value.date.calendarLabel()} · ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
}.getOrElse { displayDate(raw) }

private val isoDisplayDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val isoDisplayMonth = Regex("[0-9]{4}-[0-9]{2}")
private val isoDisplayTimestamp = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T.*")

internal fun displayDateValue(raw: String): String = when {
    raw.length < 7 || raw.firstOrNull()?.isDigit() != true -> raw
    isoDisplayDate.matches(raw) -> displayDate(raw)
    isoDisplayMonth.matches(raw) -> displayMonth(raw)
    isoDisplayTimestamp.matches(raw) -> displayTimestamp(raw)
    else -> raw
}
