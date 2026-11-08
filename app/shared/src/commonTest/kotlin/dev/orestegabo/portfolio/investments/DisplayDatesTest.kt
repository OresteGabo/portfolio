package dev.orestegabo.portfolio.investments

import kotlinx.datetime.TimeZone
import kotlin.test.*

class DisplayDatesTest {
    @Test fun datesRespectLanguageAndContext() {
        val old = AppLanguage.preference
        try {
            AppLanguage.select("en")
            assertEquals("8 Nov 2026", displayDate("2026-11-08"))
            assertEquals("Sunday, 8 November 2026", displayDate("2026-11-08", full = true))
            assertEquals("November 2026", displayDateValue("2026-11"))
            assertEquals("Monday, 9 November 2026 · 01:35", displayTimestamp("2026-11-08T23:35:00Z", TimeZone.of("Africa/Kigali")))
            AppLanguage.select("fr")
            assertEquals("8 nov 2026", displayDate("2026-11-08"))
            assertEquals("dimanche, 8 novembre 2026", displayDate("2026-11-08", full = true))
        } finally { AppLanguage.select(old) }
    }
    @Test fun invalidDatesAndNonDateContentStayIntact() {
        for (value in listOf("2026-02-29", "2026-13", "document-2026-11-08.pdf", "RW2026-11", "", "2026-11-08Tbad")) {
            assertEquals(value, displayDateValue(value))
        }
        assertEquals("29 Feb 2024", displayDate("2024-02-29"))
    }
    @Test fun appleHardwareNamesHaveAnHonestFallback() {
        assertEquals("iPhone 12 Pro", appleDeviceModel("iPhone13,3", "iPhone"))
        assertEquals("iPhone (iPhone99,9)", appleDeviceModel("iPhone99,9", "iPhone"))
    }
    @Test fun deviceDetailsSupportOlderRecordsAndDoNotAssumeAndroid() {
        assertEquals("iPhone / iPad", securityDeviceTitle(JsonRecord().put("platform", "ios")))
        assertEquals("Unknown device", securityDeviceTitle(JsonRecord()))
        val device = JsonRecord().put("deviceModel", "Google Pixel 8").put("osVersion", "Android 16").put("appVersion", "1.0 (42)")
        assertEquals("Google Pixel 8", securityDeviceTitle(device))
        assertEquals("Android 16 · Struct Capital 1.0 (42)", securityDeviceSoftware(device))
    }
}
