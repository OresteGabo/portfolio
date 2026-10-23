package dev.orestegabo.portfolio.investments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
fun CalendarScreen(model: PortfolioModel) {
    val today = Today
    var selectedValue by rememberSaveable { mutableStateOf(today.toString()) }
    var filter by rememberSaveable { mutableStateOf(CalendarFilter.ALL) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val selected = calendarDate(selectedValue) ?: today
    val allEvents = remember(model.calendar, model.portfolio) { investmentCalendarEvents(model.calendar, model.portfolio) }
    val events = remember(allEvents, filter) { allEvents.filter { it.matches(filter) } }
    val byDate = remember(events) { events.groupBy { it.date } }
    val ranges = remember(model.calendar) { calendarSubscriptionRanges(model.calendar) }
    val monthRanges = if (filter == CalendarFilter.PAYMENTS) emptyList() else subscriptionRangesInMonth(ranges, selected)
    val focusedRange = preferredSubscriptionRange(monthRanges, selected)
    val selectedEvents = byDate[selected].orEmpty()
    val next = events.firstOrNull { it.date > selected }
    fun select(date: LocalDate) { selectedValue = date.toString(); expanded = null }

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    CalendarFilter.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = filter == option, onClick = { filter = option; expanded = null },
                            shape = SegmentedButtonDefaults.itemShape(index, CalendarFilter.entries.size), icon = {}) {
                            Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "About this calendar") }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { select(shiftCalendarMonth(selected, -1)) }) {
                            Icon(Icons.Outlined.ChevronLeft, "Previous month")
                        }
                        Text("${selected.monthLabel()} ${selected.year}", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { select(today) }) { Text("Today") }
                        IconButton(onClick = { select(shiftCalendarMonth(selected, 1)) }) {
                            Icon(Icons.Outlined.ChevronRight, "Next month")
                        }
                    }
                    focusedRange?.let { range ->
                        SubscriptionRangeHeader(range) { select(range.start) }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Column {
                        calendarMonthDays(selected).chunked(7).filter { week -> week.any { it.month == selected.month } }.forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    CalendarDay(date, selected, today, byDate[date].orEmpty(), monthRanges, focusedRange, Modifier.weight(1f)) { select(date) }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                        CalendarCategory.entries.filter { category ->
                            filter == CalendarFilter.ALL || (filter == CalendarFilter.ISSUANCES) == (category == CalendarCategory.ISSUANCE)
                        }.forEach { category ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                EventDot(category.color())
                                Text(if (category == CalendarCategory.ISSUANCE) "Subscription window" else category.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (selected == today) "Today" else selected.dayLabel(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${selected.day} ${selected.monthLabel()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text("${selectedEvents.size} ${if (selectedEvents.size == 1) "event" else "events"}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selectedEvents.isEmpty()) item {
            EmptyState(
                title = if (filter == CalendarFilter.PAYMENTS && events.isEmpty()) "No payments scheduled" else "No events",
                artwork = EmptyArtwork.CALENDAR,
                compact = true,
                actionLabel = next?.let { "Next · ${it.date.day} ${it.date.monthLabel()}" },
                onAction = next?.let { upcoming -> { select(upcoming.date) } }
            )
        }
        items(selectedEvents, key = { it.id }) { event ->
            CalendarEventTile(event, expanded == event.id, model.purchases) { expanded = if (expanded == event.id) null else event.id }
        }
    }
    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("About this calendar") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Issuances · BNR FY 2026/2027", fontWeight = FontWeight.SemiBold)
            Text("Dates follow the BNR Treasury Bond Issuance Calendar for FY 2026/2027, as of 24 June 2026. The schedule runs from July 2026 to June 2027. Confirm changes with BNR or your broker.")
            Text("Highlighted ranges run from the book opening date through the closing date. The calendar automatically highlights the current or nearest subscription window in the displayed month. Settlement, coupons and maturities are individual dates.")
            Text("Your payments come from active bond purchases. Coupons are schedule estimates, not confirmation that a payment was received.")
        }
    }, confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it") } })
}

@Composable
private fun SubscriptionRangeHeader(range: CalendarSubscriptionRange, choose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(onClick = choose).clearAndSetSemantics {
        contentDescription = "${range.record.text("title")}, subscriptions open ${range.start.calendarLabel()}, close ${range.end.calendarLabel()}"
        role = Role.Button
        onClick { choose(); true }
    },
        shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = .4f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(range.record.text("title"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text("${range.start.day} ${range.start.monthLabel().take(3)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Opens", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Subscriptions", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EventDot(colors.primary)
                        HorizontalDivider(Modifier.weight(1f), color = colors.primary.copy(alpha = .35f))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = colors.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${range.end.day} ${range.end.monthLabel().take(3)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Closes", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate, selected: LocalDate, today: LocalDate, events: List<InvestmentCalendarEvent>,
    ranges: List<CalendarSubscriptionRange>, focused: CalendarSubscriptionRange?, modifier: Modifier, choose: () -> Unit
) {
    val range = ranges.firstOrNull { it === focused && date in it } ?: ranges.firstOrNull { date in it }
    val endpoint = range != null && (date == range.start || date == range.end)
    val chosen = date == selected
    val currentMonth = date.month == selected.month && date.year == selected.year
    val colors = MaterialTheme.colorScheme
    val solid = endpoint || (chosen && range == null && focused == null)
    val color = when {
        solid -> colors.onPrimary
        range != null || date == today -> colors.primary
        !currentMonth -> colors.onSurfaceVariant.copy(alpha = .45f)
        else -> colors.onSurface
    }
    Box(modifier.height(48.dp).clickable(role = Role.Button, onClick = choose).semantics(mergeDescendants = true) {
        contentDescription = date.calendarLabel() + (if (date == today) ", today" else "") +
            (range?.let { ", ${it.record.text("title")}, subscription window ${it.start} to ${it.end}" } ?: "") +
            (if (events.isEmpty()) ", no events" else ", ${events.joinToString { it.label }}")
        this.selected = chosen || endpoint
    }, contentAlignment = Alignment.Center) {
        if (range != null) {
            val left = if (date == range.start) 24.dp else 0.dp
            val right = if (date == range.end) 24.dp else 0.dp
            Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(topStart = left, bottomStart = left,
                topEnd = right, bottomEnd = right)).background(colors.primary.copy(alpha = if (range === focused) .16f else .08f)))
        }
        Surface(modifier = Modifier.size(38.dp), shape = CircleShape,
            border = if (!solid && (date == today || chosen)) BorderStroke(1.dp, colors.primary) else null,
            color = if (solid) colors.primary else Color.Transparent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(date.day.toString(), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (solid || range != null || date == today) FontWeight.Bold else FontWeight.Normal,
                    color = color, modifier = Modifier.clearAndSetSemantics {})
                Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    events.map { it.category }.distinct().forEach { EventDot(if (solid) color else it.color(), 3) }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventTile(event: InvestmentCalendarEvent, expanded: Boolean, purchases: List<JsonRecord>, toggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            ListItem(
                headlineContent = { Text(event.title, fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.label, color = event.category.color())
                        event.amount?.let { Text(money(it, event.record.text("currency")), fontWeight = FontWeight.SemiBold) }
                    }
                },
                leadingContent = { Icon(when (event.category) {
                    CalendarCategory.ISSUANCE -> Icons.Outlined.AccountBalance
                    CalendarCategory.COUPON -> Icons.Outlined.Payments
                    CalendarCategory.MATURITY -> Icons.Outlined.EventAvailable
                }, null, tint = event.category.color()) },
                trailingContent = { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse details" else "Expand details") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(onClick = toggle),
            )
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    if (event.category == CalendarCategory.ISSUANCE) {
                        val record = event.record
                        listOf("Announcement" to "announcementDate", "Book opens" to "openBookDate", "Book closes" to "closingBookDate",
                            "Settlement" to "settlementDate", "Maturity" to "maturityDate").forEach { (label, field) -> Fact(label, record.text(field)) }
                        val recorded = purchases.filter { it.text("settlementDate") == record.text("settlementDate") && it.text("maturityDate") == record.text("maturityDate") }
                        Fact("Recorded applications", recorded.size.toString())
                        recorded.forEach { Fact(it.text("bondName"), "${it.text("status")} · ${money(it.optDouble("faceValue", 0.0), it.text("currency"))}") }
                        Text(when (event.label) {
                            "Book opens", "Book open" -> "Confirm the terms and submit your bid to your broker before the book closes."
                            "Book closes" -> "Confirm your bid with the broker before today’s closing deadline."
                            "Settlement" -> "Check allocation, cash debit, and your purchase record with your broker."
                            else -> "Review the prospectus and prepare your bid before the book opens."
                        }, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Fact("Date", event.date.toString())
                        Fact("Broker", event.record.text("broker").ifBlank { "—" })
                        Text(if (event.category == CalendarCategory.COUPON) "${event.record.text("scheduleConfidence").ifBlank { "Estimated" }} schedule · Expected net coupon; confirm receipt with your broker."
                            else "Expected principal repayment; confirm receipt with your broker.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable private fun CalendarCategory.color(): Color = when (this) {
    CalendarCategory.ISSUANCE -> MaterialTheme.colorScheme.primary
    CalendarCategory.COUPON -> if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF9DCBFD) else Color(0xFF246C9F)
    CalendarCategory.MATURITY -> if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF8DDBB7) else Color(0xFF22705D)
}
@Composable private fun EventDot(color: Color, size: Int = 6) {
    Canvas(Modifier.size(size.dp)) { drawCircle(color) }
}
