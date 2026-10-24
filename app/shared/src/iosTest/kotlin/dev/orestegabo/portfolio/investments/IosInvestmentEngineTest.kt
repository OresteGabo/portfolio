@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import platform.Foundation.*
import platform.UIKit.*
import platform.CoreGraphics.*
import kotlin.test.*

class IosInvestmentEngineTest {
    @Test fun originalProjectionRunsInJavaScriptCore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = IosInvestmentEngine()
        try {
            val output = withContext(Dispatchers.Default) { engine.run("projection", JsonRecord().put("assumptions", JsonRecord().put("horizonYears", 1).put("auctionFillRate", 0).put("contributionPeriods", JsonList()))) }
            assertEquals(12, output.getJSONArray("rows").length())
            assertEquals(3_600_000.0, output.getJSONObject("summary").getDouble("finalAccountValue"), .01)
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }
    @Suppress("CAST_NEVER_SUCCEEDS") // Kotlin/Native bridges String to NSString for UIKit drawing.
    @Test fun nativePdfKitFeedsTheSharedPurchaseParser() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = IosInvestmentEngine()
        val pdf = bondPdf(scanned = false)

        try {
            val result = withContext(Dispatchers.Default) { engine.run("pdf", pdf = pdf) }
            assertEquals("bond", result.text("kind"))
            assertEquals(1_000_000.0, result.getJSONObject("fields").getDouble("faceValue"), .01)
            assertEquals(.12, result.getJSONObject("fields").getDouble("couponRate"), .0001)
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }
    @Test fun scannedPdfUsesNativeVisionThenTheSameSharedParser() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = IosInvestmentEngine()
        try {
            val pdf = bondPdf(scanned = true)
            val result = withContext(Dispatchers.Default) { engine.run("pdf", pdf = pdf) }
            assertTrue(result.optBoolean("usedOcr"))
            assertEquals("bond", result.text("kind"))
            assertEquals(.12, result.getJSONObject("fields").getDouble("couponRate"), .0001)
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }
    @Test fun scannedTableKeepsSeparateLabelAndValueColumnsTogether() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = IosInvestmentEngine()
        try {
            val result = withContext(Dispatchers.Default) { engine.run("pdf", pdf = scannedTablePdf()) }
            assertEquals("bond", result.text("kind"))
            assertTrue(result.optBoolean("usedOcr"))
            val fields = result.getJSONObject("fields")
            assertEquals("FXD1/2026/7YR", fields.text("bondName"))
            assertEquals(99.0, fields.getDouble("pricePercent"), .01)
            assertEquals(.12, fields.getDouble("couponRate"), .0001)
            assertEquals("2026-07-17", fields.text("settlementDate"))
            assertEquals("2033-07-08", fields.text("maturityDate"))
            assertEquals("2027-01-17", fields.text("firstCouponDate"))
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }

    @Test fun nativePdfKitRecognizesStatementWithoutTurningDepositsIntoPurchases() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = IosInvestmentEngine()
        try {
            val text = """
                BK CAPITAL
                CLIENT STATEMENT
                From Date : 01-Jul-2026
                To Date : 30-Sep-2026
                Account Summary Balance (RWF)
                Opening Balance As On 01-07-2026 0.00 0.00
                14-Jul-2026 Receipt DEMO 0.00 1,511,000.00
                17-Jul-2026 Journal 1 July Bond Cost 1,500,000.00 0.00
                17-Jul-2026 Journal 1 Bond Fee 5,000.00 0.00
                Closing Balance As On 30-Sep-2026 : 100.00 Cr
            """.trimIndent()
            val result = withContext(Dispatchers.Default) { engine.run("pdf", pdf = bondPdf(false, text)) }
            assertEquals("statement", result.text("kind"))
            assertEquals(1, result.rows("rows").size)
            assertEquals("bond", result.rows("rows").single().text("kind"))
            assertFalse(result.rows("rows").single().has("faceValue"))
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }

