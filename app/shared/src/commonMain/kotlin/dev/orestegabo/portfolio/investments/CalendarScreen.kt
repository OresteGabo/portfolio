package dev.orestegabo.portfolio.investments

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

@Composable
fun CalendarScreen(model: PortfolioModel, initialDate: LocalDate? = null, addPending: (JsonRecord, LocalDate) -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val selectionFeedback = LocalSelectionFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val today by produceState(bondSubscriptionDate(Clock.System.now()), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = bondSubscriptionDate(Clock.System.now())
                delay(30_000)
            }
        }
    }
    var selectedValue by rememberSaveable(initialDate) { mutableStateOf((initialDate ?: today).toString()) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val selected = calendarDate(selectedValue) ?: today
    var historyRequested by remember(model.user?.text("id")) { mutableStateOf(false) }
    LaunchedEffect(model.busy, model.historyComplete) {
        if (!model.busy && !model.historyComplete && !historyRequested) {
            historyRequested = true
            model.loadInvestmentHistory()
        }
    }
    val events = remember(model.calendar, model.portfolio) { investmentCalendarEvents(model.calendar, model.portfolio) }
    val byDate = remember(events) { events.groupBy { it.date } }
    val ranges = remember(model.calendar) { calendarSubscriptionRanges(model.calendar) }
    val monthEvents = calendarMonthEvents(events, selected)
    val next = events.firstOrNull { it.date > selected && (it.date.year != selected.year || it.date.month != selected.month) }
    val availableIssues = subscriptionRangesInMonth(ranges, selected).filter { (calendarDate(it.record.text("maturityDate")) ?: today) > today }
    val preferred = preferredSubscriptionRange(availableIssues, selected)
    var chooseIssue by remember { mutableStateOf(false) }
    fun select(date: LocalDate) {
        if (date != selected) selectionFeedback()
        selectedValue = date.toString(); expanded = null
    }
    val changeMonth by rememberUpdatedState<(Int) -> Unit> { offset -> select(shiftCalendarMonth(selected, offset)) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        /*item(key = "subscription-status") {
            SubscriptionTodayCard(ranges, today, ::select)
        }*/
        item {
            Surface(modifier = Modifier.pointerInput(Unit) {
                var distance = 0f
                val threshold = (size.width * .18f).coerceIn(48.dp.toPx(), 96.dp.toPx())
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                    onDragCancel = { distance = 0f },
                    onDragEnd = {
                        when {
                            distance <= -threshold -> changeMonth(1)
                            distance >= threshold -> changeMonth(-1)
                        }
                        distance = 0f
                    }
                )
            }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { select(shiftCalendarMonth(selected, -1)) }) {
                            AppIcon(Icons.Outlined.ChevronLeft, "Previous month")
                        }
                        AppText("${selected.monthLabel().take(3)} ${selected.year}", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { select(today) }) { AppText("Today") }
                        IconButton(onClick = { select(shiftCalendarMonth(selected, 1)) }) {
                            AppIcon(Icons.Outlined.ChevronRight, "Next month")
                        }
                    }
                    AnimatedContent(
                        targetState = LocalDate(selected.year, selected.month, 1),
                        transitionSpec = {
                            val forward = targetState > initialState
                            if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                            else (slideInHorizontally { width -> if (forward) width else -width } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> if (forward) -width else width } + fadeOut())
                        }, label = "Calendar month"
                    ) { displayMonth ->
                        val monthRanges = subscriptionRangesInMonth(ranges, displayMonth)
                        val focusedRange = preferredSubscriptionRange(monthRanges, selected)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            focusedRange?.let { range ->
                                SubscriptionRangeHeader(range, today) { select(range.start) }

                            }
                            Row(Modifier.fillMaxWidth()) {
                                calendarWeekdayLabels().forEach { day ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        AppText(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Column {
                                calendarMonthDays(displayMonth).chunked(7).filter { week -> week.any { it.month == displayMonth.month } }.forEach { week ->
                                    Row(Modifier.fillMaxWidth()) {
                                        week.forEach { date ->
                                            CalendarDay(date, selected, today, byDate[date].orEmpty(), monthRanges, focusedRange, Modifier.weight(1f)) { select(date) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                        CalendarCategory.entries.forEach { category ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                EventDot(category.color())
                                AppText(if (category == CalendarCategory.ISSUANCE) "Subscription window" else category.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText("${selected.monthLabel()} events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                AppText("${monthEvents.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { showInfo = true }) { AppIcon(Icons.Outlined.Info, "About this calendar") }
            }
        }
        if (monthEvents.isEmpty()) item {
            EmptyState(
                title = "No events",
                artwork = EmptyArtwork.CALENDAR,
                compact = true,
                actionLabel = next?.let { "Next · ${it.date.day} ${it.date.monthLabel()}" },
                onAction = next?.let { upcoming -> { select(upcoming.date) } }
            )
        }
        monthEvents.groupBy { it.date }.forEach { (date, entries) ->
            item(key = "date:$date") {
                AppText("${date.dayLabel()} · ${date.day} ${date.monthLabel()}",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = if (date == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(entries, key = { it.id }) { event ->
                CalendarEventTile(event, expanded == event.id, model.purchases, model.documents, model.historyComplete, model.busy, today,
                    reloadHistory = { model.loadInvestmentHistory() }, toggle = { expanded = if (expanded == event.id) null else event.id })
            }
        }
    }
    if (preferred != null) ExtendedFloatingActionButton(
        onClick = { if (!model.busy) { if (availableIssues.size == 1) addPending(preferred.record, selected) else chooseIssue = true } },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics { contentDescription = localized("Record pending bond purchase") },
        icon = { AppIcon(Icons.Outlined.Add, null) }, text = { AppText("Record purchase") })
    }
    if (chooseIssue) AlertDialog(onDismissRequest = { chooseIssue = false }, title = { AppText("Choose a bond") }, text = {
        Column { availableIssues.forEach { range ->
            ListItem(headlineContent = { AppText(range.record.text("title")) },
                supportingContent = { AppText("${range.start.day}–${range.end.day} ${range.end.monthLabel()}") },
                modifier = Modifier.clickable { chooseIssue = false; addPending(range.record, selected) })
        } }
    }, confirmButton = { TextButton(onClick = { chooseIssue = false }) { AppText("Cancel") } })

    if (showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { AppText("About this calendar") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText("Issuances · BNR FY 2026/2027", fontWeight = FontWeight.SemiBold)
            AppText("Dates follow the BNR Treasury Bond Issuance Calendar for FY 2026/2027, as of 24 June 2026. The schedule runs from July 2026 to June 2027. Confirm changes with BNR or your broker.")
            AppText("Highlighted ranges run from the book opening date through the closing date. The calendar automatically highlights the current or nearest subscription window in the displayed month. Settlement, coupons and maturities are individual dates.")
            AppText("Subscription status follows published dates in Rwanda time and updates automatically. On the closing date, confirm the exact deadline with your broker. It does not confirm that bids are currently being accepted.")
            AppText("Your payments come from active bond purchases. Coupons are schedule estimates, not confirmation that a payment was received.")
        }
    }, confirmButton = { TextButton(onClick = { showInfo = false }) { AppText("Got it") } })
}

/*
@Composable
private fun SubscriptionTodayCard(ranges: List<CalendarSubscriptionRange>, today: LocalDate, select: (LocalDate) -> Unit) {
    val active = ranges.filter { today in it }
    val upcoming = ranges.firstOrNull { it.start > today }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText("${localized("Today")} · ${today.day} ${today.monthLabel()}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (active.isNotEmpty()) {
                active.forEach { range ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { select(today) }.padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubscriptionStatusBadge(range.statusOn(today))
                        AppText(range.record.text("title"), fontWeight = FontWeight.SemiBold)
                        AppText(if (today == range.end) "Confirm the closing deadline with your broker."
                            else "${localized("Closes")} · ${range.end.calendarLabel()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                AppText(if (ranges.isEmpty()) "No subscription dates available" else "No subscriptions scheduled today",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                upcoming?.let { range ->
                    TextButton(onClick = { select(range.start) }, contentPadding = PaddingValues(0.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText(range.record.text("title"), fontWeight = FontWeight.Medium)
                            AppText("${localized("Opens")} · ${range.start.calendarLabel()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            AppText("Based on published dates · Rwanda time", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
*/

@Composable
private fun SubscriptionStatusBadge(status: SubscriptionStatus) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val color = when (status) {
        SubscriptionStatus.OPEN -> if (dark) Color(0xFF8DDBB7) else Color(0xFF22705D)
        SubscriptionStatus.CLOSING_TODAY -> if (dark) Color(0xFFFFD180) else Color(0xFF875000)
        SubscriptionStatus.UPCOMING -> MaterialTheme.colorScheme.primary
        SubscriptionStatus.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            EventDot(color, 7)
            AppText(status.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubscriptionRangeHeader(range: CalendarSubscriptionRange, today: LocalDate, choose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(onClick = choose).clearAndSetSemantics {
        contentDescription = "${localized(range.record.text("title"))}, ${localized(range.statusOn(today).label)}. " +
            localized("subscriptions open ${range.start.calendarLabel()}, close ${range.end.calendarLabel()}")
        role = Role.Button
        onClick { choose(); true }
    },
        shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = .4f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubscriptionStatusBadge(range.statusOn(today))
            AppText(range.record.text("title"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    AppText("${range.start.day} ${range.start.monthLabel().take(3)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppText("Opens", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText("Subscriptions", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EventDot(colors.primary)
                        HorizontalDivider(Modifier.weight(1f), color = colors.primary.copy(alpha = .35f))
                        AppIcon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = colors.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    AppText("${range.end.day} ${range.end.monthLabel().take(3)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppText("Closes", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
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
    BoxWithConstraints(modifier.height(48.dp).clickable(role = Role.Button, onClick = choose).semantics(mergeDescendants = true) {
        contentDescription = date.calendarLabel() + localized(if (date == today) ", today" else "") +
            (range?.let { localized(", ${localized(it.record.text("title"))}, subscription window ${it.start} to ${it.end}") } ?: "") +
            (if (events.isEmpty()) localized(", no events") else ", ${events.joinToString { localized(it.label) }}")
        this.selected = chosen || endpoint
    }, contentAlignment = Alignment.Center) {
        val dayDiameter = minOf(38.dp, maxWidth)
        val endpointInset = (maxWidth - dayDiameter) / 2
        if (range != null) {
            val first = date == range.start
            val last = date == range.end
            val left = if (first) dayDiameter / 2 else 0.dp
            val right = if (last) dayDiameter / 2 else 0.dp
            // End caps share the date circle's bounds rather than the wider day cell.
            Box(Modifier.fillMaxWidth().height(dayDiameter)
                .padding(start = if (first) endpointInset else 0.dp, end = if (last) endpointInset else 0.dp)
                .clip(RoundedCornerShape(topStart = left, bottomStart = left,
                topEnd = right, bottomEnd = right)).background(colors.primary.copy(alpha = if (range === focused) .16f else .08f)))
        }
        Surface(modifier = Modifier.size(dayDiameter), shape = CircleShape,
            border = if (!solid && (date == today || chosen)) BorderStroke(1.dp, colors.primary) else null,
            color = if (solid) colors.primary else Color.Transparent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AppText(date.day.toString(), style = MaterialTheme.typography.bodyMedium,
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
private fun CalendarEventTile(event: InvestmentCalendarEvent, expanded: Boolean, purchases: List<JsonRecord>, documents: List<JsonRecord>,
    historyComplete: Boolean, busy: Boolean, today: LocalDate, reloadHistory: () -> Unit, toggle: () -> Unit) {
    val personal = if (event.category == CalendarCategory.ISSUANCE) calendarIssuePurchases(event.record, purchases, documents, historyComplete) else null
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            ListItem(
                headlineContent = { AppText(event.title, fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppText(event.label, color = event.category.color())
                        if (personal != null && personal.status != CalendarPurchaseStatus.NONE) {
                            AppText(personal.status.label, style = MaterialTheme.typography.labelMedium,
                                color = if (personal.status == CalendarPurchaseStatus.UNAVAILABLE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                        event.amount?.let { FinancialText(money(it, event.record.text("currency")), fontWeight = FontWeight.SemiBold) }
                    }
                },
                leadingContent = { AppIcon(when (event.category) {
                    CalendarCategory.ISSUANCE -> Icons.Outlined.AccountBalance
                    CalendarCategory.COUPON -> Icons.Outlined.Payments
                    CalendarCategory.MATURITY -> Icons.Outlined.EventAvailable
                }, null, tint = event.category.color()) },
                trailingContent = { AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse details" else "Expand details") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(onClick = toggle),
            )
            AppAnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    if (event.category == CalendarCategory.ISSUANCE) {
                        val record = event.record
                        listOf("Announcement" to "announcementDate", "Book opens" to "openBookDate", "Book closes" to "closingBookDate",
                            "Settlement" to "settlementDate", "Maturity" to "maturityDate").forEach { (label, field) -> Fact(label, record.text(field)) }
                        val recorded = personal!!.records
                        if (historyComplete) Fact("Recorded purchases", recorded.size.toString())
                        recorded.forEach { Fact(it.text("bondName"), "${displayRecordValue(it.text("status"))} · ${money(it.optDouble("faceValue", 0.0), it.text("currency"))}") }
                        AppText(calendarIssueGuidance(record, personal, today), style = MaterialTheme.typography.bodySmall)
                        if (personal.status == CalendarPurchaseStatus.UNAVAILABLE) TextButton(enabled = !busy, onClick = reloadHistory) {
                            AppText("Retry loading purchases")
                        }
                    } else {
                        Fact("Date", event.date.toString())
                        Fact("Broker", event.record.text("broker").ifBlank { "—" })
                        AppText(if (event.category == CalendarCategory.COUPON) "${localized(event.record.text("scheduleConfidence").ifBlank { "Estimated" })} schedule · Expected net coupon; confirm receipt with your broker."
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
