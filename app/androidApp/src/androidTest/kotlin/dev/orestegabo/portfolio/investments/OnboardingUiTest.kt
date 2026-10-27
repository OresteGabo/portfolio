package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        val largeTextOnly = InstrumentationRegistry.getArguments().getString("largeTextOnly") == "true"
        val smallScreenOnly = InstrumentationRegistry.getArguments().getString("smallScreenOnly") == "true"
        val profile = InstrumentationRegistry.getArguments().getString("smallProfile") ?: "small"
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
            val onboardingAction = if (smallScreenOnly) nodes().firstOrNull {
                it.isVisibleToUser && it.text?.toString() in listOf(localized("Continue"), localized("Get started"))
            } else null
            val actionBounds = onboardingAction?.let { Rect().also(it::getBoundsInScreen) }
            val start = actionBounds?.let { it.top - 72 * metrics.density } ?: (metrics.heightPixels * .74f)
            val end = if (actionBounds != null) 130 * metrics.density else metrics.heightPixels * .34f
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
        var largeText = false
        var textScaleOverride: Float? = null
        fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, captureMaskRequired = false)
                activity.viewModelStore.put("onboarding-test", model)
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalNativeActions provides rememberNativeActions(),
                        LocalDensity provides Density(density.density, textScaleOverride ?: if (largeText) 1.35f else density.fontScale)) { InvestmentApp(model) }
                }
            }
        }
        try {
            if (smallScreenOnly) {
                val steps = listOf(
                    "Your portfolio.\nA clearer picture." to "Bring your Rwanda bond and share records together, so the details make more sense.",
                    "Less typing.\nMore understanding." to "Turn a broker PDF into clear bond and share records. Review the details before they reach your portfolio.",
                    "Important dates.\nIn plain sight." to "See subscription windows, expected coupons and maturities. Choose the reminders that matter to you in Settings.",
                    "A year of investing.\nClearly documented." to "Keep your documents together and generate an annual report whenever you need one. You choose what to export.",
                )
                fun scrollTo(label: String) {
                    repeat(10) {
                        // Semantics can lag a completed pager animation or screenshot briefly.
                        // Wait before scrolling so a transient missing node does not move it off screen.
                        repeat(3) {
                            if (nodes().any { node -> node.isVisibleToUser && node.text?.toString() == label }) return
                            Thread.sleep(100)
                        }
                        scrollSettings()
                    }
                    await(label)
                }
                fun assertReachableButton(label: String) {
                    var node = await(label)
                    while (!node.isClickable && node.parent != null) node = node.parent
                    val bounds = Rect().also(node::getBoundsInScreen)
                    val display = instrumentation.targetContext.resources.displayMetrics
                    assertTrue("$profile: $label stays inside the screen", bounds.top >= 0 && bounds.bottom <= display.heightPixels && bounds.left >= 0 && bounds.right <= display.widthPixels)
                    assertTrue("$profile: $label keeps a 48dp touch target", bounds.height() >= (48 * display.density).toInt())
                }
                for ((theme, language, scale) in listOf(Triple("light", "en", 1f), Triple("dark", "fr", 1f),
                    Triple("dark", "en", 1.35f), Triple("light", "fr", 1.35f)).filter {
                        InstrumentationRegistry.getArguments().getString("smallLargeTextOnly") != "true" || it.third > 1f
                    }) {
                    textScaleOverride = scale
                    prefs.edit().putString("origin", origin).putString("theme", theme).putString("language", language)
                        .putBoolean("onboardingCompleted", false).commit()
                    AppLanguage.select(language)
                    launch().use {
                        val next = localized("Continue")
                        steps.forEachIndexed { index, (title, body) ->
                            if (index > 0) tap(next)
                            val stepLabel = "${localized("Step")} ${index + 1} ${localized("of")} 4: ${localized(title).replace('\n', ' ')}"
                            await(stepLabel)
                            Thread.sleep(400)
                            val action = localized(if (index == 3) "Get started" else "Continue")
                            assertReachableButton(action)
                            assertReachableButton(localized("Skip"))
                            val file = "onboarding-small-$profile-$theme-$language-${scale}-$index"
                            screenshot("$file-start.png")
                            await(localized(title))
                            scrollTo(localized(body))
                            screenshot("$file-read.png")
                            // Also inspect the artwork below the explanation on short viewports.
                            scrollSettings()
                            screenshot("$file-middle.png")
                            repeat(2) { scrollSettings() }
                            screenshot("$file-art.png")
                            assertReachableButton(action)
                            if (index > 0) assertReachableButton(localized("Previous step"))
                        }
                        tap(localized("Get started"))
                        await(localized("Welcome back"))
                        assertTrue(prefs.getBoolean("onboardingCompleted", false))
                    }
                }
                return
            }
            for ((theme, language) in if (largeTextOnly) emptyList() else listOf("light" to "en", "dark" to "en", "dark" to "fr")) {
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
                        if (page == 2) await(if (french)
                            "Les données d’un PDF de courtier sont extraites et vérifiées. Obligations, actions et coûts d’achat alimentent la répartition du portefeuille, sa valeur estimée et les dates des coupons. Vérifiez les détails avant l’enregistrement."
                            else "A broker PDF is extracted and reviewed. Bond records, share records and purchase costs feed your portfolio mix, estimated value and coupon dates. Review the details before saving.")
                        if (page == 2) {
                            val copy = await(if (french)
                                "Transformez un PDF de courtier en données claires sur vos obligations et actions. Vérifiez les détails avant de les ajouter au portefeuille."
                                else "Turn a broker PDF into clear bond and share records. Review the details before they reach your portfolio.")
                            val copyBounds = Rect().also(copy::getBoundsInScreen)
                            val buttonBounds = Rect().also(await(if (french) "Continuer" else "Continue")::getBoundsInScreen)
                            assertTrue("The explanation stays above the navigation controls", copyBounds.bottom <= buttonBounds.top)
                        }
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
            largeText = true
            prefs.edit().putString("origin", origin).putBoolean("onboardingCompleted", false).putString("theme", "dark").putString("language", "en").commit()
            AppLanguage.select("en")
            launch().use {
                tap("Continue")
                await("A broker PDF is extracted and reviewed. Bond records, share records and purchase costs feed your portfolio mix, estimated value and coupon dates. Review the details before saving.")
                screenshot("onboarding-import-large-text-top.png")
                scrollSettings()
                await("Less typing.\nMore understanding.")
                screenshot("onboarding-import-large-text-bottom.png")
                tap("Skip")
                await("Welcome back")
            }
            largeText = false
            if (largeTextOnly) return
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
