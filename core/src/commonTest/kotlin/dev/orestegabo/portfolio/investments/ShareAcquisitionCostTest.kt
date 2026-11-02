package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ShareAcquisitionCostTest {
    @Test fun feesAreAddedOnceForTheReferencePurchases() {
        assertEquals(ShareAcquisitionCost(36000.0,615.60,36615.60),shareAcquisitionCost(400.0,90.0,listOf(600.0,15.60)))
        assertEquals(ShareAcquisitionCost(335000.0,5728.50,340728.50),shareAcquisitionCost(500.0,670.0,listOf(5728.50)))
    }
    @Test fun decimalMoneyUsesTheExistingHalfUpBoundary() {
        assertEquals(ShareAcquisitionCost(1.01,.03,1.04),shareAcquisitionCost(1.0,1.005,listOf(.015,.015)))
    }
    @Test fun invalidCountsAndCostsDoNotCreatePlausibleTotals() {
        for (shares in listOf(0.0,-1.0,.5,Double.NaN)) assertFailsWith<IllegalArgumentException> { shareAcquisitionCost(shares,90.0,emptyList()) }
        assertFailsWith<IllegalArgumentException> { shareAcquisitionCost(400.0,90.0,listOf(-1.0)) }
        assertFailsWith<IllegalArgumentException> { shareAcquisitionCost(400.0,Double.POSITIVE_INFINITY,emptyList()) }
    }
}
