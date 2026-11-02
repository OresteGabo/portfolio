package dev.orestegabo.portfolio.investments

import kotlin.test.*

class LegalContentTest {
    @Test fun bothLanguagesDescribePrivateContractHistoryAndOwnerPublishedPublicReports() {
        val privacy = legalDocuments.single { it.id == "privacy" }
        val en = privacy.sections.joinToString(" ") { it.body.en }
        val fr = privacy.sections.joinToString(" ") { it.body.fr }
        assertTrue(en.contains("authenticated account that saved"))
        assertTrue(en.contains("not other users’ private contracts"))
        assertTrue(en.contains("signed-in users can explicitly share public BNR issuance reports"))
        assertTrue(en.contains("Format validation is not independent verification by BNR"))
        assertTrue(en.contains("separate projection of public security facts"))
        assertTrue(en.contains("excludes investor identity, source PDF links"))
        assertFalse(en.contains("no in-app opt-out"))
        assertTrue(fr.contains("compte authentifié"))
        assertTrue(fr.contains("contrats privés des autres utilisateurs"))
        assertTrue(fr.contains("utilisateurs connectés peuvent partager explicitement"))
    }
    @Test fun deletionAndExportsDescribeTheirActualLimits() {
        val data = legalDocuments.single { it.id == "data" }.sections.joinToString(" ") { it.body.en }
        assertTrue(data.contains("Original purchase PDFs are not embedded"))
        assertTrue(data.contains("main owner"))
        assertTrue(data.contains("password-manager"))
        assertTrue(data.contains("active application storage"))
    }
    @Test fun guestPrivacyAndDeletionExplainConsentLocalRetentionAndSeparateAccountOwnership() {
        val privacy=legalDocuments.single { it.id=="privacy" }.sections.single { it.title.en=="Private Guest Mode" }
        assertTrue(privacy.body.en.contains("Signing in does not transfer guest data"))
        assertTrue(privacy.body.en.contains("not cloud-backed up"))
        assertTrue(privacy.body.en.contains("A selected PDF is sent in full"))
        assertTrue(privacy.body.en.contains("does not delete the separate guest workspace"))
        assertTrue(privacy.body.fr.contains("La connexion ne transfère pas"))
        assertTrue(privacy.body.fr.contains("ni sauvegardées dans le cloud"))
        val data=legalDocuments.single { it.id=="data" }.sections.first()
        assertTrue(data.body.en.contains("Account deletion does not erase guest data"))
        assertTrue(data.body.fr.contains("La suppression du compte n’efface pas les données invité"))
    }
    @Test fun allDocumentsHaveDistinctRoutesAndCompleteTranslations() {
        assertEquals(5, legalDocuments.map { it.id }.toSet().size)
        legalDocuments.forEach { doc ->
            assertNotEquals(doc.title.en, doc.title.fr)
            assertTrue(doc.sections.isNotEmpty())
            doc.sections.forEach { section ->
                assertTrue(section.title.en.isNotBlank() && section.title.fr.isNotBlank())
                assertTrue(section.body.en.isNotBlank() && section.body.fr.isNotBlank())
                assertNotEquals(section.body.en, section.body.fr)
            }
        }
    }
    @Test fun licenceReaderRemovesCommentWrappersButKeepsLegalTextAndBullets() {
        val source = "// Copyright Example\n//\n// * Redistributions must retain this notice.\n// https://example.test/licence"
        assertEquals("Copyright Example\n\n* Redistributions must retain this notice.\nhttps://example.test/licence", readableLicence(source))
        assertEquals("Copyright Example\nAll rights reserved.", readableLicence("/*\n * Copyright Example\n * All rights reserved.\n */"))
        val plain = "MIT License\n\nCopyright Example\n\nTHE SOFTWARE IS PROVIDED AS IS."
        assertEquals(plain, readableLicence(plain))
    }

}
