package dev.orestegabo.portfolio.investments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsNavigationTest {
    private fun destinations(guest: Boolean = false, owner: Boolean = false, widgets: Boolean = true,
        push: Boolean = true, personalGuest: Boolean = false, canLock: Boolean = false) =
        settingsDestinations(guest, owner, personalGuest, widgets, push, !guest, canLock)

    @Test fun searchFindsLocalizedLabelsAndGoalAliasesWithoutExposingAmounts() {
        val pages = destinations()
        assertEquals(listOf("Investment goal"), searchSettings(pages, " YEARLY target ") { it }.map { it.label })
        assertEquals(listOf("Hide numbers", "Shake to hide numbers"), searchSettings(pages, "masquer montants") { localized(it, "fr") }.map { it.label })
        assertEquals(listOf("Language"), searchSettings(pages, "language") { it }.map { it.label })
        assertEquals(listOf("Investment goal"), searchSettings(pages, "goal") { it }.map { it.label })
        assertEquals(listOf("Protect screenshots & recordings"), searchSettings(pages, "privacy screenshots") { it }.map { it.label })
        assertTrue(searchSettings(pages, "3,600,000") { it }.isEmpty())
        assertTrue(searchSettings(pages, " ") { it }.isEmpty())
        assertTrue(searchSettings(pages, "not-a-setting") { it }.isEmpty())
    }

    @Test fun unavailableCapabilitiesNeverAppearInSearchOrNavigation() {
        val guest = destinations(guest = true, owner = true, personalGuest = true)
        assertTrue(guest.none { it.key in listOf("administration", "security", "account", "tools", "notifications") })
        assertEquals(listOf("Encrypted backup"), searchSettings(guest, "backup") { it }.map { it.label })
        assertTrue(searchSettings(destinations(guest = true), "backup") { it }.isEmpty())
        assertTrue(searchSettings(destinations(), "Accounts") { it }.isEmpty())
        assertEquals(listOf("Accounts"), searchSettings(destinations(owner = true), "Accounts") { it }.map { it.label })
        assertTrue(searchSettings(destinations(widgets = false, push = false), "What’s next") { it }.isEmpty())
        assertTrue(searchSettings(destinations(widgets = false, push = false), "Phone notifications") { it }.isEmpty())
        assertTrue(searchSettings(destinations(), "Lock app now") { it }.isEmpty())
        assertEquals(listOf("Lock app now"), searchSettings(destinations(canLock = true), "Lock app now") { it }.map { it.label })
    }
}
