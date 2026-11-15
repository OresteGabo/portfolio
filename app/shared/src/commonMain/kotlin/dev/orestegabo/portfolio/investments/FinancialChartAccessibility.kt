package dev.orestegabo.portfolio.investments

/** Spoken alternatives use the same filtered observations as the plotted chart, never inferred prices. */
internal fun chartAccessibleSummary(points: List<Pair<Double, String>>, style: String): String {
    if (points.isEmpty()) return localized("No chart observations")
    val first = points.first()
    val latest = points.last()
    val trend = when {
        latest.first > first.first -> "Rising"
        latest.first < first.first -> "Falling"
        else -> "Unchanged"
    }
    val values = listOf(localized(style.replaceFirstChar { it.uppercase() }), points.size.toString(),
        displayDateValue(first.second), money(first.first), displayDateValue(latest.second), money(latest.first),
        money(points.minOf { it.first }), money(points.maxOf { it.first }), localized(trend))
    return values.foldIndexed(localized("{0} chart, {1} observations. First: {2}, {3}. Latest: {4}, {5}. Lowest: {6}. Highest: {7}. Trend: {8}.")) {
        index, text, value -> text.replace("{$index}", value)
    }
}

internal fun chartObservationDescription(points: List<Pair<Double, String>>, index: Int, note: String?): String {
    val point = points[index]
    val values = listOf((index + 1).toString(), points.size.toString(), displayDateValue(point.second), money(point.first))
    val description = values.foldIndexed(localized("Observation {0} of {1}: {2}, {3}.")) { n, text, value ->
        text.replace("{$n}", value)
    }
    return description + note?.takeIf { it.isNotBlank() }?.let { " ${localized(it)}" }.orEmpty()
}
