package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Actual Calendar -> annual ledger -> existing bond page, with synthetic account data only. */
@SdkSuppress(minSdkVersion = 33)
class YearlyCouponsNavigationTest {
    @Test fun yearlyListPaginatesOpensBondsAndPreservesCalendarState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val previousOrigin = prefs.getString("origin", null)
        val hadIntroduction = prefs.contains("onboardingCompleted")
        val previousIntroduction = prefs.getBoolean("onboardingCompleted", false)
        val previousLanguage = AppLanguage.preference
        val marketDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
        val today = kotlinx.datetime.LocalDate(marketDate.year, marketDate.monthValue, marketDate.dayOfMonth)
        val futureOnly = InstrumentationRegistry.getArguments().getString("futureOnly") == "true"
        val year = today.year + if (futureOnly) 1 else 0
        val dates = listOf("$year-01-01", "$year-07-01", "${year + 1}-01-01", "${year + 1}-07-01")
        val purchases = (1..7).map { index -> JsonRecord().put("id", "year-bond-$index").put("bondName", "Treasury bond $index")
            .put("issuer", "Government of Rwanda").put("isin", "SYNTHETIC-$index").put("status", "active").put("currency", "RWF")
            .put("purchaseDate", "${today.year - 1}-01-01").put("maturityDate", "${year + 1}-07-01")
            .put("faceValue", 1000000).put("couponRate", .12).put("withholdingTaxRate", .05).put("couponFrequency", 2)
            .put("couponDates", JsonList(dates)).put("scheduleConfidence", "confirmed") }
        val receipts = listOf("manual", "automatic").mapIndexed { index, method -> JsonRecord()
            .put("purchaseId", "year-bond-${index + 1}").put("dueDate", "$year-01-01").put("method", method)
            .put("received", true).put("currency", "RWF").put("amount", if (index == 0) 56000 else 57000) }
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val couponWrites = AtomicInteger()
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val start = reader.readLine().orEmpty().split(' ')
                val path = start.getOrNull(1).orEmpty()
                while (!reader.readLine().isNullOrEmpty()) { }
                if (start.firstOrNull() == "POST" && path.endsWith("/notifications/coupons")) couponWrites.incrementAndGet()
                val body = when {
                    path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"year-coupons-test","email":"fixture@example.test","isOwner":true}}"""
                    path.endsWith("/purchases") -> JsonRecord().put("purchases", JsonList(purchases)).toString()
                    path.endsWith("/equities") -> """{"holdings":[]}"""
                    path.endsWith("/notifications/preferences") -> """{"configured":true,"categories":{},"autoCoupon":false}"""
                    path.endsWith("/notifications/coupons") -> JsonRecord().put("receipts", JsonList(receipts)).toString()
                    path.endsWith("/notifications") -> """{"notifications":[],"serverDate":"$today"}"""
                    else -> "{}"
                }.encodeToByteArray()
                client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray() + body)
            } } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun readOnboardingCompleted() = true
            override fun readScreenCaptureAllowed() = true
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            instrumentation.uiAutomation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                if (node.isVisibleToUser) result += node
                for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
            return result
        }
        fun find(text: String) = nodes().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
        fun await(text: String): AccessibilityNodeInfo {
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            while (android.os.SystemClock.uptimeMillis() < until) { find(text)?.let { return it }; Thread.sleep(100) }
            File(instrumentation.targetContext.filesDir, "year-coupons-failure.txt").writeText(nodes().joinToString("\n") { "${it.text} | ${it.contentDescription}" })
            throw AssertionError("Missing yearly coupon control: $text")
        }
        fun scrollTo(text: String) {
            if (find(text) != null) return
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) repeat(24) {
                if (find(text) != null) return
                nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }?.performAction(direction)
                instrumentation.waitForIdleSync(); Thread.sleep(100)
            }
            await(text)
        }
        fun click(text: String) {
            scrollTo(text)
            var node: AccessibilityNodeInfo? = await(text)
            while (node != null) {
                if (node.isClickable) {
                    assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    instrumentation.waitForIdleSync(); Thread.sleep(300); return
                }
                node = node.parent
            }
            throw AssertionError("No accessible action for $text")
        }
        try {
            prefs.edit().putString("origin", origin).putBoolean("onboardingCompleted", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: PortfolioModel
                lateinit var window: android.view.Window
                scenario.onActivity { activity ->
                    window = activity.window; window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() })
                    activity.viewModelStore.put("year-coupons-test", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        CompositionLocalProvider(LocalNativeActions provides rememberNativeActions()) { InvestmentApp(model) }
                        NativePrivacyCover(false)
                    }
                }
                await("Calendar"); click("Calendar"); await("View yearly coupons")
                click("Previous month")
                val previousMonth = shiftCalendarMonth(today, -1).let { "${it.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${it.year}" }
                await(previousMonth)
                readerFixtureScreenshot(window, File(instrumentation.targetContext.filesDir, "year-coupons-calendar.png"))
                click("View yearly coupons")
                if (futureOnly) {
                    await("No coupons due in ${today.year}")
                    await("View $year coupons")
                    readerFixtureScreenshot(window, File(instrumentation.targetContext.filesDir, "year-coupons-empty-preview.png"))
                    scrollTo("Treasury bond 1"); await("RWF 57,000")
                    readerFixtureScreenshot(window, File(instrumentation.targetContext.filesDir, "year-coupons-next-preview.png"))
                    click("View $year coupons")
                }
                await("Scheduled net coupons"); await(year.toString())
                scrollTo(formatNumber(798000.0)); scrollTo("Confirmed by you"); scrollTo("Marked automatically"); scrollTo(year.toString())
                assertNull("Child page hides bottom navigation", find("Markets"))
                readerFixtureScreenshot(window, File(instrumentation.targetContext.filesDir, "year-coupons-overview.png"))
                scrollTo("Load more coupons"); await("12 of 14 coupons"); click("Load more coupons")
                scrollTo("14 of 14 coupons"); await("14 of 14 coupons")
                val bondLink = "View bond details: Treasury bond 7 · 1 Jul $year"
                click(bondLink); await("Bond details"); await("Treasury bond 7")
                click("Back"); scrollTo("14 of 14 coupons"); await("14 of 14 coupons")
                scrollTo("Next year · ${year + 1}"); click("Next year · ${year + 1}"); await((year + 1).toString()); scrollTo("Load more coupons"); await("12 of 14 coupons")
                scrollTo("Next year"); click("Next year"); await("No coupons due in ${year + 2}")
                click("This year")
                if (futureOnly) { await("No coupons due in ${today.year}"); click("View $year coupons") }
                await(year.toString()); await("Scheduled net coupons")
                scenario.onActivity { model.toggleNumberVisibility() }
                await("Financial numbers hidden"); assertNull(find(formatNumber(798000.0)))
                readerFixtureScreenshot(window, File(instrumentation.targetContext.filesDir, "year-coupons-hidden.png"))
                scenario.onActivity { model.toggleNumberVisibility(); AppLanguage.select("fr") }
                await("Coupons nets prévus")
                readerFixtureScreenshot(window, File(instrumentation.targetContext.filesDir, "year-coupons-fr.png"))
                scenario.onActivity { AppLanguage.select("en") }
                click("Back"); await(previousMonth)
                click("Home"); click("Calendar")
                await("${today.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${today.year}")
                assertEquals("Annual navigation is read-only", 0, couponWrites.get())
            }
        } finally {
            server.close(); worker.shutdownNow()
            prefs.edit().apply {
                if (previousOrigin == null) remove("origin") else putString("origin", previousOrigin)
                if (hadIntroduction) putBoolean("onboardingCompleted", previousIntroduction) else remove("onboardingCompleted")
            }.commit()
            AppLanguage.select(previousLanguage)
        }
    }
}
