package dev.orestegabo.portfolio.investments

import kotlin.test.*

class OnboardingTest {
    @Test fun requestedIntroductionDoesNotDependOnNetworkOrSessionResolution() {
        assertTrue(shouldShowIntroduction(requested = true, locked = false))
        assertFalse(shouldShowIntroduction(requested = false, locked = false))
    }

    @Test fun introductionNeverReplacesDeviceUnlock() {
        assertFalse(shouldShowIntroduction(requested = true, locked = true))
        assertFalse(shouldShowIntroduction(requested = false, locked = true))
    }
}