    @Test fun unrecognizedPdfShowsAnActionableErrorWithoutItsContents() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = IosInvestmentEngine()
        try {
            val pdf = bondPdf(false, "Unrelated PRIVATE_TEST_SENTINEL document. This is a long ordinary page with enough text for native extraction, but no investment results or purchase information.")
            val failure = assertFailsWith<IllegalStateException> { withContext(Dispatchers.Default) { engine.run("pdf", pdf = pdf) } }
            assertTrue(failure.message.orEmpty().contains("Could not recognize"))
            assertFalse(failure.message.orEmpty().contains("PRIVATE_TEST_SENTINEL"))
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun scannedTablePdf(): ByteArray {
        val rows = listOf("ISSUER" to "GOVERNMENT OF RWANDA", "ISSUANCE NUMBER" to "FXD1/2026/7YR",
            "BOND CODE" to "RWTEST000007/MN", "AMOUNT" to "RWF 10 billion", "TENOR" to "7 Years",
            "PRICE" to "99", "Coupon Interest rate" to "12.00%", "COUPON PAYMENT DATES" to "17-Jan-2027 17-Jul-2027",
            "SETTLEMENT DATE" to "17/07/2026", "REDEMPTION DATE" to "08/07/2033")
        val image = UIGraphicsImageRenderer(size = CGSizeMake(1100.0, 1400.0)).imageWithActions {
            UIColor.whiteColor.setFill(); UIRectFill(CGRectMake(0.0, 0.0, 1100.0, 1400.0))
            val style: Map<Any?, Any?> = mapOf(NSFontAttributeName to UIFont.systemFontOfSize(18.0))
            ("NATIONAL BANK OF RWANDA" as NSString).drawAtPoint(CGPointMake(40.0, 80.0), withAttributes = style)
            rows.forEachIndexed { index, (label, value) ->
                (label as NSString).drawAtPoint(CGPointMake(40.0, 200.0 + index * 40.0), withAttributes = style)
                (value as NSString).drawAtPoint(CGPointMake(530.0, 200.0 + index * 40.0), withAttributes = style)
            }
            ("1. INVESTOR RESULTS" as NSString).drawAtPoint(CGPointMake(40.0, 760.0), withAttributes = style)
            ("Investor Bank Applied Amount Allocation Settlement Amount Coupon Amount" as NSString).drawAtPoint(CGPointMake(40.0, 810.0), withAttributes = style)
            ("TEST INVESTOR BK CAPITAL 2,000,000 1,000,000 990,000 60,000" as NSString).drawAtPoint(CGPointMake(40.0, 860.0), withAttributes = style)
        }
        return UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, 1100.0, 1400.0)).PDFDataWithActions { output ->
            output!!.beginPage(); image.drawInRect(CGRectMake(0.0, 0.0, 1100.0, 1400.0))
        }.toBytes()
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun bondPdf(scanned: Boolean, textOverride: String? = null): ByteArray {
        val text = textOverride ?: """
            NATIONAL BANK OF RWANDA
            ISSUANCE NUMBER FXD1/2026/20YR
            BOND CODE RWTEST000001/MN
            AMOUNT RWF 10 billion
            TENOR 20 Years
            PRICE 98.5
            Coupon Interest rate 12.00%
            SETTLEMENT DATE 14/08/2026
            REDEMPTION DATE 01/08/2046
            1. INVESTOR RESULTS
            Investor's name Bank Applied Amount (FRW) Allocation Settlement Amount Coupon Amount
            TEST INVESTOR BK CAPITAL 2,000,000 1,000,000 985,000 60,000
        """.trimIndent()

        val image = if (scanned) {
            UIGraphicsImageRenderer(size = CGSizeMake(1100.0, 1400.0)).imageWithActions {
                UIColor.whiteColor.setFill()
                UIRectFill(CGRectMake(0.0, 0.0, 1100.0, 1400.0))
                text.lines().forEachIndexed { index, line ->
                    (line as NSString).drawAtPoint(CGPointMake(30.0, 40.0 + index * 30.0), withAttributes = mapOf(NSFontAttributeName to UIFont.systemFontOfSize(16.0)))
                }
            }
        } else null
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, 1100.0, 1400.0))
        val pdf = renderer.PDFDataWithActions { output ->
            output!!.beginPage()
            if (image != null) image.drawInRect(CGRectMake(0.0, 0.0, 1100.0, 1400.0)) else text.lines().forEachIndexed { index, line ->
                (line as NSString).drawAtPoint(CGPointMake(30.0, 40.0 + index * 30.0), withAttributes = mapOf(NSFontAttributeName to UIFont.systemFontOfSize(16.0)))
            }
        }
        return pdf.toBytes()
    }

}
