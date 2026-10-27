package dev.orestegabo.portfolio.investments

import kotlin.test.*

class OnboardingTest {
    @Test fun firstRunWaitsForSessionResolutionAndNeverReplacesUnlock() {
        assertTrue(shouldShowIntroduction(true, false, false, false, false))
        assertFalse(shouldShowIntroduction(true, false, false, false, true))
        assertFalse(shouldShowIntroduction(true, false, true, false, false))
        assertFalse(shouldShowIntroduction(true, false, false, true, false))
        assertFalse(shouldShowIntroduction(false, false, false, false, false))
    }

    @Test fun explicitReplayIsAvailableToAnExistingUserWithoutSigningOut() {
        assertTrue(shouldShowIntroduction(true, true, true, false, false))
        assertFalse(shouldShowIntroduction(false, true, true, false, false))
    }
}
