package dev.orestegabo.portfolio

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import dev.orestegabo.portfolio.investments.AppLanguage
import dev.orestegabo.portfolio.investments.NativeCalendarBridge
import java.time.LocalDate
import java.time.ZoneOffset

internal fun calendarInsertIntent(date: String, title: String, notes: String): Intent {
    require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    val day = LocalDate.parse(date)
    require(day.year > 0 && title.isNotBlank())
    return Intent(Intent.ACTION_INSERT)
        .setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.item/event")
        .putExtra(CalendarContract.Events.TITLE, title.take(180))
        .putExtra(CalendarContract.Events.DESCRIPTION, notes.take(1000))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        // Android Calendar's all-day intent contract uses UTC civil dates.
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
}

internal fun installCalendarExport(activity: Activity) {
    val host = java.lang.ref.WeakReference(activity)
    NativeCalendarBridge.install { date, title, notes ->
        val current = host.get()
        if (current != null && !current.isFinishing && !current.isDestroyed) {
            try {
                current.startActivity(calendarInsertIntent(date,title,notes))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(current, if (AppLanguage.code == "fr") "Installez une application de calendrier pour ajouter cet événement."
                    else "Install a calendar app to add this event.", Toast.LENGTH_LONG).show()
            } catch (_: IllegalArgumentException) {
                Toast.makeText(current, if (AppLanguage.code == "fr") "Impossible d’ouvrir cet événement."
                    else "Could not open this event.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
