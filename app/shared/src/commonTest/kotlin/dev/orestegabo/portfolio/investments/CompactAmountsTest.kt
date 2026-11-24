package dev.orestegabo.portfolio.investments

import kotlin.test.*

class CompactAmountsTest {
    private fun english(block: () -> Unit) {
        val previous = AppLanguage.code
        try { AppLanguage.select("en"); block() } finally { AppLanguage.select(previous) }
    }

    @Test fun millionBillionAndTrillionUseExactlyTwoPlaces() = english {
        assertEquals("4.56M", compactNumber(4_560_000.0))
        assertEquals("1.00M", compactNumber(1_000_000.0))
        assertEquals("1.25B", compactNumber(1_250_000_000.0))
        assertEquals("2.34T", compactNumber(2_340_000_000_000.0))
        assertEquals("1000.00T", compactNumber(1_000_000_000_000_000.0))
        assertEquals("USD 4.56M", compactMoney(4_560_000.0, "USD"))
    }

    @Test fun ordinaryAmountsAndExactMoneyRemainUnchanged() = english {
        for (amount in listOf(0.0, -0.001, 1.005, 36_615.6, 999_999.99)) {
            assertEquals(formatNumber(amount), compactNumber(amount))
        }
        assertEquals("RWF 4,560,000.12", money(4_560_000.1234))
        assertEquals("RWF 4.56M", compactMoney(4_560_000.1234))
    }

    @Test fun decimalHalfUpRoundingAndUnitCarryHaveSymmetricNegativeResults() = english {
        assertEquals("4.57M", compactNumber(4_565_000.0))
        assertEquals("4.56M", compactNumber(4_564_999.999))
        assertEquals("−4.57M", compactNumber(-4_565_000.0))
        assertEquals("999.99M", compactNumber(999_994_999.0))
        assertEquals("1.00B", compactNumber(999_995_000.0))
        assertEquals("1.00T", compactNumber(999_995_000_000.0))
        assertEquals("1000.00T", compactNumber(999_995_000_000_000.0))
    }

    @Test fun frenchUsesTheExistingDecimalCommaWithTheSameUnitMeanings() {
        val previous = AppLanguage.code
        try {
            AppLanguage.select("fr")
            assertEquals("RWF 4,56M", compactMoney(4_560_000.0))
            assertEquals("1,25B", compactNumber(1_250_000_000.0))
            assertEquals("−2,34T", compactNumber(-2_340_000_000_000.0))
            assertEquals("36\u202f615,6", compactNumber(36_615.6))
        } finally { AppLanguage.select(previous) }
    }

    @Test fun veryLargeFiniteValuesDoNotOverflowAndNonFiniteIsUnavailable() = english {
        assertTrue(Regex("[0-9]+\\.[0-9]{2}T").matches(compactNumber(Double.MAX_VALUE)))
        assertEquals("Unavailable", compactNumber(Double.NaN))
        assertEquals("Unavailable", compactNumber(Double.POSITIVE_INFINITY))
    }

    @Test fun formattingNeverChangesTheFinancialRecordOrValuesUsedInCalculations() = english {
        val record = JsonRecord().put("amount", 4_565_123.4567).put("couponRate", .1275)
        val before = record.toString()
        repeat(100) { compactMoney(record.getDouble("amount")) }
        assertEquals(before, record.toString())
        assertEquals(4_565_123.4567, record.getDouble("amount"))
    }

    @Test fun hiddenAmountsConcealTheUnitAsWellAsDigitsInBothLanguages() {
        val previous = AppLanguage.code
        try {
            for (language in listOf("en", "fr")) {
                AppLanguage.select(language)
                for (value in listOf(4_560_000.0, -1_250_000_000.0, 2_340_000_000_000.0)) {
                    assertEquals("RWF ••••", maskFinancialNumbers(compactMoney(value)))
                    assertEquals("••••", maskFinancialNumbers(compactNumber(value)))
                }
            }
        } finally { AppLanguage.select(previous) }
    }
}
