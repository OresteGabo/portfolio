package dev.orestegabo.portfolio.investments

import kotlin.test.*

class LegalContentTest {
    @Test fun bothLanguagesExplainSharedTradeDataAndDoNotPromiseAnonymity() {
        val privacy = legalDocuments.single { it.id == "privacy" }
        val en = privacy.sections.joinToString(" ") { it.body.en }
        val fr = privacy.sections.joinToString(" ") { it.body.fr }
        assertTrue(en.contains("other invited users"))
        assertTrue(en.contains("not a guarantee of anonymity"))
        assertTrue(en.contains("no in-app opt-out"))
        assertTrue(fr.contains("autres utilisateurs invités"))
        assertTrue(fr.contains("l’anonymat n’est pas garanti"))
        assertTrue(fr.contains("aucun refus", ignoreCase = true))
    }
    @Test fun deletionAndExportsDescribeTheirActualLimits() {
        val data = legalDocuments.single { it.id == "data" }.sections.joinToString(" ") { it.body.en }
        assertTrue(data.contains("Original purchase PDFs are not embedded"))
        assertTrue(data.contains("main owner"))
        assertTrue(data.contains("password-manager"))
        assertTrue(data.contains("active application storage"))
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
