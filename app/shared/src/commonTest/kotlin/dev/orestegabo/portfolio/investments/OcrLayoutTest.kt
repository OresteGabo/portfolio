package dev.orestegabo.portfolio.investments

import kotlin.test.*

class OcrLayoutTest {
    @Test fun pdfKitWordSelectionsRestoreStatementRowsWithOffsetNarrativeAndWrappedSuffix() {
        val words = listOf(OcrTextBlock("22-Jul-2026", 10.0, 100.0, 8.0),
            OcrTextBlock("Purchase", 70.0, 100.0, 8.0), OcrTextBlock("10000000001#", 130.0, 100.0, 8.0),
            OcrTextBlock("01", 130.0, 88.0, 8.0), OcrTextBlock("Purchase of IMR", 220.0, 102.5, 8.0),
            OcrTextBlock("18,400.00", 400.0, 100.0, 8.0), OcrTextBlock("0.00", 460.0, 100.0, 8.0))
        assertEquals("22-Jul-2026 Purchase 10000000001# Purchase of IMR 18,400.00 0.00\n01", layoutOcrText(words))
    }
    @Test fun pdfMoneySignsAndPrecisionArePreservedWithoutInsertingSpacesInsideTokens() {
        assertEquals("-1,500,000.00 0.00", layoutOcrText(listOf(
            OcrTextBlock("0.00", 400.0, 100.0, 8.0), OcrTextBlock("-1,500,000.00", 300.0, 100.0, 8.0))))
    }
    @Test fun columnFirstRecognitionRejoinsLabelsAndValuesWithoutMixingRows() {
        val boxes = listOf(
            OcrTextBlock("ISSUANCE NUMBER", .1, .8, .02), OcrTextBlock("BOND CODE", .1, .75, .02),
            OcrTextBlock("PRICE", .1, .70, .02), OcrTextBlock("FXD1/2026/7YR", .4, .801, .02),
            OcrTextBlock("RWTEST000007/MN", .4, .749, .02), OcrTextBlock("99", .4, .699, .02),
        )
        assertEquals("ISSUANCE NUMBER FXD1/2026/7YR\nBOND CODE RWTEST000007/MN\nPRICE 99", layoutOcrText(boxes))
    }
    @Test fun couponCellsAreSortedAcrossRowsAndKeepInvestorAmountsInColumnOrder() {
        val boxes = listOf(OcrTextBlock("COUPON PAYMENT", .1, .8, .02), OcrTextBlock("DATES", .1, .77, .02),
            OcrTextBlock("17-Jan-2027", .4, .8, .02), OcrTextBlock("17-Jan-2028", .4, .77, .02),
            OcrTextBlock("17-Jul-2027", .7, .8, .02), OcrTextBlock("17-Jul-2028", .7, .77, .02),
            OcrTextBlock("Allocation", .5, .4, .02), OcrTextBlock("1,000,000", .5, .35, .02),
            OcrTextBlock("Settlement", .7, .4, .02), OcrTextBlock("990,000", .7, .35, .02))
        assertEquals("COUPON PAYMENT 17-Jan-2027 17-Jul-2027\nDATES 17-Jan-2028 17-Jul-2028\nAllocation Settlement\n1,000,000 990,000", layoutOcrText(boxes))
    }
}
