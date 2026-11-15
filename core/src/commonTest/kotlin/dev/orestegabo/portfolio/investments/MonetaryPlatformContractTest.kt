package dev.orestegabo.portfolio.investments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exact public Double/wire results, not platform-specific error tolerances. */
class MonetaryPlatformContractTest {
    @Test fun roundedMoneySumsAndProportionalCostsUseTheSameCanonicalDouble() {
        assertEquals(102523.68, roundedMoney(102523.68))
        assertEquals(102523.68, sumMoney(listOf(29292.48, 73231.2)))
        assertEquals(102523.68, proportionalCost(256309.2, 40.0, 100.0))
        assertEquals("{\"amount\":102523.68}", JsonRecord().put("amount", sumMoney(listOf(29292.48, 73231.2))).toString())
    }

    @Test fun halfUpRoundingTaxAndAddendsKeepTheirExistingOrder() {
        assertEquals(1.01, roundedMoney(1.005))
        assertEquals(-1.01, roundedMoney(-1.005))
        assertEquals(.03, sumMoney(listOf(.015, .015)))
        assertEquals(54.66, decimalAmount(listOf(1000.0, .115), divisor = 2, taxRate = .05, addends = listOf(.015, .015)))
    }

    @Test fun invalidMoneyInputsKeepControlledFailures() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { roundedMoney(value) }
            assertFailsWith<IllegalArgumentException> { proportionalCost(value, 1.0, 100.0) }
        }
        assertFailsWith<IllegalArgumentException> { decimalAmount(listOf(1.0), divisor = 0) }
        assertFailsWith<IllegalArgumentException> { decimalAmount(listOf(1.0), taxRate = 1.1) }
        assertFailsWith<IllegalArgumentException> { proportionalCost(1.0, 1.0, 0.0) }
    }
}
