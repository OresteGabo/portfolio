package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import java.net.ServerSocket
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class SettingsTargetEditorTest {
    @Test fun collapsedTargetOpensVisibleEditorAndSavesWhileCancelPreservesValue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val previousOrigin = prefs.getString("origin", null)
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = when {
                        path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"target-editor-test","email":"demo@example.test","isOwner":true}}"""
                        path.endsWith("/purchases") -> """{"purchases":[]}"""
                        path.endsWith("/equities") -> """{"holdings":[]}"""
                        path.endsWith("/documents") -> """{"documents":[]}"""
                        path.endsWith("/statement-items") -> """{"items":[]}"""
                        else -> "{}"
                    }
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                }
            } catch (_: Exception) { }
        }
        var storedTarget: Long? = null
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun readScreenCaptureAllowed() = true
            override fun readMonthlyTarget(account: String) = storedTarget
            override fun writeMonthlyTarget(account: String, amount: Long) { storedTarget = amount }
        }
        fun visibleNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (node.isVisibleToUser && !bounds.isEmpty && predicate(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { match -> return match } }
                return null
            }
            return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        }
        fun named(value: String) = visibleNode { it.text?.toString() == value || it.contentDescription?.toString() == value }
        fun awaitNamed(value: String): AccessibilityNodeInfo {
            val deadline = System.currentTimeMillis() + 6000
            while (System.currentTimeMillis() < deadline) {
                named(value)?.let { return it }
                Thread.sleep(100)
            }
            val dump = StringBuilder()
            fun inspect(node: AccessibilityNodeInfo, depth: Int = 0) {
                dump.append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                    .append(" desc=").append(node.contentDescription).append(" visible=").append(node.isVisibleToUser)
                    .append(" scroll=").append(node.isScrollable).append('\n')
                for (i in 0 until node.childCount) node.getChild(i)?.let { inspect(it, depth + 1) }
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let { inspect(it) }
            java.io.File(instrumentation.targetContext.cacheDir, "target-editor-accessibility.txt").writeText(dump.toString())
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(instrumentation.targetContext.cacheDir, "target-editor-state.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            throw AssertionError("Visible control not found: $value")
        }
        fun click(value: String) {
            var node: AccessibilityNodeInfo? = awaitNamed(value)
            while (node != null && !node.isClickable) node = node.parent
            assertNotNull("Clickable control: $value", node)
            assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            Thread.sleep(150)
        }
        try {
            // Use only synthetic account data and restore the real connection after the test.
            prefs.edit().putString("origin", origin).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: PortfolioModel
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() })
                    activity.viewModelStore.put("target-editor-test", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        val actions = rememberNativeActions()
                        CompositionLocalProvider(LocalNativeActions provides actions, LocalNumbersHidden provides false) {
                            MaterialTheme {
                                // Reproduce the app's parent Column, which previously placed the
                                // full-height editor after the full-height Settings list.
                                Column(Modifier.fillMaxSize()) {
                                    SettingsScreen(model, "light", {}, "area", {}, {}, {}, {})
                                }
                            }
                        }
                        NativePrivacyCover(false)
                    }
                }
                val deadline = System.currentTimeMillis() + 6000
                var ready = false
                while (!ready && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { ready = model.user != null && !model.busy }
                    if (!ready) Thread.sleep(100)
                }
                assertTrue("Synthetic account must finish loading", ready)
                for (attempt in 0 until 12) {
                    if (named("Monthly investment target") != null) break
                    val metrics = instrumentation.targetContext.resources.displayMetrics
                    val x = metrics.widthPixels * .5f
                    val startY = metrics.heightPixels * .72f
                    val endY = metrics.heightPixels * .52f
                    val downTime = android.os.SystemClock.uptimeMillis()
                    fun touch(action: Int, y: Float) {
                        android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0).also {
                            instrumentation.sendPointerSync(it); it.recycle()
                        }
                    }
                    touch(android.view.MotionEvent.ACTION_DOWN, startY)
                    for (step in 1..10) {
                        Thread.sleep(40)
                        touch(android.view.MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 10)
                    }
                    Thread.sleep(150)
                    touch(android.view.MotionEvent.ACTION_UP, endY)
                    instrumentation.waitForIdleSync()
                    Thread.sleep(300)
                }
                assertNull("Progress starts folded", named("Recent months"))
                click("Edit monthly target")
                val editor = awaitNamed("Monthly target")
                val bounds = Rect().also(editor::getBoundsInScreen)
                assertTrue("Editor must be inside the visible viewport", bounds.top >= 0 && bounds.bottom <= instrumentation.targetContext.resources.displayMetrics.heightPixels)
                click("300k")
                click("Save")
                scenario.onActivity { assertEquals(300_000L, model.monthlyInvestmentTarget) }
                assertEquals(300_000L, storedTarget)
                click("Edit monthly target")
                awaitNamed("Monthly target")
                val input = checkNotNull(visibleNode { it.isEditable })
                assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "450000") }))
                instrumentation.waitForIdleSync()
                click("Save")
                scenario.onActivity { assertEquals(450_000L, model.monthlyInvestmentTarget) }
                assertEquals(450_000L, storedTarget)
                click("Edit monthly target")
                click("500k")
                click("Cancel")
                scenario.onActivity { assertEquals(450_000L, model.monthlyInvestmentTarget) }
                assertEquals(450_000L, storedTarget)
                assertNull("Editing must not unfold the progress section", named("Recent months"))
            }
        } finally {
            prefs.edit().apply { if (previousOrigin == null) remove("origin") else putString("origin", previousOrigin) }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
