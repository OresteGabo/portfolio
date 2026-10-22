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
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun bondPdf(scanned: Boolean): ByteArray {
        val text = """
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
