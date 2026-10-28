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
                    val body = if (path.endsWith("/documents/share-contract")) shortPdf else if (path.contains("/documents/")) sourcePdf else when {
                        path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"document-library-test","email":"demo@example.test","isOwner":true}}"""
                        path.endsWith("/documents") -> fixtures
                        path.endsWith("/purchases") -> """{"purchases":[]}"""
                        path.endsWith("/equities") -> """{"holdings":[]}"""
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
            throw AssertionError("Missing document control: $label")
        }
        fun click(label: String) {
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
                await("October Treasury Bond"); await("October 2026"); await("Logo: BNR")
                assertNull(find("Upload PDF")); assertNull(find("Import")); await("Chat")
                val readyDeadline = System.currentTimeMillis() + 10_000
                while (model.busy && System.currentTimeMillis() < readyDeadline) Thread.sleep(50)
                assertFalse("Wait for document history before sharing", model.busy)
                instrumentation.waitForIdleSync()
                screenshot("document-library-en.png")
                click("Share PDF: October Treasury Bond")
                assertEquals("Share error=${model.error}, busy=${model.busy}", "bnr-confirmation.pdf", downloads.poll(5, TimeUnit.SECONDS))
                assertFalse("Sharing must not expand the card", nodes().any { it.stateDescription?.toString() == "Expanded" })
                scenario.onActivity { model.message = null }
                click("October Treasury Bond")
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
                click("Back"); await("Search documents")
                click("Details")
                assertTrue("Details remain available independently of PDF opening", nodes().any { it.stateDescription?.toString() == "Expanded" })
                click("Share contract")
                await("Bank of Kigali share purchase"); await("Logo: Bank of Kigali")
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
                click("Back"); await("Bank of Kigali share purchase")
                assertNull("Returning from PDF preserves the category filter", find("October Treasury Bond"))
                assertNull("Category filter excludes bonds", find("October Treasury Bond"))
                screenshot("document-library-shares.png")
                scenario.onActivity { AppLanguage.select("fr") }
                await("Documents"); await("Logo : Bank of Kigali")
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
