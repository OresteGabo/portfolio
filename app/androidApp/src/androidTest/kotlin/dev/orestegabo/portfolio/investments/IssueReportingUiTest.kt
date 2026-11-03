package dev.orestegabo.portfolio.investments

import android.os.Bundle
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Synthetic report only: composeEmail is stubbed and no real email or network request is made. */
class IssueReportingUiTest {
    @Test fun reviewDiagnosticsFallbackAndDiscardWorkWithoutSendingMail() {
        val i = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val language = args.getString("language") ?: "en"
        val dark = args.getString("theme") == "dark"
        val previousLanguage = AppLanguage.preference
        AppLanguage.select(language)
        var calls = 0
        var reportBody = ""
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external URL in report test")
            override fun shareText(text: String) = error("No external share in report test")
            override suspend fun composeEmail(address: String, subject: String, body: String): Boolean {
                assertEquals("support@structcapital.net", address)
                assertTrue(subject.startsWith("Struct Capital"))
                reportBody = body
                calls++
                return calls > 1
            }
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            i.uiAutomation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                if (node.isVisibleToUser) result += node
                for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
            }
            i.uiAutomation.rootInActiveWindow?.let(::visit)
            return result
        }
        fun find(label: String) = nodes().firstOrNull { it.text?.toString() == localized(label) || it.contentDescription?.toString() == localized(label) }
        fun scroll(label: String) {
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) repeat(15) {
                if (find(label) != null) return
                nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }
                    ?.performAction(direction)
                i.waitForIdleSync(); Thread.sleep(100)
            }
            error("Report control missing: $label")
        }
        fun tap(label: String) {
            scroll(label)
            var target: AccessibilityNodeInfo? = find(label)
            var clicked = false
            repeat(6) {
                if (!clicked && target?.isClickable == true) clicked = target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) target = target?.parent
            }
            assertTrue("Clickable report control: $label", clicked)
            i.waitForIdleSync(); Thread.sleep(300)
        }
        try {
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scenario ->
                var closed = false
                var window: android.view.Window? = null
                scenario.onActivity { activity ->
                    window = activity.window
                    activity.setContent {
                        MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                            CompositionLocalProvider(LocalNativeActions provides actions, LocalNumbersHidden provides false) {
                                IssueReportScreen("Share details") { closed = true }
                            }
                        }
                    }
                }
                i.waitForIdleSync(); Thread.sleep(500)
                fun capture(name: String) {
                    i.waitForIdleSync()
                    readerFixtureScreenshot(checkNotNull(window), File(i.targetContext.filesDir, "report-$language-${if (dark) "dark" else "light"}-$name.png"))
                }
                capture("form")
                tap("Bug or broken feature")
                tap("Incorrect or missing data")
                var field: AccessibilityNodeInfo? = null
                for (attempt in 0 until 50) {
                    field = nodes().firstOrNull { node -> node.isEditable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } }
                    if (field != null) break
                    nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    i.waitForIdleSync(); Thread.sleep(100)
                }
                assertNotNull("Description field is editable after category selection", field)
                assertTrue(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Synthetic report: closing price looks incorrect & needs review.")
                }))
                i.waitForIdleSync(); Thread.sleep(150)
                tap("Include app details")
                tap("Review report")
                capture("review-without-details")
                tap("Open email draft")
                assertEquals(1, calls)
                assertTrue(reportBody.contains("Synthetic report:"))
                assertFalse(reportBody.contains("Android"))
                assertFalse(reportBody.contains("Screen:"))
                scroll("No email app could be opened. Copy the report and email it to support@structcapital.net.")
                tap("Copy report")
                scroll("Report copied. Paste it into an email to support@structcapital.net.")
                tap("Edit report")
                tap("Include app details")
                tap("Review report")
                capture("review")
                tap("Open email draft")
                assertEquals(2, calls)
                assertTrue(reportBody.contains("Android"))
                scroll("Email draft opened. Send it from your email app; opening the draft does not send the report.")
                tap("Edit report")
                tap("Back")
                scroll("Discard this report?")
                tap("Keep editing")
                assertFalse(closed)
                tap("Back")
                tap("Discard report")
                assertTrue(closed)
            }
        } finally { AppLanguage.select(previousLanguage) }
    }
}
