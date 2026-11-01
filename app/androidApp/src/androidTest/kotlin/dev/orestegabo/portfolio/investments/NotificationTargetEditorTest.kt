package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

/** End-to-end notification action, using a synthetic localhost account only. */
class NotificationTargetEditorTest {
    @Test fun targetNotificationOpensAboveInboxAndCanSaveOrCancel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val original = prefs.getString("origin", null)
        val hadOnboarding = prefs.contains("onboardingCompleted")
        val oldOnboarding = prefs.getBoolean("onboardingCompleted", false)
        val oldLanguage = AppLanguage.preference
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val serverTarget = AtomicLong(100_000)
        val storedTarget = AtomicLong(100_000)
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val start = reader.readLine().orEmpty().split(' ')
                val method = start.firstOrNull().orEmpty()
                val path = start.getOrNull(1).orEmpty()
                var length = 0
                while (true) {
                    val header = reader.readLine().orEmpty()
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                val chars = CharArray(length)
                var read = 0
                while (read < length) { val count = reader.read(chars, read, length - read); if (count < 0) break; read += count }
                if (method == "PUT" && path.endsWith("/notifications/preferences"))
                    serverTarget.set(JsonRecord(String(chars)).text("monthlyTarget").toLong())
                val body = when {
                    path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"notification-target-test","email":"test@example.test","isOwner":true}}"""
                    path.endsWith("/purchases") -> """{"purchases":[]}"""
                    path.endsWith("/equities") -> """{"holdings":[]}"""
                    path.endsWith("/documents") -> """{"documents":[]}"""
                    path.endsWith("/statement-items") -> """{"items":[]}"""
                    path.endsWith("/notifications/preferences") -> """{"monthlyTarget":${serverTarget.get()},"categories":{},"autoCoupon":false}"""
                    path.endsWith("/notifications/coupons") -> """{"receipts":[]}"""
                    path.endsWith("/notifications") -> """{"notifications":[{"id":"target-alert","kind":"targets","action":"settings","title":"Synthetic target reached","month":"2026-10","read":false}],"unreadCount":1,"serverDate":"$Today"}"""
                    else -> "{}"
                }
                val bytes = body.encodeToByteArray()
                client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
            } } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun readOnboardingCompleted() = true
            override fun readScreenCaptureAllowed() = true
            override fun readMonthlyTarget(account: String) = storedTarget.get()
            override fun writeMonthlyTarget(account: String, amount: Long) { storedTarget.set(amount) }
        }
        fun find(value: String): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (node.isVisibleToUser && !bounds.isEmpty && (node.text?.toString() == value || node.contentDescription?.toString() == value)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { found -> return found } }
                return null
            }
            return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        }
        fun await(value: String): AccessibilityNodeInfo {
            val deadline = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < deadline) { find(value)?.let { return it }; Thread.sleep(100) }
            fun collect(node: AccessibilityNodeInfo): String = buildString {
                append(node.text).append(' ').append(node.contentDescription).append(' ')
                for (i in 0 until node.childCount) node.getChild(i)?.let { append(collect(it)) }
            }
            java.io.File(instrumentation.targetContext.filesDir, "notification-target-state.txt").writeText(
                instrumentation.uiAutomation.rootInActiveWindow?.let(::collect).orEmpty())
            throw AssertionError("Visible notification/editor control missing: $value")
        }
        fun click(value: String) {
            val bounds = Rect().also(await(value)::getBoundsInScreen)
            val down = android.os.SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
            }
            instrumentation.waitForIdleSync(); Thread.sleep(200)
        }
        try {
            prefs.edit().putString("origin", origin).putBoolean("onboardingCompleted", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: PortfolioModel
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() })
                    activity.viewModelStore.put("notification-target", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        val actions = rememberNativeActions()
                        CompositionLocalProvider(LocalNativeActions provides actions) { InvestmentApp(model) }
                        NativePrivacyCover(false)
                    }
                }
                await("Home"); click("Notifications"); click("Edit monthly target")
                await("Monthly target"); click("500k"); click("Save")
                scenario.onActivity { assertEquals(500_000L, model.monthlyInvestmentTarget) }
                assertEquals(500_000L, storedTarget.get())
                val deadline = System.currentTimeMillis() + 5000
                while (serverTarget.get() != 500_000L && System.currentTimeMillis() < deadline) Thread.sleep(50)
                assertEquals("Target sync reaches the existing preferences API", 500_000L, serverTarget.get())
                click("Edit monthly target"); await("Monthly target"); click("300k"); click("Cancel")
                scenario.onActivity { assertEquals(500_000L, model.monthlyInvestmentTarget) }
                assertEquals(500_000L, storedTarget.get())
                await("Edit monthly target")
            }
        } finally {
            server.close(); worker.shutdownNow()
            prefs.edit().apply {
                if (original == null) remove("origin") else putString("origin", original)
                if (hadOnboarding) putBoolean("onboardingCompleted", oldOnboarding) else remove("onboardingCompleted")
            }.commit()
            AppLanguage.select(oldLanguage)
        }
    }
}
