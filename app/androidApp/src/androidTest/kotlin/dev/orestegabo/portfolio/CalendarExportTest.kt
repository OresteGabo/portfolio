package dev.orestegabo.portfolio

import android.content.Intent
import android.provider.CalendarContract
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CalendarExportTest {
    @Test fun allDayCivilDateAndExclusiveEndDoNotDependOnPhoneTimeZone() {
        val intent = calendarInsertIntent("2028-02-29", "Struct Capital · Expected coupon payment", "Indicative date")
        val start = LocalDate.parse("2028-02-29").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("vnd.android.cursor.item/event", intent.type)
        assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals(start, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(start + 86_400_000, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1))
        assertFalse(intent.hasExtra(CalendarContract.Events.CALENDAR_ID))
        for (date in listOf("2027-02-29", "2028-2-29", "not-a-date")) {
            try { calendarInsertIntent(date, "Test", ""); fail("Invalid date accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: java.time.format.DateTimeParseException) { }
        }
    }
}
