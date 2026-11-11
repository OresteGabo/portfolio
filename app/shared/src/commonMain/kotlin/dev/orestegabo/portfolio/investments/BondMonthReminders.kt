package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

internal fun validBondReminderMonth(month: String) = month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))
internal fun bondReminderMonth(date: LocalDate) = date.toString().take(7)

@Composable
internal fun BondMonthReminderButton(model: PortfolioModel, selected: LocalDate, today: LocalDate) {
    val month = bondReminderMonth(selected)
    val active = month in model.bondReminderMonths
    val upcoming = model.calendar.any {
        val close = calendarDate(it.text("closingBookDate"))
        close != null && bondReminderMonth(close) == month && close >= today
    }
    if (!upcoming && !active) return
    val busy = model.bondRemindersLoading || model.bondReminderSaving != null
    RefreshCapturedUi(month, active, busy)
    val colors = MaterialTheme.colorScheme
    val label = localized(if(active) "Remove reminder for {0}" else "Set reminder for {0}")
        .replace("{0}", "${selected.monthLabel()} ${selected.year}")
    AssistChip(
        onClick = {
            if(model.bondRemindersLoaded) model.setBondMonthReminder(month, !active)
            else model.loadBondReminders()
        },
        label = { AppText(if(active) "Reminder set" else "Set reminder", style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            if(busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else AppIcon(
                if (active) Icons.Default.Check else Icons.Outlined.NotificationAdd,
                null,
                Modifier.size(16.dp)
            )
        },
        enabled = !busy && !model.locked,
        modifier = Modifier.semantics { contentDescription = label; stateDescription = localized(if(active) "Reminder on" else "Reminder off") },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if(active) colors.primaryContainer else colors.surface,
            labelColor = colors.primary,
            leadingIconContentColor = colors.primary),
    )
}
