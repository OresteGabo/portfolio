package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import org.junit.Assert.*
import org.junit.Test

/** Calendar -> actual Android PDF reader -> explicit sharing, using synthetic data only. */
class CalendarApplicationFormTest {
    @Test fun calendarPreviewsTheSharedFormBeforeExplicitSharingAndRestoresTheMonth() {
        val i = InstrumentationRegistry.getInstrumentation()
        val prefs = i.targetContext.getSharedPreferences("connection", 0)
        val hadOnboarding = prefs.contains("onboardingCompleted")
        val oldOnboarding = prefs.getBoolean("onboardingCompleted", false)
        val oldLanguage = AppLanguage.preference
        val marketDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
        val today = LocalDate(marketDate.year, marketDate.monthValue, marketDate.dayOfMonth)
        val month = LocalDate(today.year, today.month, 1)
        val closing = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        val issue = JsonRecord().put("id", "synthetic-application").put("title", "Synthetic five-year issuance")
            .put("tenorYears", 5).put("announcementDate", month.toString())
            .put("openBookDate", closing.minus(DatePeriod(days = 2)).toString()).put("closingBookDate", closing.toString())
            .put("settlementDate", closing.plus(DatePeriod(days = 2)).toString())
            .put("maturityDate", closing.plus(DatePeriod(years = 5)).toString())
        val form = JsonRecord().put("id", "shared-application").put("label", "Bond application form")
            .put("category", "Bond application").put("visibility", "shared")
            .put("originalFileName", "synthetic-application.pdf").put("downloadUrl", "/api/bonds/documents/shared-application")
            .put("applicationForm", JsonRecord(issue.toString()).put("calendarId", issue.text("id")))
        val pdf = readerFixturePdf(pageCount = 1)
        val shares = AtomicInteger()
        val downloads = AtomicInteger()
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://android-forms.example.test"
            override fun writeOrigin(origin: String) {}
            override fun readOnboardingCompleted() = true
            override fun readLanguage() = "en"
            override fun readScreenCaptureAllowed() = true
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = error("Share explicitly")
            override suspend fun sharePdf(fileName: String, bytes: ByteArray): Boolean {
                assertEquals("synthetic-application.pdf", fileName)
                assertArrayEquals(pdf, bytes)
                shares.incrementAndGet()
                return true
            }
            override fun openUrl(url: String) = error("Keep the PDF inside the app")
            override fun shareText(text: String) = Unit
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No import in the calendar reader journey")
            override fun cancel() {}
            override fun destroy() {}
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            if (android.os.Build.VERSION.SDK_INT >= 33) i.uiAutomation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo) {
                if (n.isVisibleToUser) result += n
                for (index in 0 until n.childCount) n.getChild(index)?.let(::visit)
            }
            i.uiAutomation.rootInActiveWindow?.let(::visit)
            return result
        }
        fun find(text: String) = nodes().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
        fun await(text: String) {
            val until = android.os.SystemClock.uptimeMillis() + 12_000
            while (android.os.SystemClock.uptimeMillis() < until) {
                if (find(text) != null) return
                Thread.sleep(100)
            }
            throw AssertionError("Missing calendar/PDF control: $text; visible: ${nodes().map { it.text ?: it.contentDescription }}")
        }
        fun click(text: String) {
            repeat(16) {
                if (find(text) == null) {
                    nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    i.waitForIdleSync(); Thread.sleep(100)
                }
            }
            await(text)
            var n = find(text)
            while (n != null) {
                if (n.isClickable) { assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); i.waitForIdleSync(); return }
                n = n.parent
            }
            throw AssertionError("Control has no click action: $text")
        }
        try {
            prefs.edit().putBoolean("onboardingCompleted", true).commit()
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scenario ->
                lateinit var window: android.view.Window
                scenario.onActivity { host ->
                    window = host.window
                    val model = PortfolioModel(settings, assets = { name ->
                        (if (name == "issuance-calendar.json") JsonList(listOf(issue)).toString() else "[]").encodeToByteArray()
                    }, apiFactory = { origin -> PortfolioApi(origin, MockEngine { request ->
                        if (request.url.encodedPath == "/api/bonds/documents/shared-application") {
                            downloads.incrementAndGet()
                            respond(pdf, headers = headersOf(HttpHeaders.ContentType, "application/pdf"))
                        } else {
                            val response = when (request.url.encodedPath) {
                                "/api/bonds/auth/session" -> """{"authenticated":true,"user":{"id":"synthetic-forms-user","isOwner":false}}"""
                                "/api/bonds/documents" -> JsonRecord().put("documents", JsonList(listOf(form))).toString()
                                "/api/bonds/application-forms" -> JsonRecord().put("forms", JsonList(listOf(form))).toString()
                                else -> "{}"
                            }
                            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                    }) })
                    host.viewModelStore.put("calendar-application-test", model)
                    AppLanguage.initialize(settings)
                    host.setContent {
                        CompositionLocalProvider(LocalNativeActions provides actions, LocalImportEngine provides engine) {
                            InvestmentApp(model)
                        }
                    }
                }
                await("Calendar"); click("Calendar"); click("Open application form")
                await("Share PDF"); await("1 / 1")
                assertEquals("Opening must not share automatically", 0, shares.get())
                assertEquals(1, downloads.get())
                readerFixtureScreenshot(window, File(i.targetContext.filesDir, "calendar-application-preview.png"))
                click("Share PDF")
                val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                while (shares.get() == 0 && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
                assertEquals(1, shares.get())
                assertEquals("Sharing reuses the loaded original", 1, downloads.get())
                click("Back"); await("Open application form")
                assertNull(find("Upload application form"))
                readerFixtureScreenshot(window, File(i.targetContext.filesDir, "calendar-application-return.png"))
            }
        } finally {
            prefs.edit().apply {
                if (hadOnboarding) putBoolean("onboardingCompleted", oldOnboarding) else remove("onboardingCompleted")
            }.commit()
            AppLanguage.select(oldLanguage)
        }
    }
}
