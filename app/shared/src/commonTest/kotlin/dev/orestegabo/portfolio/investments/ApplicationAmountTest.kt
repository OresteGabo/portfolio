package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ApplicationAmountTest {
    @Test fun amountMovesInOneHundredThousandStepsAndStopsAtLimits() {
        assertEquals("200000", steppedApplicationAmount("100000", true))
        assertEquals("100000", steppedApplicationAmount("200000", false))
        assertEquals("100000", steppedApplicationAmount("100000", false))
        assertEquals(MaximumApplicationAmount.toString(), steppedApplicationAmount(MaximumApplicationAmount.toString(), true))
        assertEquals("200000", steppedApplicationAmount("", true))
        assertEquals("100000", steppedApplicationAmount("", false))
        assertEquals("250000", steppedApplicationAmount("150000", true))
    }
}
