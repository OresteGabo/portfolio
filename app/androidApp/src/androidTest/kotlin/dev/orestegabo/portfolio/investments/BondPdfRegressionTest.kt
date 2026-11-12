package dev.orestegabo.portfolio.investments

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in local diagnosis; the supplied original is never bundled, logged or uploaded. */
class BondPdfRegressionTest {
    @Test fun suppliedConfirmationReachesValidatedReviewWithoutSaving() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Supply a private confirmation fixture explicitly", args.getString("privateBondPdf") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.filesDir, "bond-import-regression.pdf")
        assertTrue("Private fixture missing", fixture.isFile)
        val bytes = fixture.readBytes()
        val scenario = ActivityScenario.launch<EngineHostActivity>(Intent(context, EngineHostActivity::class.java)
            .putExtra("privateDocuments", true))
        lateinit var engine: LocalInvestmentEngine
        scenario.onActivity { engine = it.engine }
        try {
            val review = engine.run("pdf", pdf = bytes)
            assertEquals("bond", review.text("kind"))
            val missing = requireNotNull(review.optJSONArray("missingFields"))
            assertEquals("Missing required fields: $missing", 0, missing.length())
            val input = importedPurchaseInput(review)
            if (args.getString("privateReviewOutput") == "true") {
                File(context.filesDir, "bond-import-regression-review.json").writeText(review.toString())
            }
            args.getString("expectedCoupons")?.toInt()?.let { count ->
                assertEquals("Incomplete coupon schedule", count, input.optJSONArray("couponDates")?.length())
            }
            assertTrue(input.optDouble("faceValue", 0.0) > 0)
            // No repository save or API request is performed by this test.
        } finally {
            bytes.fill(0)
            scenario.close()
        }
    }
}
