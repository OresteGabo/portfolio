package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.datetime.LocalDate

val LocalChartStyle = staticCompositionLocalOf { "area" }

/** The same observed values in three views; bars use a zero baseline. */
@Composable
fun InvestmentChart(values: List<Double>, labels: List<String> = emptyList(), notes: List<String> = emptyList(), sparse: Boolean = false, dateRange: ClosedRange<LocalDate>? = null) {
    CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = { HiddenFinancialChart(Modifier.fillMaxSize()) }) {
        InvestmentChartContent(values, labels, notes, sparse, dateRange)
    }
}

@Composable
private fun InvestmentChartContent(values: List<Double>, labels: List<String>, notes: List<String>, sparse: Boolean, dateRange: ClosedRange<LocalDate>?) {
    if (LocalNumbersHidden.current) { HiddenFinancialChart(Modifier.fillMaxWidth().height(276.dp)); return }
    val validIndices = values.indices.filter { values[it].isFinite() && values[it] >= 0 }
    val pointNotes = validIndices.map { notes.getOrNull(it) }
    val points = validIndices.map { values[it] to labels.getOrElse(it) { "Observation ${it + 1}" } }
    if (points.isEmpty()) return
    val dates = points.map { runCatching { LocalDate.parse(it.second).toEpochDays().toDouble() }.getOrNull() }
    val dated = dates.all { it != null } && dates.zipWithNext().all { (a, b) -> a!! < b!! }
    val axisRange = dateRange?.takeIf { dated && it.start < it.endInclusive }
    val axisStart = axisRange?.start?.toEpochDays()?.toDouble() ?: dates.first()
    val axisEnd = axisRange?.endInclusive?.toEpochDays()?.toDouble() ?: dates.last()
    val fractions = points.indices.map { index ->
        if (dated && axisRange != null) ((dates[index]!! - axisStart!!) / (axisEnd!! - axisStart)).toFloat().coerceIn(0f, 1f)
        else if (points.size == 1) .5f
        else if (dated) ((dates[index]!! - dates.first()!!) / (dates.last()!! - dates.first()!!)).toFloat()
        else index.toFloat() / points.lastIndex
    }
    val style = LocalChartStyle.current
    val reducedMotion = LocalReducedMotion.current
    // Reveal real observations in date order; never interpolate financial values.
    val reveal = remember(values, labels, style) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reveal, reducedMotion) {
        if (reducedMotion) reveal.snapTo(1f)
        else reveal.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    var selected by remember(values, labels, style) { mutableStateOf<Int?>(null) }
    val current = points[selected?.coerceIn(points.indices) ?: points.lastIndex]
    val first = points.first().first
    val change = if (first > 0 && points.size > 1) (points.last().first - first) / first else null
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val low = if (style == "bars") 0.0 else points.minOf { it.first }
    val high = points.maxOf { it.first }
    val padding = if (style == "bars") high * .12 else ((high - low) * .18).coerceAtLeast(high * .015).coerceAtLeast(1.0)
    val minimum = if (style == "bars") 0.0 else (low - padding).coerceAtLeast(0.0)
    val maximum = (high + padding).coerceAtLeast(minimum + 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FinancialText(money(current.first), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                AppText((if (selected == null) "Latest · ${current.second}" else current.second) +
                    pointNotes.getOrNull(selected ?: points.lastIndex)?.let { " · ${localized(it)}" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall, color = muted)
            }
            if (change != null) Surface(shape = MaterialTheme.shapes.large,
                color = (if (change >= 0) accent else MaterialTheme.colorScheme.error).copy(alpha = .1f)) {
                FinancialText((if (change >= 0) "+" else "") + percent(change), Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge, color = if (change >= 0) accent else MaterialTheme.colorScheme.error)
            }
        }
        Row(Modifier.fillMaxWidth().height(184.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.width(54.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maximum, (maximum + minimum) / 2, minimum).forEach {
                    FinancialText(money(it).removePrefix("RWF "), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()
                .semantics { contentDescription = localized("${localized(style.replaceFirstChar { it.uppercase() })} chart, ${points.size} observations. ${current.second}, ${money(current.first)}") }
                .pointerInput(points, style, axisRange) {
                    detectTapGestures { tap ->
                        val inset = 8.dp.toPx()
                        val fraction = ((tap.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        selected = fractions.indices.minByOrNull { abs(fractions[it] - fraction) }
                    }
                }) {
                val inset = 8.dp.toPx()
                val plotHeight = size.height - inset * 2
                val plotWidth = size.width - inset * 2
                fun x(index: Int) = inset + fractions[index] * plotWidth
                fun y(value: Double) = size.height - inset - ((value - minimum) / (maximum - minimum) * plotHeight).toFloat()
                listOf(0f, .5f, 1f).forEach { fraction ->
                    val height = inset + plotHeight * fraction
                    drawLine(grid, Offset(0f, height), Offset(size.width, height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
                }
                clipRect(right = size.width * reveal.value) {
                    if (style == "bars") {
                        val width = (plotWidth / points.size * .62f).coerceIn(2.dp.toPx(), 20.dp.toPx())
                        points.forEachIndexed { index, point ->
                            drawRoundRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = .4f))),
                                Offset(x(index) - width / 2, y(point.first)), Size(width, (y(0.0) - y(point.first)).coerceAtLeast(0f)),
                                CornerRadius(3.dp.toPx()), alpha = if (selected == null || selected == index) 1f else .3f)
                        }
                    } else {
                        val line = Path().apply { points.forEachIndexed { index, point ->
                            if (index == 0) moveTo(x(index), y(point.first)) else lineTo(x(index), y(point.first))
                        } }
                        if (style == "area" && !sparse) {
                            val area = Path().apply { addPath(line); lineTo(x(points.lastIndex), size.height - inset); lineTo(x(0), size.height - inset); close() }
                            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .28f), Color.Transparent)))
                        }
                        drawPath(line, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = if (sparse) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())) else null))
                        if (sparse || points.size == 1) points.forEachIndexed { index, point ->
                            drawCircle(accent, 3.dp.toPx(), Offset(x(index), y(point.first)))
                        }
                    }
                    val focus = selected ?: points.lastIndex
                    if (selected != null) drawLine(accent.copy(alpha = .4f), Offset(x(focus), inset), Offset(x(focus), size.height - inset), 1.dp.toPx())
                    if (style != "bars" || selected != null) {
                        drawCircle(accent.copy(alpha = .15f), 8.dp.toPx(), Offset(x(focus), y(points[focus].first)))
                        drawCircle(accent, 3.5.dp.toPx(), Offset(x(focus), y(points[focus].first)))
                    }
                }
            }
        }
        if (axisRange != null) {
            val ticks = chartMonthTicks(axisRange)
            val measurer = rememberTextMeasurer()
            val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = muted)
            Canvas(Modifier.fillMaxWidth().padding(start = 62.dp).height(42.dp)
                .semantics { contentDescription = localized("Months: ") + ticks.joinToString { localized(it.second) } }) {
                val inset = 8.dp.toPx()
                val plotWidth = size.width - inset * 2
                ticks.forEach { (date, month) ->
                    val fraction = (date.toEpochDays().toDouble() - axisStart!!) / (axisEnd!! - axisStart)
                    val x = inset + fraction.toFloat() * plotWidth
                    drawLine(grid, Offset(x, 0f), Offset(x, 4.dp.toPx()), 1.dp.toPx())
                    val text = measurer.measure(localized(month), labelStyle)
                    val anchor = Offset(x, 20.dp.toPx())
                    rotate(-45f, anchor) {
                        drawText(text, topLeft = Offset(x - text.size.width / 2f, anchor.y - text.size.height / 2f))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 62.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(points.first().second, points.last().second).forEach {
                    AppText(it, style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
}
