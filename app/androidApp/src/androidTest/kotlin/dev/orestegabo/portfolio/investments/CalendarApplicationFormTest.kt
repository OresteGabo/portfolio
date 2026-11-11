package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import io.ktor.http.content.OutgoingContent
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
    @Test fun calendarPreviewsTheSharedFormBeforeExplicitSharingAndRestoresTheMonth() = calendarJourney(parsedTerms = true)
    @Test fun calendarTemporaryPurchaseAutomaticallySelectsBondWithoutParsedFormTerms() = calendarJourney(parsedTerms = false)

    private fun calendarJourney(parsedTerms: Boolean) {
        val i = InstrumentationRegistry.getInstrumentation()
        val prefs = i.targetContext.getSharedPreferences("connection", 0)
        val hadOnboarding = prefs.contains("onboardingCompleted")
        val oldOnboarding = prefs.getBoolean("onboardingCompleted", false)
        val oldLanguage = AppLanguage.preference
        val marketDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
        val today = LocalDate(marketDate.year, marketDate.monthValue, marketDate.dayOfMonth)
        val month = LocalDate(today.year, today.month, 1)
        val reminderMonthLabel = marketDate.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " ${marketDate.year}"
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
        if (parsedTerms) form.put("bidTerms", JsonRecord(issue.toString()).put("bondName", "FXD SYNTHETIC 5YRS").put("issuanceDate", closing.toString()))
        val pdf = readerFixturePdf(pageCount = 1)
        val shares = AtomicInteger()
        val downloads = AtomicInteger()
        val bids = AtomicInteger()
        val reminderWrites = AtomicInteger()
        val reminderMonths = linkedSetOf<String>()
        var savedBid: JsonRecord? = null
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://android-forms.example.test"
            override fun writeOrigin(origin: String) {}
            override fun readOnboardingCompleted() = true
            override fun readLanguage() = "en"
            override fun readScreenCaptureAllowed() = true
            override fun readNumbersHidden() = true
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
        ExperimentalCaptureBackdrop.attach { true }
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
                            if(request.url.encodedPath.startsWith("/api/bonds/notifications/bond-reminders")) {
                                if(request.method == HttpMethod.Put) {
                                    val enabled=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).optBoolean("enabled")
                                    val key=request.url.encodedPath.substringAfterLast('/')
                                    if(enabled) reminderMonths+=key else reminderMonths-=key
                                    reminderWrites.incrementAndGet()
                                }
                                return@MockEngine respond(JsonRecord().put("months",JsonList(reminderMonths)).toString(), headers=headersOf(HttpHeaders.ContentType,"application/json"))
                            }
                            val response = when (request.url.encodedPath) {
                                "/api/bonds/auth/session" -> """{"authenticated":true,"user":{"id":"synthetic-forms-user","isOwner":false}}"""
                                "/api/bonds/application-forms/shared-application/bids" -> {
                                    val payload=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                    assertEquals(1000000.0,payload.optDouble("amount"),0.0)
                                    bids.incrementAndGet()
                                    savedBid=JsonRecord().put("id","synthetic-bid").put("bondName","FXD SYNTHETIC 5YRS").put("instrumentType","treasury").put("issuer","Government of Rwanda")
                                        .put("status","submitted").put("purchaseDate",today.toString()).put("settlementDate",issue.text("settlementDate")).put("maturityDate",issue.text("maturityDate"))
                                        .put("tenorYears",5).put("faceValue",1000000).put("pricePercent",100).put("feesPaid",0).put("accruedInterestPaid",0).put("couponRate",0).put("couponFrequency",2)
                                        .put("withholdingTaxRate",.05).put("scheduleConfidence","estimated").put("currency","RWF").put("market","primary").put("applicationFormDocumentId",form.text("id"))
                                    JsonRecord().put("purchase",savedBid).toString()
                                }
                                "/api/bonds/purchases" -> {
                                    if (request.method == HttpMethod.Post) {
                                        assertFalse(parsedTerms)
                                        val payload = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                        assertEquals(issue.text("title"), payload.text("bondName"))
                                        assertEquals(issue.text("settlementDate"), payload.text("settlementDate"))
                                        assertEquals("submitted", payload.text("status"))
                                        assertEquals(1000000.0, payload.optDouble("faceValue"), 0.0)
                                        savedBid = payload.put("id", "synthetic-bid")
                                        bids.incrementAndGet()
                                    }
                                    JsonRecord().put("purchases",JsonList(listOfNotNull(savedBid))).toString()
                                }
                                "/api/bonds/equities" -> """{"holdings":[]}"""
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
                        // Exercise the same redaction overlays used on iPhone while checking draft and saved privacy.
                        AndroidCaptureBackdropHost(true) {
                            CompositionLocalProvider(LocalNativeActions provides actions, LocalImportEngine provides engine) {
                                InvestmentApp(model)
                            }
                        }
                    }
                }
                await("Calendar"); click("Calendar")
                await("Set reminder for $reminderMonthLabel")
                val enabledDeadline=android.os.SystemClock.uptimeMillis()+5000
                while(find("Set reminder for $reminderMonthLabel")?.isEnabled != true && android.os.SystemClock.uptimeMillis()<enabledDeadline) Thread.sleep(50)
                click("Set reminder for $reminderMonthLabel")
                val reminderDeadline=android.os.SystemClock.uptimeMillis()+5000
                while(reminderWrites.get()<1 && android.os.SystemClock.uptimeMillis()<reminderDeadline) Thread.sleep(50)
                assertEquals(1,reminderWrites.get())
                assertTrue(month.toString().take(7) in reminderMonths)
                fun reminderIsOn() = find("Remove reminder for $reminderMonthLabel") != null
                val checkedDeadline=android.os.SystemClock.uptimeMillis()+5000
                while(!reminderIsOn() && android.os.SystemClock.uptimeMillis()<checkedDeadline) Thread.sleep(50)
                assertTrue("Saved reminder must display an active bell",reminderIsOn())
                await("Reminder saved. Phone alerts need setup.")
                assertNull(find("Remind me about bonds"))
                Thread.sleep(300) // Let the protected renderer finish its icon/toast transition.
                readerFixtureScreenshot(window, File(i.targetContext.filesDir, "calendar-month-reminder.png"))
                click("Open application form")
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
                click("Add temporary purchase")
                await(if (parsedTerms) "Bid amount (RWF)" else "Amount applied (RWF)")
                assertNull("Calendar has already chosen the bond", find("Choose bond"))
                assertNull(find("Choose a bond"))
                assertFalse("Amount selection must not open a keyboard", nodes().any { it.isEditable })
                assertNull(find("Hide amount")); assertNull(find("Show amount"))
                fun awaitAmount(value: String) {
                    val until = android.os.SystemClock.uptimeMillis() + 5_000
                    while (android.os.SystemClock.uptimeMillis() < until) {
                        val count = nodes().count { it.text?.toString() == value }
                        if (count >= if (value == "100,000") 3 else 1) return
                        Thread.sleep(100)
                    }
                    throw AssertionError("Formatted draft amount missing: $value")
                }
                awaitAmount("100,000")
                click("Increase amount"); awaitAmount("200,000")
                click("Decrease amount"); awaitAmount("100,000")
                repeat(9) { index -> click("Increase amount"); awaitAmount(formatNumber((index + 2) * 100000.0)) }
                awaitAmount("1,000,000")
                readerFixtureScreenshot(window, File(i.targetContext.filesDir, "calendar-amount-selector.png"))
                click(if (parsedTerms) "Save bid" else "Save pending purchase")
                await("Calendar");click(if (parsedTerms) "FXD SYNTHETIC 5YRS" else issue.text("title"))
                assertEquals(1,bids.get())
                await("Pending")
                assertFalse("Saved purchase must respect number hiding", nodes().any { it.text?.toString()?.contains("1,000,000") == true })
                readerFixtureScreenshot(window,File(i.targetContext.filesDir,"calendar-bid-home.png"))
            }
        } finally {
            ExperimentalCaptureBackdrop.detach()
            prefs.edit().apply {
                if (hadOnboarding) putBoolean("onboardingCompleted", oldOnboarding) else remove("onboardingCompleted")
            }.commit()
            AppLanguage.select(oldLanguage)
        }
    }
}
