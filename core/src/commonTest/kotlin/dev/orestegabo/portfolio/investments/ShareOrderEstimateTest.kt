package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ShareOrderEstimateTest {
    @Test fun brokerRequestExampleIncludesTheFixedCommission() {
        val estimate=shareOrderEstimate(1000.0,720.0)
        assertEquals(720000.0,estimate.gross)
        assertEquals(12312.0,estimate.commission)
        assertEquals(732312.0,estimate.total)
    }
    @Test fun feesRoundAsDecimalMoneyAndInvalidQuantitiesAreRejected() {
        assertEquals(0.02,shareOrderEstimate(1.0,1.0).commission)
        assertEquals(1.02,shareOrderEstimate(1.0,1.0).total)
        for (quantity in listOf(0.0,-1.0,1.5,Double.NaN,Double.POSITIVE_INFINITY)) assertFailsWith<IllegalArgumentException> { shareOrderEstimate(quantity,720.0) }
        for (price in listOf(0.0,-1.0,1.001,Double.NaN)) assertFailsWith<IllegalArgumentException> { shareOrderEstimate(1000.0,price) }
    }
}
