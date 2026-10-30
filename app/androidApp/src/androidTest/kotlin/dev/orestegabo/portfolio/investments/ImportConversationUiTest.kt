package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic, account-scoped import journey. No real PDF, biometric prompt, account or recipient. */
class ImportConversationUiTest {
    @Test fun conversationReviewsRetriesPersistsAndLinksToTheRealLibraryAndPurchase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val previous = prefs.all.toMap()
        val language = AppLanguage.preference
        var screenshotWindow: android.view.Window? = null
        val sourcePdf = readerFixturePdf()
        val failRead = AtomicBoolean(true)
        val firstRead = CompletableDeferred<Unit>()
        val failSave = AtomicBoolean(true)
        val uploaded = AtomicBoolean(false)
        val saved = AtomicBoolean(false)
        val picks = AtomicInteger()
        val pickerOpens = AtomicInteger()
        val cancelNextPick = AtomicBoolean(true)
        val uploads = AtomicInteger()
        val writes = AtomicInteger()
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val fields = JsonRecord().put("instrumentType", "treasury").put("issuer", "Government of Rwanda").put("currency", "RWF")
            .put("bondName", "Test Treasury bond").put("isin", "RWTEST000001/MN").put("market", "primary")
            .put("purchaseDate", "2026-10-22").put("settlementDate", "2026-10-23").put("maturityDate", "2046-09-28")
            .put("firstCouponDate", "2027-04-23").put("tenorYears", 20).put("faceValue", 1_000_000)
            .put("pricePercent", 100).put("couponRate", .12).put("couponFrequency", 2).put("withholdingTaxRate", .05)
            .put("couponDates", JsonList(listOf("2027-04-23", "2046-09-28"))).put("scheduleConfidence", "confirmed")
            .put("feesPaid", 11000).put("accruedInterestPaid", 0)
        val review = JsonRecord().put("kind", "bond").put("fields", fields).put("missingFields", JsonList()).put("warnings", JsonList())
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val request = reader.readLine().orEmpty().split(' ')
                var length = 0
                while (true) {
                    val line = reader.readLine().orEmpty(); if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                }
                val payload = CharArray(length); var at = 0
                while (at < length) { val n = reader.read(payload, at, length - at); if (n < 0) break; at += n }
                val path = request.getOrNull(1).orEmpty()
                var status = "200 OK"
                val purchase = JsonRecord(fields.toString()).put("id", "pending-id").put("status", if (saved.get()) "active" else "submitted")
                if (saved.get()) purchase.put("confirmationDocumentId", "chat-doc").put("confirmationMethod", "document")
                val doc = JsonRecord().put("id", "chat-doc").put("label", "Bond confirmation").put("instrumentName", "Test Treasury bond")
                    .put("category", "Bond confirmation").put("issuer", "Government of Rwanda").put("documentDate", "2026-10-23")
                    .put("originalFileName", "receipt.pdf").put("downloadUrl", "/api/bonds/documents/chat-doc")
                val body = if (path.endsWith("/documents/chat-doc")) sourcePdf else when {
                    path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"chat-user","email":"demo@example.test","isOwner":false}}"""
                    path.endsWith("/mobile-overview") -> { status = "404 Not Found"; "{}" }
                    path.endsWith("/documents") && request.firstOrNull() == "POST" -> { uploads.incrementAndGet(); uploaded.set(true); """{"documentId":"chat-doc"}""" }
                    path.endsWith("/documents") -> JsonRecord().put("documents", JsonList(if (uploaded.get()) listOf(doc) else emptyList())).toString()
                    path.endsWith("/purchases/pending-id") && request.firstOrNull() == "PUT" -> {
                        writes.incrementAndGet()
                        val input = JsonRecord(String(payload))
                        if (input.text("confirmationDocumentId") != "chat-doc" || input.text("status") != "active") status = "400 Bad Request"
                        else if (failSave.get()) status = "503 Service Unavailable" else saved.set(true)
                        """{"purchase":{"id":"pending-id"}}"""
                    }
                    path.endsWith("/purchases/pending-id") -> JsonRecord().put("purchase", purchase).toString()
                    path.endsWith("/purchases") -> JsonRecord().put("purchases", JsonList(listOf(purchase))).toString()
                    path.endsWith("/equities") -> """{"holdings":[]}"""
                    else -> "{}"
                }.toByteArray()
                client.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
            } } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) {}
            override fun readScreenCaptureAllowed() = true
            override fun readLanguage() = "en"
            override fun readImportHistory(account: String) = prefs.getString("test-import:$account", null)
            override fun writeImportHistory(account: String, history: String) { prefs.edit().putString("test-import:$account", history).commit() }
        }
        val actions = object : NativeActions {
            override val pickedPdfName = "receipt.pdf"
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? {
                pickerOpens.incrementAndGet()
                if (cancelNextPick.getAndSet(false)) return null
                picks.incrementAndGet(); onSelected(); return sourcePdf
            }
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) {}
            override fun shareText(text: String) {}
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
                if (failRead.get()) { firstRead.await(); throw IllegalArgumentException("Could not read this PDF.") }
                return review
            }
            override fun cancel() {}
            override fun destroy() {}
        }
        fun nodes(visibleOnly: Boolean = true): List<AccessibilityNodeInfo> {
            instrumentation.uiAutomation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun walk(node: AccessibilityNodeInfo) { if (!visibleOnly || node.isVisibleToUser) result += node; for (i in 0 until node.childCount) node.getChild(i)?.let(::walk) }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::walk)
            return result
        }
        fun find(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
        fun await(label: String): AccessibilityNodeInfo {
            val end = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < end) { nodes(false).firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }?.let { return it }; Thread.sleep(80) }
            File(instrumentation.targetContext.filesDir, "conversation-failure.txt").writeText(nodes().joinToString("\n") { "${it.text} / ${it.contentDescription}" })
            error("Missing conversation control: $label")
        }
        fun tap(label: String) {
            val end = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < end) {
                var node: AccessibilityNodeInfo? = await(label)
                while (node != null && !node.isClickable) node = node.parent
                if (node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    instrumentation.waitForIdleSync(); Thread.sleep(350); return
                }
                Thread.sleep(80)
            }
            error("Conversation control was disabled: $label")
        }
        fun swipe() {
            val display = instrumentation.targetContext.resources.displayMetrics
            val attach = find("Attach PDF")
            val start = if (attach != null) Rect().also(attach::getBoundsInScreen).top - 110 * display.density else display.heightPixels * .75f
            val end = display.heightPixels * .3f
            val down = SystemClock.uptimeMillis()
            for (i in 0..12) {
                val action = when(i) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, display.widthPixels * .5f, start + (end - start) * i / 12, 0).also { instrumentation.sendPointerSync(it); it.recycle() }
                Thread.sleep(20)
            }
            instrumentation.waitForIdleSync(); Thread.sleep(200)
        }
        fun reveal(label: String) { repeat(12) { if (find(label) != null) return; swipe() }; await(label) }
        fun shot(name: String) {
            instrumentation.waitForIdleSync(); Thread.sleep(250)
            screenshotWindow?.let { readerFixtureScreenshot(it, File(instrumentation.targetContext.filesDir, name)) }
        }
        fun launch(theme: String): ActivityScenario<MainActivity> {
            prefs.edit().putString("origin", origin).putString("theme", theme).putBoolean("screenCaptureAllowed", true).putBoolean("onboardingCompleted", true).commit()
            AppLanguage.select("en")
            return ActivityScenario.launch(MainActivity::class.java).also { scenario -> scenario.onActivity { activity ->
                screenshotWindow = activity.window
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, captureMaskRequired = false)
                activity.viewModelStore.put("conversation", model)
                activity.setContent {
                    CompositionLocalProvider(LocalNativeActions provides actions, LocalImportEngine provides engine) { InvestmentApp(model) }
                    NativePrivacyCover(false)
                }
            } }
        }
        prefs.edit().putBoolean("importHintDismissed", false).commit()
        try {
            launch("light").use {
                tap("Documents"); await("Search documents"); await("Add document")
                assertNull(find("Attach PDF"))
                assertEquals(0, pickerOpens.get())
                tap("Add document"); await("Search documents")
                assertEquals(1, pickerOpens.get()); assertEquals(0, picks.get())
                assertNull(find("Attach PDF"))
                tap("Chat"); await("Attach PDF")
                assertFalse(nodes().any { it.isEditable })
                assertNull(find("Portfolio"))
                shot("conversation-welcome-light.png")
                tap("Dismiss import information")
                assertNull(find("PDF imports"))
                tap("Back"); await("Search documents"); await("Portfolio")
                tap("Chat"); await("Attach PDF")
                assertNull(find("Dismiss import information"))
                assertNull(find("Portfolio"))
                tap("Back"); await("Search documents")
                tap("Add document"); await("Reading your PDF")
                assertNull(find("Search documents")); assertNull(find("Portfolio"))
                firstRead.complete(Unit)
                await("Import needs attention"); await("Could not read this PDF.")
                shot("conversation-error-light.png")
                failRead.set(false); reveal("Retry import"); tap("Retry import")
                await("Bond confirmation detected"); reveal("Save PDF and confirm purchase")
                assertEquals(1, picks.get())
                shot("conversation-review-light.png")
                tap("Library"); await("Search documents"); tap("Chat")
                reveal("Save PDF and confirm purchase"); tap("Save PDF and confirm purchase")
                reveal("Confirm using saved PDF")
                assertEquals(1, uploads.get()); assertEquals(1, writes.get())
                failSave.set(false); tap("Confirm using saved PDF")
                reveal("Open document")
                assertEquals(1, uploads.get()); assertEquals(2, writes.get())
                shot("conversation-complete-light.png")
                reveal("Previous attempts"); tap("Previous attempts"); reveal("Could not read this PDF.")
                reveal("Open document"); tap("Open document")
                await("PDF page 1")
                shot("conversation-pdf-preview-light.png")
                tap("Back"); await("Chat")
                assertNull(find("Upload PDF"))
                shot("conversation-library-light.png")
                tap("Chat"); reveal("View purchase"); tap("View purchase"); await("Bond details")
            }
            launch("dark").use {
                tap("Documents"); await("Search documents"); await("Add document")
                tap("Chat"); reveal("Open document")
                assertNull(find("Dismiss import information"))
                assertEquals(1, picks.get())
                shot("conversation-history-dark.png")
                tap("Attach PDF"); await("Already in Library")
                await("This PDF and its purchase details are already saved. No further action is needed.")
                reveal("Open document")
                repeat(12) {
                    if (nodes().none { it.text?.toString()?.startsWith("First saved on this device") == true }) swipe()
                }
                shot("conversation-duplicate-time.png")
                assertTrue("Original save time must be shown on the duplicate receipt", nodes(false).any {
                    it.text?.toString()?.startsWith("First saved on this device") == true
                })
                assertEquals(1, uploads.get()); assertEquals(2, writes.get()); assertEquals(2, picks.get())
                assertNull(find("Save PDF and confirm purchase"))
                shot("conversation-already-saved-dark.png")
                instrumentation.runOnMainSync {
                    NativePdfInbox.offer((sourcePdf + "\nshared synthetic".toByteArray()), "conversation-shared-test", "shared-confirmation.pdf")
                }
                await("shared-confirmation.pdf"); await("Bond confirmation detected")
                assertEquals(2, picks.get())
                reveal("Cancel import"); tap("Cancel import")
                tap("Back"); await("Search documents"); tap("Portfolio"); await("Add purchase")
                tap("Add purchase"); await("Already in Library")
                assertEquals(3, picks.get()); assertNull(find("Choose PDF")); assertNull(find("Portfolio"))
                instrumentation.runOnMainSync { AppLanguage.select("fr") }
                Thread.sleep(400)
                shot("conversation-shared-french.png")
            }
        } finally {
            instrumentation.runOnMainSync { NativePdfInbox.dismiss() }
            AppLanguage.select(language)
            prefs.edit().clear().apply { previous.forEach { (key, value) -> when (value) {
                is String -> putString(key, value); is Boolean -> putBoolean(key, value); is Int -> putInt(key, value); is Long -> putLong(key, value); is Float -> putFloat(key, value)
            } } }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
