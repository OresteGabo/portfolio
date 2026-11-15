package dev.orestegabo.portfolio.investments

import android.os.Bundle
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*

/** Accessibility-driven UI interactions; no physical key or global input-mode changes. */
internal class UsabilityTestDriver {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    fun nodes(): List<AccessibilityNodeInfo> {
        fun walk(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (n == null) emptyList()
            else listOf(n) + (0 until n.childCount).flatMap { walk(n.getChild(it)) }
        instrumentation.uiAutomation.clearCache()
        return walk(instrumentation.uiAutomation.rootInActiveWindow)
    }
    fun matches(n: AccessibilityNodeInfo, label: String) = n.isVisibleToUser &&
        (n.text?.toString() == label || n.contentDescription?.toString() == label || n.hintText?.toString() == label)
    fun waitUntil(description: String, predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < until) { if (predicate()) return; Thread.sleep(60) }
        error("Synthetic UX condition missing: $description")
    }
    fun await(label: String): AccessibilityNodeInfo {
        waitUntil(label) { nodes().any { matches(it, label) } }
        return nodes().first { matches(it, label) }
    }
    fun click(label: String, last: Boolean = false) {
        waitUntil("clickable $label") {
            nodes().filter { matches(it, label) }.let { if (last) it.reversed() else it }.any { candidate ->
                var n: AccessibilityNodeInfo? = candidate
                while (n != null && !n.isClickable) n = n.parent
                n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
    }
    fun scroll(label: String) {
        for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) repeat(16) {
            if (nodes().any { matches(it, label) }) return
            nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }
                ?.performAction(direction)
            instrumentation.waitForIdleSync(); Thread.sleep(80)
        }
        await(label)
    }
    fun enter(label: String, value: String) {
        scroll(label)
        fun containsLabel(n: AccessibilityNodeInfo): Boolean = matches(n, label) ||
            (0 until n.childCount).any { n.getChild(it)?.let(::containsLabel) == true }
        var field = nodes().firstOrNull { it.isEditable && containsLabel(it) }
        if (field == null) {
            var n: AccessibilityNodeInfo? = await(label)
            while (n != null && !n.isEditable) n = n.parent
            field = n
        }
        assertNotNull("Editable $label", field)
        assertTrue(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
    }
}

internal class UsabilityPreferences : AutoCloseable {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("connection", 0)
    private val before = prefs.all.toMap()
    private val language = AppLanguage.preference
    init { prefs.edit().putBoolean("onboardingCompleted", true).putString("theme", "light").putString("language", "en").commit() }
    @Suppress("UNCHECKED_CAST")
    override fun close() {
        val editor = prefs.edit().clear()
        before.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
        } }
        editor.commit()
        AppLanguage.initialize(object : ServerSettings {
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = language
        })
    }
}
