package dev.orestegabo.portfolio.investments

import kotlin.test.*

class IssueReportTest {
    private val details = IssueReportDetails("Share details", "1.2 (34)", "Android 16 (API 36)", "en")

    @Test fun requiresUsefulDescriptionAndBoundsAllFreeText() {
        assertFalse(IssueReportDraft().valid)
        assertFalse(IssueReportDraft(description = "    short    ").valid)
        assertTrue(IssueReportDraft(description = "Price looks wrong").valid)
        assertFalse(IssueReportDraft(description = "x".repeat(2001)).valid)
        assertFalse(IssueReportDraft(description = "Price looks wrong", steps = "x".repeat(1001)).valid)
        assertFalse(IssueReportDraft(description = "Price looks wrong", expected = "x".repeat(1001)).valid)
    }

    @Test fun diagnosticsAreOptionalAndNeverReadPortfolioOrCredentials() {
        val draft = IssueReportDraft(IssueCategory.DATA, "The displayed price looks wrong", "Open a share", "The published closing price")
        val without = issueReportBody(draft, null, "en")
        val with = issueReportBody(draft, details, "en")
        assertTrue(without.contains(draft.description))
        assertTrue(without.contains(draft.steps))
        assertTrue(without.contains(draft.expected))
        assertFalse(without.contains(details.version))
        assertFalse(without.contains(details.platform))
        assertFalse(without.contains("Screen:"))
        assertTrue(with.contains("Screen: Share details"))
        assertTrue(with.contains("App version: 1.2 (34)"))
        assertTrue(with.contains("Platform: Android 16 (API 36)"))
    }

    @Test fun financialDocumentTitlesAndUnknownRoutesAreNotIncluded() {
        assertEquals("PDF viewer", issueReportScreenName("Private broker contract 123456.pdf", true))
        assertEquals("App", issueReportScreenName("Private broker contract 123456.pdf"))
        assertEquals("Bond details", issueReportScreenName("Bond details"))
        assertEquals("Sign in", issueReportScreenName("Sign in"))
    }

    @Test fun frenchReportKeepsUserTextVerbatim() {
        val body = issueReportBody(IssueReportDraft(IssueCategory.DATA, "Le cours affiché est incorrect & différent"), details, "fr")
        assertTrue(body.contains("Le cours affiché est incorrect & différent"))
        assertTrue(body.contains("Signaler un problème"))
        assertTrue(body.contains("Données incorrectes ou manquantes"))
        assertFalse(body.contains("Steps to reproduce"))
    }

    @Test fun mailtoCannotInjectRecipientsOrHeadersAndPreservesSpecialText() {
        val uri = supportEmailUri(BrandContacts.SUPPORT, "Bug & data?", "first line\nÉquité + 10% & wrong? #value")
        assertTrue(uri.startsWith("mailto:${BrandContacts.SUPPORT}?subject="))
        assertTrue(uri.contains("%26"))
        assertTrue(uri.contains("%0A"))
        assertTrue(uri.contains("%2B"))
        assertTrue(uri.contains("%23"))
        assertFalse(uri.contains("\n"))
        assertEquals(2, uri.count { it == '&' } + 1)
        assertFailsWith<IllegalArgumentException> { supportEmailUri("other@example.test", "Bug", "body") }
        assertFailsWith<IllegalArgumentException> { supportEmailUri(BrandContacts.SUPPORT, "Bug\r\nBcc: other@example.test", "body") }
    }
}
