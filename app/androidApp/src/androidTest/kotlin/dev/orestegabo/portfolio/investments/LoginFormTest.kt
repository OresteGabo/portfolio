package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
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

class LoginFormTest {
    @Test fun conciseFormSupportsPasswordRevealInvitationModeAndTranslatedCopy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val oldOrigin = prefs.getString("origin", null)
        val oldIntroduction = prefs.getBoolean("onboardingCompleted", false)
        prefs.edit().putBoolean("onboardingCompleted", true).commit()
        val oldLanguage = AppLanguage.preference
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val submissions = LinkedBlockingQueue<String>()
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val first = reader.readLine().orEmpty()
                    var length = 0
                    while (true) {
                        val line = reader.readLine().orEmpty()
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    val request = CharArray(length)
                    var read = 0
                    while (read < length) { val n = reader.read(request, read, length - read); if (n < 0) break; read += n }
                    if (first.startsWith("POST ")) submissions.add(String(request, 0, read))
                    val body = """{"authenticated":false}"""
                    val status = if (first.startsWith("POST ")) "401 Unauthorized" else "200 OK"
                    client.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
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
        val vault = object : SecureSignIn {
            override val available = true
            override val unlockLabel = "Fingerprint"
            override fun hasSaved(origin: String) = false
            override suspend fun read(origin: String): String? = null
            override suspend fun save(origin: String, value: String) = true
            override fun delete(origin: String) = Unit
            override fun lock() = Unit
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
        fun find(label: String): AccessibilityNodeInfo? = nodes().firstOrNull {
            it.text?.toString() == label || it.contentDescription?.toString() == label
        }
        fun await(label: String): AccessibilityNodeInfo {
            val deadline = System.currentTimeMillis() + 6000
            while (System.currentTimeMillis() < deadline) { find(label)?.let { return it }; Thread.sleep(100) }
            File(instrumentation.targetContext.cacheDir, "login-test-state.txt").writeText(nodes().joinToString("\n") {
                val bounds = android.graphics.Rect().also(it::getBoundsInScreen)
                "${it.className} text=${it.text} desc=${it.contentDescription} clickable=${it.isClickable} enabled=${it.isEnabled} bounds=$bounds"
            })
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(instrumentation.targetContext.cacheDir, "login-test-state.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            throw AssertionError("Missing login control: $label")
        }
        fun tap(node: AccessibilityNodeInfo) {
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            val downTime = android.os.SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
            }
            instrumentation.waitForIdleSync(); Thread.sleep(250)
        }
        fun click(label: String) { tap(await(label)) }
        fun edit(node: AccessibilityNodeInfo, value: String) {
            tap(node)
            instrumentation.sendStringSync(value)
            instrumentation.waitForIdleSync(); Thread.sleep(200)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync(); Thread.sleep(250)
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
                    // Keep synthetic test credentials out of the user's actual password provider.
                    activity.window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, vault = vault)
                    activity.viewModelStore.put("login-form-test", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        CompositionLocalProvider(LocalNativeActions provides rememberNativeActions()) { InvestmentApp(model) }
                        NativePrivacyCover(false)
                    }
                }
                await("Welcome back")
                assertNull(find("Investment journal")); assertNull(find("Your investment journal"))
                assertEquals(2, nodes().count { it.isEditable })
                screenshot("login-refined-en.png")
                edit(nodes().first { it.isEditable }, "demo@example.test")
                edit(nodes().last { it.isEditable }, "synthetic-demo-password")
                assertTrue(nodes().any { it.isEditable && it.isPassword })
                val passwordBounds = android.graphics.Rect().also(nodes().last { it.isEditable }::getBoundsInScreen)
                val maskedFrame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                click("Show password")
                await("Hide password")
                // Android intentionally keeps password accessibility/autofill classification.
                // Compare the text pixels, excluding the label, icons and blinking caret.
                assertTrue(nodes().any { it.isEditable && it.isPassword })
                val revealedFrame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val density = instrumentation.targetContext.resources.displayMetrics.density
                var changedTextPixels = 0
                for (y in passwordBounds.top + (24 * density).toInt() until passwordBounds.bottom - (8 * density).toInt()) {
                    for (x in passwordBounds.left + (64 * density).toInt() until passwordBounds.right - (64 * density).toInt()) {
                        if (maskedFrame.getPixel(x, y) != revealedFrame.getPixel(x, y)) changedTextPixels++
                    }
                }
                maskedFrame.recycle(); revealedFrame.recycle()
                assertTrue("Reveal must change the displayed password text", changedTextPixels > 200)
                click("Hide password")
                assertTrue(nodes().any { it.isEditable && it.isPassword })
                click("Sign in")
                val submitted = checkNotNull(submissions.poll(5, TimeUnit.SECONDS))
                assertTrue(submitted.contains("demo@example.test"))
                assertTrue(submitted.contains("synthetic-demo-password"))
                // A rejected login stays on the form, with the password still hidden.
                await("Use an invitation")
                click("Use an invitation")
                await("Confirm password")
                assertEquals(4, nodes().count { it.isEditable })
                click("Back to sign in")
                await("Welcome back")
                scenario.onActivity { AppLanguage.select("fr") }
                await("Bon retour")
                await("Se connecter")
                screenshot("login-refined-fr.png")
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
