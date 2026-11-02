package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import dev.orestegabo.portfolio.MainActivity
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Uses the real App/settings path with an unavailable localhost server, not an authenticated fixture. */
@SdkSuppress(minSdkVersion = 33)
class GuestEntryTest {
    @Test fun guestEntrySwitchingAndActivityRestartNeedNoAccountRequests() {
        val i = InstrumentationRegistry.getInstrumentation()
        val prefs = i.targetContext.getSharedPreferences("connection", 0)
        val keys = listOf("origin", "sessionChoice", "onboardingCompleted", "screenCaptureAllowed", "numbersHidden", "language")
        val old = prefs.all.filterKeys { it in keys }
        val server = ServerSocket(0)
        val requests = AtomicInteger()
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                requests.incrementAndGet()
                val reader = client.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) {}
                client.getOutputStream().write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
            } } catch (_: Exception) {}
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            i.uiAutomation.clearCache()
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo) { if (n.isVisibleToUser) nodes += n; for (c in 0 until n.childCount) n.getChild(c)?.let(::visit) }
            i.uiAutomation.rootInActiveWindow?.let(::visit)
            return nodes
        }
        fun find(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
        fun await(label: String) {
            val until = android.os.SystemClock.uptimeMillis() + 12_000
            while (android.os.SystemClock.uptimeMillis() < until) { if (find(label) != null) return; Thread.sleep(100) }
            File(i.targetContext.filesDir,"guest-entry-failure.txt").writeText(nodes().joinToString("\n") { "${it.text} | ${it.contentDescription}" })
            throw AssertionError("Guest control missing: $label")
        }
        fun scroll(label: String) {
            if (find(label) != null) return
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) repeat(12) {
                if (find(label) != null) return
                nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }?.performAction(direction)
                i.waitForIdleSync(); Thread.sleep(100)
            }
            await(label)
        }
        fun click(label: String) {
            scroll(label)
            for (candidate in nodes().filter { it.text?.toString() == label || it.contentDescription?.toString() == label }) {
                var n: AccessibilityNodeInfo? = candidate
                while (n != null) {
                    if (n.isClickable) { assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); i.waitForIdleSync(); Thread.sleep(300); return }
                    n = n.parent
                }
            }
            throw AssertionError("Guest action missing: $label")
        }
        try {
            prefs.edit().remove("sessionChoice").putString("origin","http://127.0.0.1:${server.localPort}")
                .putBoolean("onboardingCompleted",false).putBoolean("screenCaptureAllowed",true).putBoolean("numbersHidden",false).putString("language","en").commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("Continue as Guest"); assertEquals(0,requests.get())
                click("Continue as Guest"); await("Explore fictional sample"); click("Explore fictional sample")
                await("Guest · Fictional sample"); scroll("Example company — fictional")
                var window: android.view.Window? = null
                scenario.onActivity { window = it.window }
                readerFixtureScreenshot(requireNotNull(window), File(i.targetContext.filesDir,"guest-sample.png"))
                assertEquals("guest-sample-v1",prefs.getString("sessionChoice",null))
                click("Documents"); await("Switch to your personal workspace to import PDFs.")
                click("Markets"); await("Guest live markets are not available yet")
                click("Settings"); click("Change guest experience"); click("Start personal portfolio")
                await("Guest · Personal workspace"); scroll("Your personal portfolio starts here")
                assertNull(find("Example company — fictional")); assertEquals("guest-personal-v1",prefs.getString("sessionChoice",null))
                assertTrue(prefs.getBoolean("onboardingCompleted",false)); assertEquals(0,requests.get())
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await("Guest · Personal workspace"); scroll("Your personal portfolio starts here")
                var window: android.view.Window? = null
                scenario.onActivity { window = it.window }
                readerFixtureScreenshot(requireNotNull(window), File(i.targetContext.filesDir,"guest-personal-reopened.png"))
                click("Sign in to your account"); await("Welcome back"); await("Return to Guest")
                assertEquals(0, requests.get())
                click("Return to Guest"); await("Guest · Personal workspace")
                assertEquals(0, requests.get())
            }
        } finally {
            server.close(); worker.shutdownNow()
            prefs.edit().apply {
                keys.forEach(::remove)
                old.forEach { (key,value) -> when(value) { is Boolean -> putBoolean(key,value); is String -> putString(key,value) } }
            }.commit()
        }
    }
}
