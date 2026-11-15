package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ClosingPriceFeedbackTest {
    @Test fun loadingAndRefreshingAreDistinctAndRetainedPricesAreNotPresentedAsFreshAfterFailure() {
        assertEquals("Loading closing prices…", closingPriceFeedback(false, true, false))
        assertEquals("Updating closing prices…", closingPriceFeedback(true, true, true))
        assertEquals("Closing prices unavailable.", closingPriceFeedback(false, false, true))
        assertEquals("Closing prices unavailable.", closingPriceFeedback(false, false, false))
        assertEquals("Using previously loaded closing prices.", closingPriceFeedback(true, false, true))
        assertNull(closingPriceFeedback(true, false, false))
    }
}
