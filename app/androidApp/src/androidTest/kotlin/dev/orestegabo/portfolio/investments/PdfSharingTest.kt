package dev.orestegabo.portfolio.investments

import android.content.Intent
import android.content.ComponentName
import android.accessibilityservice.AccessibilityService
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors

class PdfSharingTest {
    @Test fun pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("connection", 0)
        val previous = prefs.all.toMap()
        val server = ServerSocket(0)
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                reader.readLine()
                while (!reader.readLine().isNullOrEmpty()) { }
                val body = "{\"authenticated\":false}"
                client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n" + body).toByteArray())
            } } catch (_: Exception) { }
        }
        val bytes = "%PDF-1.7 synthetic share fixture\n%%EOF".toByteArray()
        fun await(check: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 10_000
            while (!check() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue(check())
        }
        fun clearInbox() = instrumentation.runOnMainSync { NativePdfInbox.dismiss(); NativePdfInbox.dismissError() }
        try {
            clearInbox()
            prefs.edit().putString("origin", "http://127.0.0.1:${server.localPort}")
                .putBoolean("onboardingCompleted", true).commit()
            val outgoing = pdfShareIntent(context, "../../statement.pdf", bytes)
            assertEquals(Intent.ACTION_SEND, outgoing.action)
            assertEquals("application/pdf", outgoing.type)
            assertTrue(outgoing.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, outgoing.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            @Suppress("DEPRECATION") val uri = requireNotNull(outgoing.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", uri.scheme)
            assertEquals(context.packageName + ".pdfs", uri.authority)
            assertEquals(uri, outgoing.clipData!!.getItemAt(0).uri)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            val matches = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("application/pdf"), 0)
            assertTrue("Struct Capital appears as a PDF share target", matches.any { it.activityInfo.packageName == context.packageName && it.activityInfo.name.endsWith("MainActivity") })
            val component = ComponentName(context, MainActivity::class.java)
            val cold = Intent(outgoing).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<MainActivity>(cold).use { scenario ->
                try {
                    await { NativePdfInbox.pending != null }
                    assertArrayEquals(bytes, NativePdfInbox.pending!!.bytes)
                    val first = NativePdfInbox.pending!!.id
                    scenario.recreate()
                    Thread.sleep(300)
                    assertEquals(first, NativePdfInbox.pending!!.id)
                    clearInbox()
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(view)
                    await { NativePdfInbox.pending != null }
                    assertTrue(NativePdfInbox.pending!!.id > first)
                    assertArrayEquals(bytes, NativePdfInbox.pending!!.bytes)
                    clearInbox()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/private.pdf")).setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    await { NativePdfInbox.error != null }
                    assertNull(NativePdfInbox.pending)
                    assertEquals("Could not open the selected PDF.", NativePdfInbox.error)
                    clearInbox()
                    val completed = java.util.concurrent.atomic.AtomicBoolean(false)
                    scenario.onActivity { activity -> activity.setContent {
                        val actions = rememberNativeActions()
                        LaunchedEffect(Unit) {
                            actions.sharePdf("synthetic-report.pdf", bytes)
                            completed.set(true)
                        }
                    } }
                    await {
                        instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() in setOf("android", "com.android.intentresolver")
                    }
                    assertFalse(completed.get())
                    instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    await { completed.get() }
                } finally {
                    // ActivityScenario tracks the launch intent; onNewIntent intentionally replaces it.
                    scenario.onActivity { it.intent = cold }
                }
            }
        } finally {
            clearInbox()
            prefs.edit().clear().apply {
                previous.forEach { (key, value) -> when(value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                } }
            }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
