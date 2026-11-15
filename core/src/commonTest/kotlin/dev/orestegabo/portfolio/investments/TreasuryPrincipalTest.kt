package dev.orestegabo.portfolio.investments

import kotlin.test.*

class TreasuryPrincipalTest {
    @Test fun finitePositiveExactIncrementsAreRequired() {
        assertEquals(100_000L, TREASURY_PRINCIPAL_INCREMENT)
        for (amount in listOf(100_000.0, 200_000.0, 2_200_000.0)) assertTrue(validTreasuryPrincipal(amount))
        for (amount in listOf(-100_000.0, -0.0, 0.0, 99_999.99, 150_000.0, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) assertFalse(validTreasuryPrincipal(amount))
    }

    @Test fun validationDoesNotRoundNearMultiplesOrIntroduceAContextSpecificUpperLimit() {
        assertFalse(validTreasuryPrincipal(100_000.004))
        assertFalse(validTreasuryPrincipal(199_999.999))
        assertFalse(validTreasuryPrincipal(200_000.001))
        assertTrue(validTreasuryPrincipal(10_000_000_000_000.0))
        assertTrue(validTreasuryPrincipal(10_000_000_100_000.0))
    }
}
