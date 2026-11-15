package dev.orestegabo.portfolio.investments

import kotlin.test.*

class FinancialNumberBoundaryTest {
    @Test fun inputParsingKeepsSubCentPricesAndRejectsMixedSeparators() {
        assertEquals(1000.015, "1\u202f000,015".toInputNumberOrNull())
        assertEquals(1000.015, "1 000.015".toInputNumberOrNull())
        assertEquals(-.005, "-0,005".toInputNumberOrNull())
        assertNull("1,234.56".toInputNumberOrNull())
        assertNull("not a number".toInputNumberOrNull())
    }

    @Test fun displayRoundingDoesNotRewriteStoredValuesAndUsesExistingRwfFractions() {
        val preference = AppLanguage.preference
        try {
            AppLanguage.select("en")
            val record = JsonRecord().put("amount", .005)
            val original = record.toString()
            assertEquals("RWF 0.01", money(record.getDouble("amount")))
            assertEquals(original, record.toString())
            assertEquals(.005, JsonRecord(original).getDouble("amount"))
            assertEquals("RWF 10,000,000,000,000.01", money(10_000_000_000_000.01))
            assertEquals("RWF −1.01", money(-1.005))
            assertEquals("RWF 0", money(-.004))
        } finally { AppLanguage.select(preference) }
    }

    @Test fun localizedMoneyAndPercentDoNotChangeTheUnderlyingFractionalRate() {
        val preference = AppLanguage.preference
        try {
            AppLanguage.select("fr")
            assertEquals("RWF 1\u202f000,02", money(1000.015))
            val rate = .0171
            assertEquals("1,71\u00a0%", percent(rate))
            assertEquals(.0171, rate)
        } finally { AppLanguage.select(preference) }
    }
}
