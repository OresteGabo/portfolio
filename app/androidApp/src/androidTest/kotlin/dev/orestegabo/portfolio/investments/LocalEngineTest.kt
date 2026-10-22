package dev.orestegabo.portfolio.investments

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalEngineTest {
    @Test fun localPdfEngineReadsSyntheticRsePurchase() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var engine: LocalInvestmentEngine
        val scenario = ActivityScenario.launch(EngineHostActivity::class.java)
        scenario.onActivity { engine = it.engine }
        val pdf = android.graphics.pdf.PdfDocument()
        val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(1000, 1400, 1).create())
        val paint = android.graphics.Paint().apply { textSize = 15f }
        val text = instrumentation.context.assets.open("equity-purchase.txt").bufferedReader().use { it.readText() }
        text.lines().forEachIndexed { index, line -> page.canvas.drawText(line, 35f, 35f + index * 25f, paint) }
        pdf.finishPage(page)
        val output = java.io.ByteArrayOutputStream()
        pdf.writeTo(output)
        pdf.close()
        try {
            val result = engine.run("pdf", pdf = output.toByteArray())
            assertEquals("equity", result.getString("kind"))
            assertEquals(500, result.getJSONObject("fields").getInt("shares"))
            assertEquals(340728.5, result.getJSONObject("fields").getDouble("netAmountPayable"), .01)
        } finally { scenario.close() }
    }

    @Test fun localEngineCalculatesWithNoRemoteNetwork() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var engine: LocalInvestmentEngine
        val scenario = ActivityScenario.launch(EngineHostActivity::class.java)
        scenario.onActivity { engine = it.engine }
        try {
            val result = engine.run("projection", JSONObject().put("assumptions", JSONObject()
                .put("horizonYears", 1).put("auctionFillRate", 0).put("contributionPeriods", org.json.JSONArray())))
            assertEquals(12, result.getJSONArray("rows").length())
            assertEquals(3_600_000.0, result.getJSONObject("summary").getDouble("finalAccountValue"), .01)
        } finally { scenario.close() }
    }
}
