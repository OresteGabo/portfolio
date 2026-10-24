package dev.orestegabo.portfolio.investments

import kotlin.test.*

class NumberPrivacyTest {
    @Test fun amountsQuantitiesAndRatesUseFixedMasksWithoutDisclosingLength() {
        assertEquals("RWF ••••", maskFinancialNumbers("RWF 340,728"))
        assertEquals("RWF ••••", maskFinancialNumbers("RWF −9,999,999,999"))
        assertEquals("•••• shares · Cost RWF ••••", maskFinancialNumbers("500 shares · Cost RWF 340,728"))
        assertEquals("••••", maskFinancialNumbers("+2.4%"))
        assertEquals("USD ••••", maskFinancialNumbers("USD 123.45"))
        assertEquals("RWF ••••", maskFinancialNumbers("RWF 1 000 000"))
    }
    @Test fun datesAndNonNumericLabelsRemainUsefulButAccountNumbersAreConcealed() {
        assertEquals("Missing PDF · 2026-10-24 · RWF ••••", maskFinancialNumbers("Missing PDF · 2026-10-24 · RWF 1200000"))
        assertEquals("2026-10-24", maskFinancialNumbers("2026-10-24"))
        assertEquals("Account AC••••", maskFinancialNumbers("Account AC12345678"))
        assertEquals("Pending", maskFinancialNumbers("Pending"))
        assertEquals("Unavailable", maskFinancialNumbers("Unavailable"))
    }
    @Test fun displayMaskingCannotMutateAmountsUsedForCalculations() {
        val amount = 340728.0
        val formatted = money(amount)
        maskFinancialNumbers(formatted)
        assertEquals("RWF 340,728", formatted)
        assertEquals(340728.0, amount)
    }
}
