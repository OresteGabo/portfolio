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
        val oldLanguage = AppLanguage.preference
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val downloads = LinkedBlockingQueue<String>()
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
                    val body = when {
                        path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"document-library-test","email":"demo@example.test","isOwner":true}}"""
                        path.endsWith("/documents") -> fixtures
                        path.contains("/documents/") -> "%PDF-1.4\n%%EOF"
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
            override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean {
                assertTrue(bytes.decodeToString().startsWith("%PDF-")); downloads.add(fileName); return true
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
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(instrumentation.targetContext.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        try {
            prefs.edit().putString("origin", origin).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: PortfolioModel
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() })
                    activity.viewModelStore.put("document-library-test", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        CompositionLocalProvider(LocalNativeActions provides actions) { InvestmentApp(model) }
                        NativePrivacyCover(false)
                    }
                }
                await("Documents"); click("Documents")
                await("October Treasury Bond"); await("October 2026"); await("Logo: BNR")
                screenshot("document-library-en.png")
                click("Download October Treasury Bond")
                assertEquals("bnr-confirmation.pdf", downloads.poll(5, TimeUnit.SECONDS))
                assertFalse("Download must not expand the card", nodes().any { it.stateDescription?.toString() == "Expanded" })
                scenario.onActivity { model.message = null }
                click("October Treasury Bond")
                assertTrue("Tapping the card expands its details", nodes().any { it.stateDescription?.toString() == "Expanded" })
                click("Share contract")
                await("Bank of Kigali share purchase"); await("Logo: Bank of Kigali")
                assertNull("Category filter excludes bonds", find("October Treasury Bond"))
                screenshot("document-library-shares.png")
                scenario.onActivity { AppLanguage.select("fr") }
                await("Fichiers enregistrés"); await("Logo : Bank of Kigali")
                screenshot("document-library-fr.png")
            }
        } finally {
            AppLanguage.select(oldLanguage)
            prefs.edit().apply { if (oldOrigin == null) remove("origin") else putString("origin", oldOrigin) }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
