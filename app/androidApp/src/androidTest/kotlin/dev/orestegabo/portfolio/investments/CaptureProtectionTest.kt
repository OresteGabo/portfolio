package dev.orestegabo.portfolio.investments

import android.graphics.Color
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import java.net.ServerSocket
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class CaptureProtectionTest {
    @Test fun foregroundScreenshotsSaveWithEitherPreferenceAndBackgroundRemainsSecure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val oldOrigin = prefs.getString("origin", null)
        val oldCapture = if (prefs.contains("screenCaptureAllowed")) prefs.getBoolean("screenCaptureAllowed", false) else null
        val server = ServerSocket(0)
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = "{\"authenticated\":false}"
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                }
            } catch (_: Exception) { if (!server.isClosed) throw AssertionError("Synthetic API failed") }
        }
        fun awaitFlag(scenario: ActivityScenario<MainActivity>, secure: Boolean) {
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                var actual = !secure
                scenario.onActivity { actual = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
                if (actual == secure) return
                Thread.sleep(30)
            }
            fail("Window capture flag did not become $secure")
        }
        fun awaitScreenshotColor(expected: Int) {
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    val pixel = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                    screenshot.recycle()
                    if (pixel == expected) return
                }
                Thread.sleep(60)
            }
            fail("System screenshot did not match expected protected/unprotected content")
        }
        try {
            assertTrue(prefs.edit().putString("origin", "http://127.0.0.1:${server.localPort}").remove("screenCaptureAllowed").commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitFlag(scenario, false)
                val protect = mutableStateOf(true)
                scenario.onActivity { activity ->
                    activity.window.decorView.setBackgroundColor(Color.MAGENTA)
                    activity.findViewById<android.view.View>(android.R.id.content).setBackgroundColor(Color.MAGENTA)
                    activity.setContent { NativePrivacyCover(protectCaptures = protect.value) }
                }
                awaitFlag(scenario, false)
                awaitScreenshotColor(Color.MAGENTA)
                scenario.onActivity { protect.value = false }
                awaitFlag(scenario, false)
                awaitScreenshotColor(Color.MAGENTA)
                scenario.moveToState(Lifecycle.State.CREATED)
                awaitFlag(scenario, true)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitFlag(scenario, false)
                scenario.onActivity { protect.value = true }
                awaitFlag(scenario, false)
                awaitScreenshotColor(Color.MAGENTA)
            }
            // A saved Settings override applies on a new Activity, rather than requiring another toggle.
            assertTrue(prefs.edit().putBoolean("screenCaptureAllowed", true).commit())
            ActivityScenario.launch(MainActivity::class.java).use { awaitFlag(it, false) }
        } finally {
            val edit = prefs.edit()
            if (oldOrigin == null) edit.remove("origin") else edit.putString("origin", oldOrigin)
            if (oldCapture == null) edit.remove("screenCaptureAllowed") else edit.putBoolean("screenCaptureAllowed", oldCapture)
            edit.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
