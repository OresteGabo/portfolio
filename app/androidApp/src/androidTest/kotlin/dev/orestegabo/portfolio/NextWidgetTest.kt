package dev.orestegabo.portfolio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NextWidgetTest {
    private val today = LocalDate.parse("2026-11-12")
    private fun issue(id: String, open: String, close: String, form: Boolean = false) = JSONObject()
        .put("id",id).put("title","Public Treasury bond").put("open",open).put("close",close).put("form",form)

    @Test fun activeWindowWinsAndExpiresAfterRwandaClosingDay() {
        val snapshot = JSONObject().put("issues",JSONArray(listOf(
            issue("old","2026-10-01","2026-10-05"),
            issue("active","2026-11-10","2026-11-12"),
            issue("future","2026-12-14","2026-12-16",true))))
            .put("payments",JSONArray().put(JSONObject().put("date","2026-11-12").put("kind","coupon")))
        val events = nextWidgetEvents(snapshot,today,false)
        assertEquals("issue:active",events.first().key)
        assertEquals("Closing today",events.first().status)
        assertEquals("Coupon payment",events[1].title)
        val tomorrow = nextWidgetEvents(snapshot,today.plusDays(1),false)
        assertEquals("issue:future",tomorrow.first().key)
        assertEquals("Next subscription",tomorrow.first().status) // December form isn't available in November.
        assertEquals("Form available",nextWidgetEvents(snapshot,LocalDate.parse("2026-12-01"),false).first().status)
    }

    @Test fun nearestPaymentBeatsDistantWindowAndMalformedDatesAreIgnored() {
        val snapshot = JSONObject().put("issues",JSONArray(listOf(
            issue("future","2026-12-14","2026-12-16"),issue("broken","invalid","2026-11-20"))))
            .put("payments",JSONArray().put(JSONObject().put("date","2026-11-15").put("kind","maturity")))
        val events = nextWidgetEvents(snapshot,today,true)
        assertEquals("Remboursement du capital",events.first().title)
        assertEquals(2,events.size)
    }

    @Test fun bothSizesInflateAndClearingTheCacheRemovesPreviousEvents() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = context.getSharedPreferences("portfolio.next-widget",0).all
        try {
            val day=LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
            val fixture=JSONObject().put("language","en").put("issues",JSONArray(listOf(issue("fixture",day.toString(),day.plusDays(2).toString()))))
            NextWidgetStore.save(context,fixture.toString())
            instrumentation.runOnMainSync {
                for(medium in listOf(false,true)) {
                    val view=NextWidgetStore.render(context,medium).apply(context,FrameLayout(context))
                    assertNotNull(view.findViewById<android.view.View>(R.id.next_title))
                }
            }
            val manager=AppWidgetManager.getInstance(context)
            assertNotNull(manager.getInstalledProvidersForPackage(context.packageName,null)
                .firstOrNull { it.provider==ComponentName(context,NextSmallWidget::class.java) })
            NextWidgetStore.save(context,null)
            assertEquals(0,NextWidgetStore.snapshot(context).length())
        } finally {
            val edit=context.getSharedPreferences("portfolio.next-widget",0).edit().clear()
            (before["snapshot"] as? String)?.let { edit.putString("snapshot",it) }
            (before["updated"] as? Long)?.let { edit.putLong("updated",it) }
            edit.commit();NextWidgetStore.updateAll(context)
        }
    }
}
