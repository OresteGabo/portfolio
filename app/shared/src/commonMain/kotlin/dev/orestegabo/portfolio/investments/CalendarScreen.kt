package dev.orestegabo.portfolio.investments

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

@Composable
fun CalendarScreen(model: PortfolioModel, initialDate: LocalDate? = null, addTemporaryPurchase: (JsonRecord) -> Unit) {
    val reminderToast = remember { SnackbarHostState() }
    RefreshCapturedUi(reminderToast.currentSnackbarData)
    LaunchedEffect(model) {
        snapshotFlow { model.bondReminderFeedback }.filterNotNull().collectLatest { text ->
            model.dismissBondReminderFeedback()
            reminderToast.showSnackbar(localized(text), duration = SnackbarDuration.Short)
        }
    }
    val reducedMotion = LocalReducedMotion.current
    val selectionFeedback = LocalSelectionFeedback.current
    val today = rememberBondCalendarToday()
    var selectedValue by rememberSaveable(initialDate) { mutableStateOf((initialDate ?: today).toString()) }
    var followingToday by rememberSaveable(initialDate) { mutableStateOf(initialDate == null) }
    LaunchedEffect(today) { if (followingToday) selectedValue = today.toString() }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val selected = calendarDate(selectedValue) ?: today
    var historyRequested by remember(model.user?.text("id")) { mutableStateOf(false) }
    var formsRequested by remember(model.user?.text("id")) { mutableStateOf(false) }
    LaunchedEffect(model.busy, model.historyComplete) {
        if (!model.busy && !model.historyComplete && !historyRequested) {
            historyRequested = true
            model.loadInvestmentHistory()
        } else if (!model.busy && !model.isGuest && !formsRequested) {
            formsRequested = true
            model.refreshBondApplicationForms()
        }
    }
    LaunchedEffect(model.user?.text("id")) { model.loadBondReminders() }
    val events = remember(model.calendar, model.portfolio) { investmentCalendarEvents(model.calendar, model.portfolio) }
    val byDate = remember(events) { events.groupBy { it.date } }
    val ranges = remember(model.calendar) { calendarSubscriptionRanges(model.calendar) }
    val monthEvents = calendarMonthEvents(events, selected)
    val next = events.firstOrNull { it.date > selected && (it.date.year != selected.year || it.date.month != selected.month) }
    fun select(date: LocalDate) {
        if (date != selected) selectionFeedback()
        followingToday = date == today
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
                                SubscriptionRangeHeader(range, today, calendarCouponRate(range.record, model.documents, model.sharedCouponTerms),
                                    reminder = { if (!model.isGuest) BondMonthReminderButton(model, selected, today) }) { select(range.start) }

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
        if (!model.isGuest) {
            items(subscriptionRangesInMonth(ranges, selected), key = { "application:${it.id}" }) { range ->
                BondApplicationFormCard(model, range.record, today, addTemporaryPurchase)
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
                    receipt = model.couponReceipt(event.record.text("id"), event.date.toString()), receiptsAvailable = model.couponTrackingAvailable,
                    reloadHistory = { model.loadInvestmentHistory() }, toggle = { expanded = if (expanded == event.id) null else event.id })
            }
        }
    }
    SnackbarHost(reminderToast, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
    }

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



@Composable
internal fun rememberBondCalendarToday(): LocalDate {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val today by produceState(bondSubscriptionDate(Clock.System.now()), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = bondSubscriptionDate(Clock.System.now())
                delay(30_000)
            }
        }
    }
    return today
}
