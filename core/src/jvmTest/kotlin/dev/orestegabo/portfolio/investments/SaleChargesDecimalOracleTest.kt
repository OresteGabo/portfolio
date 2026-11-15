package dev.orestegabo.portfolio.investments

import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SaleChargesDecimalOracleTest {
    @Test fun comparisonMatchesIndependentBigDecimalOracleIncludingExtremeExponents() {
        val boundary = listOf(0.0, -0.0, Double.MIN_VALUE, 1e-300, .004, .005, .1, .2, .3,
            1.005, 10_000_000_000_000.01, 1e15, 1e300, Double.MAX_VALUE)
        fun verify(fees: Double, tax: Double, gross: Double) {
            val expected = BigDecimal.valueOf(fees) + BigDecimal.valueOf(tax) <= BigDecimal.valueOf(gross)
            assertEquals(expected, saleChargesWithinProceeds(fees, tax, gross), "$fees + $tax <= $gross")
        }
        for (fees in boundary) for (tax in boundary) for (gross in boundary) verify(fees, tax, gross)
        val random = Random(20261115)
        repeat(2000) {
            val fees = random.nextDouble() * 1e13
            val tax = random.nextDouble() * 1e13
            val gross = fees + tax
            verify(fees, tax, gross)
            verify(fees, tax, Double.fromBits(gross.toBits() + 1))
            verify(fees, tax, Double.fromBits(gross.toBits() - 1))
        }
    }
}
