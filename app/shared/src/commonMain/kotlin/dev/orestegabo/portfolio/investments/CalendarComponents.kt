package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.design.StructColors
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
private fun SubscriptionStatusBadge(status: SubscriptionStatus) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val color = when (status) {
        SubscriptionStatus.OPEN -> StructColors.Calendar.open.resolve(dark)
        SubscriptionStatus.CLOSING_TODAY -> StructColors.Calendar.closingToday.resolve(dark)
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
internal fun SubscriptionRangeHeader(range: CalendarSubscriptionRange, today: LocalDate, couponRate: Double?,
    reminder: @Composable () -> Unit = {}, choose: () -> Unit) {
    val accessibleRate = couponRate?.let { privateFinancialValue(percent(it)) }
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(onClick = choose).semantics(mergeDescendants = true) {
        contentDescription = "${localized(range.record.text("title"))}, ${localized(range.statusOn(today).label)}. " +
            localized("subscriptions open ${range.start.calendarLabel()}, close ${range.end.calendarLabel()}") +
            (accessibleRate?.let { ", ${localized("Annual coupon rate")} $it" } ?: "")
        role = Role.Button
        onClick { choose(); true }
    },
        shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = .4f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SubscriptionStatusBadge(range.statusOn(today))
                reminder()
            }
            AppText(range.record.text("title"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            couponRate?.let { FinancialText("${localized("Annual coupon rate")} · ${percent(it)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
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
internal fun CalendarDay(
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
        !currentMonth -> colors.onSurfaceVariant
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
internal fun CalendarEventTile(event: InvestmentCalendarEvent, expanded: Boolean, purchases: List<JsonRecord>, documents: List<JsonRecord>,
    historyComplete: Boolean, busy: Boolean, today: LocalDate, receipt: JsonRecord?, receiptsAvailable: Boolean, reloadHistory: () -> Unit, toggle: () -> Unit) {
    val personal = if (event.category == CalendarCategory.ISSUANCE) calendarIssuePurchases(event.record, purchases, documents, historyComplete) else null
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            ListItem(
                headlineContent = { AppText(event.title, fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppText(if (event.category == CalendarCategory.COUPON && event.date <= today)
                            couponReceiptState(event.date, today, receiptsAvailable, receipt).label else event.label,
                            color = event.category.color())
                        if (personal != null && personal.status != CalendarPurchaseStatus.NONE) {
                            AppText(personal.status.label, style = MaterialTheme.typography.labelMedium,
                                color = if (personal.status == CalendarPurchaseStatus.UNAVAILABLE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                        event.amount?.let { amount ->
                            val value = money(amount, event.record.text("currency"))
                            FinancialText(if (event.category == CalendarCategory.COUPON) "${localized("Expected net coupon")} · $value" else value, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                leadingContent = { AppIcon(when (event.category) {
                    CalendarCategory.ISSUANCE -> Icons.Outlined.AccountBalance
                    CalendarCategory.COUPON -> Icons.Outlined.Payments
                    CalendarCategory.MATURITY -> Icons.Outlined.EventAvailable
                }, null, tint = event.category.color()) },
                trailingContent = { AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(role = Role.Button, onClick = toggle)
                    .semantics { stateDescription = localized(if (expanded) "Expanded" else "Collapsed") },
            )
            AppAnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    if (NativeCalendarBridge.available) {
                        TextButton(onClick = {
                            val draft = phoneCalendarEvent(event)
                            NativeCalendarBridge.present(draft.date, draft.title, draft.notes)
                        }) {
                            AppIcon(Icons.Outlined.EventAvailable, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            AppText("Add to phone calendar")
                        }
                        AppText("Review the event and choose alerts in your calendar app. Dates will not update automatically.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                        if (event.category == CalendarCategory.COUPON && couponReceiptState(event.date, today, receiptsAvailable, receipt) in listOf(CouponReceiptState.CONFIRMED, CouponReceiptState.AUTOMATIC))
                            recordedCouponAmount(receipt, event.record.text("currency"))?.let { amount ->
                                Fact("Recorded receipt amount", money(amount, event.record.text("currency")))
                            }
                        if (event.category == CalendarCategory.COUPON && couponReceiptState(event.date, today, receiptsAvailable, receipt) == CouponReceiptState.AUTOMATIC)
                            AppText("Automatic markings follow your settings; they do not verify payment into your account.", style = MaterialTheme.typography.bodySmall)
                        AppText(if (event.category == CalendarCategory.COUPON) "${localized(event.record.text("scheduleConfidence").ifBlank { "Estimated" })} schedule · Expected net coupon; confirm receipt with your broker."
                            else "Expected principal repayment; confirm receipt with your broker.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable internal fun CalendarCategory.color(): Color = when (this) {
    CalendarCategory.ISSUANCE -> MaterialTheme.colorScheme.primary
    CalendarCategory.COUPON -> StructColors.Calendar.coupon.resolve(MaterialTheme.colorScheme.surface.luminance() < .5f)
    CalendarCategory.MATURITY -> StructColors.Calendar.maturity.resolve(MaterialTheme.colorScheme.surface.luminance() < .5f)
}
@Composable internal fun EventDot(color: Color, size: Int = 6) {
    Canvas(Modifier.size(size.dp)) { drawCircle(color) }
}
