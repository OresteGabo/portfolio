package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.filters.SdkSuppress
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SdkSuppress(minSdkVersion = 33)
class DocumentsLibraryTest {
    @Test fun cardsGroupByMonthFilterExpandAndDownloadWithoutOpeningDetails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val oldOrigin = prefs.getString("origin", null)
        val oldIntroduction = prefs.getBoolean("onboardingCompleted", false)
        prefs.edit().putBoolean("onboardingCompleted", true).commit()
        val oldLanguage = AppLanguage.preference
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val downloads = LinkedBlockingQueue<String>()
        val bondDownloads = java.util.concurrent.atomic.AtomicInteger()
        val sourcePdf = readerFixturePdf()
        val shortPdf = readerFixturePdf(pageHeight = 200, pageCount = 1)
        var screenshotWindow: android.view.Window? = null
        val fixtures = """{"documents":[
            {"id":"october-bond","label":"October Treasury Bond","category":"Bond confirmation","documentDate":"2026-10-14","instrumentName":"15-year Treasury bond","issuer":"National Bank of Rwanda","originalFileName":"bnr-confirmation.pdf","downloadUrl":"/api/bonds/documents/october-bond","details":{"facts":[{"label":"Principal","value":"RWF 1,200,000"}]}},
            {"id":"share-contract","label":"Bank of Kigali share purchase","category":"Share contract","documentDate":"2026-09-24","issuer":"BK Capital","originalFileName":"share-contract.pdf","downloadUrl":"/api/bonds/documents/share-contract"},
            {"id":"statement","label":"Investment account statement","category":"Statement","documentDate":"2026-09-30","issuer":"BK Capital","originalFileName":"client-statement.pdf","downloadUrl":"/api/bonds/documents/statement"},
            {"id":"older-bond","label":"Treasury bond confirmation","category":"Bond confirmation","documentDate":"2025-12-10","issuer":"National Bank of Rwanda","originalFileName":"bond.pdf","downloadUrl":"/api/bonds/documents/older-bond"}
        ]}""".trimIndent()
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    if (path.endsWith("/documents/october-bond")) bondDownloads.incrementAndGet()
                    val body = if (path.endsWith("/documents/share-contract")) shortPdf else if (path.contains("/documents/")) sourcePdf else when {
                        path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"document-library-test","email":"demo@example.test","isOwner":true}}"""
                        path.endsWith("/documents") -> fixtures
                        path.endsWith("/purchases") -> """{"purchases":[{"id":"library-bond","confirmationDocumentId":"october-bond","bondName":"October Treasury Bond","issuer":"Government of Rwanda","isin":"RWSYNTHETIC123/MN","status":"active","currency":"RWF","faceValue":1200000,"couponRate":0.12,"withholdingTaxRate":0.05,"couponFrequency":2,"purchaseDate":"2026-10-23","firstCouponDate":"2027-04-23","maturityDate":"2046-10-23"}]}"""
                        path.endsWith("/equities") -> """{"holdings":[{"id":"library-share","contractDocumentId":"share-contract","ticker":"BOK","securityName":"Bank of Kigali","currency":"RWF","status":"active","tradeDate":"2026-09-24","shares":400,"netAmountPayable":36000}]}"""
                        else -> "{}"
                    }.toByteArray()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
            } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun writeLanguage(language: String) = Unit
            override fun readScreenCaptureAllowed() = true
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean = error("Use native PDF sharing")
            override suspend fun sharePdf(fileName: String, bytes: ByteArray): Boolean {
                assertArrayEquals(sourcePdf, bytes); downloads.add(fileName); return true
            }
            override fun openUrl(url: String) = Unit
            override fun shareText(text: String) = Unit
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                if (node.isVisibleToUser) result.add(node)
                for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
            return result
        }
        fun find(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
        fun await(label: String): AccessibilityNodeInfo {
            val until = System.currentTimeMillis() + 6000
            while (System.currentTimeMillis() < until) { find(label)?.let { return it }; Thread.sleep(100) }
            File(instrumentation.targetContext.filesDir,"document-test-state.txt").writeText(nodes().joinToString("\n") { "${it.text} | ${it.contentDescription} | scroll=${it.isScrollable}" })
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(instrumentation.targetContext.filesDir,"document-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
            throw AssertionError("Missing document control: $label")
        }
        fun scrollTo(label: String) {
            if (find(label) != null) return
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                repeat(16) {
                    if (find(label) != null) return
                    nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { node ->
                        Rect().also(node::getBoundsInScreen).height()
                    }?.performAction(direction)
                    instrumentation.waitForIdleSync(); Thread.sleep(100)
                }
            }
            await(label)
        }
        fun click(label: String) {
            if (find(label) == null) scrollTo(label)
            instrumentation.waitForIdleSync(); Thread.sleep(300)
            // Exercise the accessible action itself; labels can be descendants of merged controls.
            var control: AccessibilityNodeInfo? = await(label)
            while (control != null) {
                if (control.isClickable) {
                    assertTrue("Accessible click: $label", control.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    instrumentation.waitForIdleSync(); Thread.sleep(300)
                    return
                }
                control = control.parent
            }
            val bounds = Rect().also(await(label)::getBoundsInScreen)
            val down = android.os.SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
            }
            instrumentation.waitForIdleSync(); Thread.sleep(300)
        }
        fun screenshot(name: String) {
            screenshotWindow?.let { readerFixtureScreenshot(it, File(instrumentation.targetContext.filesDir, name)) }
        }
        try {
            prefs.edit().putString("origin", origin).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: PortfolioModel
                scenario.onActivity { activity ->
                    screenshotWindow = activity.window
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() })
                    activity.viewModelStore.put("document-library-test", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        CompositionLocalProvider(LocalNativeActions provides actions) { InvestmentApp(model) }
                        NativePrivacyCover(false)
                    }
                }
                await("Documents"); click("Documents"); await("Search documents"); await("Add document")
                scrollTo("October Treasury Bond"); await("October 2026"); await("Logo: BNR")
                assertNull(find("Upload PDF")); assertNull(find("Import")); await("Chat")
                val readyDeadline = System.currentTimeMillis() + 10_000
                while (model.busy && System.currentTimeMillis() < readyDeadline) Thread.sleep(50)
                assertFalse("Wait for document history before sharing", model.busy)
                instrumentation.waitForIdleSync()
                scrollTo("PDF preview: October Treasury Bond"); await("First page")
                screenshot("document-library-preview.png")
                val requestsBeforePrivacy = bondDownloads.get()
                scenario.onActivity { model.toggleNumberVisibility() }
                await("PDF hidden for privacy")
                assertNull("Raw PDF content must disappear in anonymous mode", find("PDF preview: October Treasury Bond"))
                screenshot("document-library-preview-hidden.png")
                scenario.onActivity { model.toggleNumberVisibility() }
                await("PDF preview: October Treasury Bond")
                assertEquals("Privacy toggle reuses the session-memory preview", requestsBeforePrivacy, bondDownloads.get())
                screenshot("document-library-en.png")
                click("Share PDF: October Treasury Bond")
                assertEquals("Share error=${model.error}, busy=${model.busy}", "bnr-confirmation.pdf", downloads.poll(5, TimeUnit.SECONDS))
                assertFalse("Sharing must not expand the card", nodes().any { it.stateDescription?.toString() == "Expanded" })
                scenario.onActivity { model.message = null }
                click("PDF preview: October Treasury Bond")
                await("PDF page 1"); await("1 / 2")
                fun assertPageFillsReaderWidth() {
                    val page = Rect().also(await("PDF page 1")::getBoundsInScreen)
                    var readerWidth = 0
                    scenario.onActivity { readerWidth = it.window.decorView.width }
                    assertTrue("PDF must fill the reader width: ${page.width()} / $readerWidth", kotlin.math.abs(page.width() - readerWidth) <= 2)
                }
                assertPageFillsReaderWidth()
                screenshot("document-pdf-page-one.png")
                click("Zoom in"); click("Fit to width"); assertPageFillsReaderWidth()
                click("Next page"); await("2 / 2"); await("PDF page 2")
                screenshot("document-pdf-page-two.png")
                click("Previous page"); await("1 / 2")
                click("Share PDF")
                assertEquals("bnr-confirmation.pdf", downloads.poll(5, TimeUnit.SECONDS))
                click("Back"); scrollTo("October Treasury Bond")
                click("Details: October Treasury Bond")
                assertTrue("Details remain available independently of PDF opening", nodes().any { it.stateDescription?.toString() == "Expanded" })
                assertNull("Compact summary must not dump financial fact rows", find("Principal"))
                scrollTo("Document details"); screenshot("document-compact-bond.png")
                scrollTo("View bond details"); click("View bond details"); await("Bond details"); await("Bond principal")
                click("Back"); scrollTo("October Treasury Bond")
                click("Document details"); await("File information")
                screenshot("document-full-details.png")
                scrollTo("Principal"); await("Principal")
                click("Back"); scrollTo("October Treasury Bond")
                scrollTo("Search documents"); click("Share contract")
                scrollTo("Bank of Kigali share purchase"); await("Logo: Bank of Kigali")
                click("Bank of Kigali share purchase"); await("PDF page 1"); await("1 / 1")
                val beforeZoom = Rect().also(await("PDF page 1")::getBoundsInScreen)
                click("Zoom in")
                val afterZoom = Rect().also(await("PDF page 1")::getBoundsInScreen)
                assertTrue("A short page must grow vertically when zoomed: ${beforeZoom.height()} -> ${afterZoom.height()}",
                    afterZoom.height() > beforeZoom.height() * 1.4f)
                repeat(5) { click("Zoom in") }
                val expandedPage = Rect().also(await("PDF page 1")::getBoundsInScreen)
                val controls = Rect().also(await("Fit to width")::getBoundsInScreen)
                assertTrue("Zoomed page must reach the bottom of the available reader", expandedPage.bottom >= controls.top - 100)
                screenshot("document-pdf-expanded-zoom.png")
                click("Fit to width")
                val resetPage = Rect().also(await("PDF page 1")::getBoundsInScreen)
                assertTrue("Fit to width restores the natural page height", kotlin.math.abs(resetPage.height() - beforeZoom.height()) <= 2)
                click("Back"); scrollTo("Bank of Kigali share purchase")
                assertNull("Returning from PDF preserves the category filter", find("October Treasury Bond"))
                assertNull("Category filter excludes bonds", find("October Treasury Bond"))
                screenshot("document-library-shares.png")
                click("Details: Bank of Kigali share purchase"); scrollTo("View share details"); screenshot("document-compact-share.png")
                click("View share details"); await("Share details"); await("Bank of Kigali")
                click("Back"); scrollTo("Bank of Kigali share purchase")
                scenario.onActivity { AppLanguage.select("fr") }
                await("Documents"); scrollTo("Logo : Bank of Kigali")
                screenshot("document-library-fr.png")
            }
        } finally {
            AppLanguage.select(oldLanguage)
            prefs.edit().apply { if (oldOrigin == null) remove("origin") else putString("origin", oldOrigin)
                putBoolean("onboardingCompleted", oldIntroduction)
            }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
