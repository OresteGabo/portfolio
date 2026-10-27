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
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors

/** Synthetic, unauthenticated first-run tour: no credentials, uploads or device permissions. */
class OnboardingUiTest {
    @Test fun tourSupportsThemesFrenchSkipCompletionAndPersistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val previous = prefs.all.toMap()
        val previousLanguage = AppLanguage.preference
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val signedIn = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = (if (path.endsWith("/auth/session") && signedIn.get())
                        """{"authenticated":true,"user":{"id":"onboarding-test","email":"demo@example.test","isOwner":false}}"""
                        else if (path.endsWith("/auth/session")) "{\"authenticated\":false}" else "{}").toByteArray()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
            } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readScreenCaptureAllowed() = true
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun walk(node: AccessibilityNodeInfo) {
                result += node
                for (index in 0 until node.childCount) node.getChild(index)?.let(::walk)
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::walk)
            return result
        }
        fun await(label: String): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 12_000
            while (SystemClock.uptimeMillis() < deadline) {
                nodes().firstOrNull { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) }?.let { return it }
                Thread.sleep(80)
            }
            File(instrumentation.targetContext.cacheDir, "onboarding-failure.txt").writeText(nodes().joinToString("\n") { "${it.text} / ${it.contentDescription}" })
            throw AssertionError("Missing onboarding control: $label")
        }
        fun tap(label: String) {
            val bounds = Rect().also(await(label)::getBoundsInScreen)
            val down = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
            }
            instrumentation.waitForIdleSync()
        }
        fun scrollSettings() {
            val metrics = instrumentation.targetContext.resources.displayMetrics
            val x = metrics.widthPixels * .5f
            val start = metrics.heightPixels * .74f
            val end = metrics.heightPixels * .34f
            val down = SystemClock.uptimeMillis()
            for (index in 0..12) {
                val action = when (index) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, start + (end - start) * index / 12, 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
                Thread.sleep(18)
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(200)
        }
        fun screenshot(name: String) {
            instrumentation.waitForIdleSync()
            Thread.sleep(150)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(instrumentation.targetContext.cacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, captureMaskRequired = false)
                activity.viewModelStore.put("onboarding-test", model)
                activity.setContent {
                    CompositionLocalProvider(LocalNativeActions provides rememberNativeActions()) { InvestmentApp(model) }
                }
            }
        }
        try {
            for ((theme, language) in listOf("light" to "en", "dark" to "en", "dark" to "fr")) {
                prefs.edit().putString("origin", origin).putString("theme", theme).putString("language", language).putBoolean("onboardingCompleted", false).commit()
                AppLanguage.select(language)
                launch().use { scenario ->
                    val french = language == "fr"
                    await(if (french) "Votre portefeuille.\nUne vision plus claire." else "Your portfolio.\nA clearer picture.")
                    screenshot("onboarding-$theme-$language-1.png")
                    for (page in 2..4) {
                        tap(if (french) "Continuer" else "Continue")
                        await(when (page) {
                            2 -> if (french) "Moins de saisie.\nPlus de clarté." else "Less typing.\nMore understanding."
                            3 -> if (french) "Les dates importantes.\nBien en vue." else "Important dates.\nIn plain sight."
                            else -> if (french) "Une année d’investissement.\nClairement documentée." else "A year of investing.\nClearly documented."
                        })
                        screenshot("onboarding-$theme-$language-$page.png")
                    }
                    tap(if (french) "Étape précédente" else "Previous step")
                    await(if (french) "Les dates importantes.\nBien en vue." else "Important dates.\nIn plain sight.")
                    tap(if (french) "Continuer" else "Continue")
                    tap(if (french) "Commencer" else "Get started")
                    await(if (french) "Bon retour" else "Welcome back")
                    assertTrue(prefs.getBoolean("onboardingCompleted", false))
                }
                launch().use { await(if (language == "fr") "Bon retour" else "Welcome back") }
            }
            prefs.edit().putBoolean("onboardingCompleted", false).commit()
            AppLanguage.select("en")
            launch().use {
                tap("Skip")
                await("Welcome back")
                assertTrue(prefs.getBoolean("onboardingCompleted", false))
            }
            prefs.edit().putBoolean("onboardingCompleted", false).commit()
            launch().use {
                tap("Already have an account? Sign in")
                await("Welcome back")
                assertTrue(prefs.getBoolean("onboardingCompleted", false))
            }
            signedIn.set(true)
            launch().use {
                tap("Settings")
                await("Notifications")
                repeat(12) {
                    if (nodes().none { node -> node.isVisibleToUser && node.text?.toString() == "Replay introduction" }) {
                        scrollSettings()
                    }
                }
                tap("Replay introduction")
                await("Your portfolio.\nA clearer picture.")
                screenshot("onboarding-replay.png")
                tap("Close")
                await("Notifications") // Settings returns at its normal top position.
                await("Settings")
            }
        } finally {
            AppLanguage.select(previousLanguage)
            prefs.edit().clear().apply {
                previous.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                } }
            }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}
