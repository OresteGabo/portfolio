package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class AppLanguageTest {
    private class Preferences(var language: String? = null) : ServerSettings {
        override fun readLanguage() = language
        override fun writeLanguage(language: String) { this.language = language }
        override fun readOrigin(): String? = null
        override fun writeOrigin(origin: String) {}
    }
    @AfterTest fun restoreEnglish() {
        AppLanguage.initialize(Preferences("en"))
        AppLanguage.systemLanguage = "en"
    }
    @Test fun systemLanguageHandlesRegionsAndFallsBackForUnsupportedLanguages() {
        assertEquals("fr", resolveLanguage("system", "fr-RW"))
        assertEquals("fr", resolveLanguage("system", "fr_CA"))
        assertEquals("en", resolveLanguage("system", "rw-RW"))
        assertEquals("en", resolveLanguage("en", "fr-FR"))
        assertEquals("fr", resolveLanguage("fr", "en-US"))
    }
    @Test fun selectionPersistsAndSystemModeFollowsSubsequentDeviceChanges() {
        val preferences = Preferences()
        AppLanguage.initialize(preferences)
        AppLanguage.systemLanguage = "fr-FR"
        assertEquals("Portefeuille", localized("Portfolio"))
        AppLanguage.select("en")
        assertEquals("en", preferences.language)
        assertEquals("Portfolio", localized("Portfolio"))
        AppLanguage.initialize(preferences)
        assertEquals("en", AppLanguage.code)
        AppLanguage.select("system")
        assertEquals("fr", AppLanguage.code)
        AppLanguage.systemLanguage = "en-GB"
        assertEquals("en", AppLanguage.code)
        assertFailsWith<IllegalArgumentException> { AppLanguage.select("invalid") }
    }
    @Test fun sentenceTemplatesPreserveNamesAmountsAndUrlsWithoutTranslatingData() {
        assertEquals("Supprimer définitivement test@example.test, ses investissements, documents et sessions actives. Cette action est irréversible. Les fichiers téléchargés et les entrées du gestionnaire de mots de passe restent sur votre appareil.",
            localized("Permanently delete test@example.test, its investments, documents and active sessions. This cannot be undone. Files you downloaded and entries in your phone’s password manager remain on your device.", "fr"))
        assertEquals("500 actions · Coût total RWF 46\u202f000", localized("500 shares · Total cost RWF 46\u202f000", "fr"))
        assertEquals("Télécharger contrat-2026.pdf", localized("Download contrat-2026.pdf", "fr"))
        listOf("I&M Bank Rwanda", "RW000A2XYZ", "2026-10-24", "/api/bonds/purchases", "https://example.test/portfolio").forEach {
            assertEquals(it, localized(it, "fr"))
        }
    }
    @Test fun frenchAmountsAndCalendarWorkWithPrivacyAndPreserveRecordedFractions() {
        AppLanguage.initialize(Preferences("fr"))
        assertEquals("RWF 1\u202f234\u202f567,99", money(1234567.99))
        assertEquals("1\u202f234,5", formatNumber(1234.5))
        assertEquals("12,35\u00a0%", percent(.1235))
        assertEquals("RWF ••••", maskFinancialNumbers(money(1234567.99)))
        assertEquals("••••\u00a0%", maskFinancialNumbers(percent(.1235)))
        assertEquals("samedi, 24 octobre 2026", LocalDate(2026, 10, 24).calendarLabel())
        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), calendarWeekdayLabels())
    }
    @Test fun frenchKeyboardInputRemainsNumericAndAmbiguousSeparatorsAreRejected() {
        assertEquals(12.35, "12,35".toInputNumberOrNull())
        assertEquals(1234567.5, "1\u202f234\u202f567,5".toInputNumberOrNull())
        assertEquals(12.35, "12.35".toInputNumberOrNull())
        assertNull("1,234.5".toInputNumberOrNull())
        assertNull("12,3,4".toInputNumberOrNull())
    }
    @Test fun everyTemplateRetainsItsArgumentsAndCatalogValuesAreNonempty() {
        val placeholder = Regex("\\{\\d+\\}")
        frenchStrings.forEach { (source, translation) ->
            assertTrue(translation.isNotBlank(), source)
            assertEquals(placeholder.findAll(source).map { it.value }.toSet(), placeholder.findAll(translation).map { it.value }.toSet(), source)
        }
    }
}
