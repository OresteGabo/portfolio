package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ImportJournalTest {
    private class Settings : ServerSettings {
        val histories = mutableMapOf<String, String>()
        var fail = false
        override fun readOrigin(): String? = null
        override fun writeOrigin(origin: String) {}
        override fun readImportHistory(account: String) = histories[account]
        override fun writeImportHistory(account: String, history: String) { if (fail) error("disk unavailable"); histories[account] = history }
    }
    @Test fun restartRetainsReceiptsAndLinksWhileUnfinishedWorkBecomesInterrupted() {
        val settings = Settings()
        val journal = ImportJournal(settings)
        journal.attach("server|alice")
        val saved = journal.begin("receipt.pdf")
        journal.update(saved.id) { it.copy(state = ImportState.SAVED, kind = "bond", outcome = ImportOutcome.PURCHASE,
            documentId = "doc-id", purchaseId = "purchase-id") }
        val unfinished = journal.begin("statement.pdf")
        journal.update(unfinished.id) { it.copy(state = ImportState.REVIEW, kind = "statement") }
        val reopened = ImportJournal(settings).apply { attach("server|alice") }
        assertEquals(ImportState.SAVED, reopened.entries.first().state)
        assertEquals("doc-id", reopened.entries.first().documentId)
        assertEquals("purchase-id", reopened.entries.first().purchaseId)
        assertEquals(ImportState.INTERRUPTED, reopened.entries.last().state)
        reopened.attach("server|bob")
        assertTrue(reopened.entries.isEmpty())
        reopened.attach("other-server|alice")
        assertTrue(reopened.entries.isEmpty())
        reopened.attach("server|alice")
        assertEquals(2, reopened.entries.size)
        reopened.detach()
        assertTrue(reopened.entries.isEmpty())
    }
    @Test fun retriesKeepTheirPriorErrorsAndNeverSerializePdfOrExtractedFinancialFields() {
        val settings = Settings()
        val journal = ImportJournal(settings).apply { attach("account") }
        val row = journal.begin("/private/folder/receipt\n.pdf")
        journal.update(row.id) { it.copy(state = ImportState.FAILED, error = "Could not read this PDF.") }
        journal.update(row.id) { it.copy(state = ImportState.READING, error = "") }
        journal.update(row.id) { it.copy(state = ImportState.SAVED, outcome = ImportOutcome.PURCHASE) }
        val restored = ImportJournal(settings).apply { attach("account") }.entries.single()
        assertEquals(2, restored.attempts)
        assertEquals("Could not read this PDF.", restored.events.single().error)
        assertEquals("receipt .pdf", restored.fileName)
        val serialized = settings.histories.getValue("account")
        for (field in listOf("faceValue", "couponRate", "accountReference", "password", "%PDF-", "fields")) assertFalse(serialized.contains(field))
    }
    @Test fun malformedStorageAndDiskFailureDoNotBreakAnImport() {
        assertTrue(decodeImportHistory("not json").isEmpty())
        assertTrue(decodeImportHistory("[{\"id\":\"bad\",\"state\":\"SAVED\"}]").isEmpty())
        val settings = Settings().apply { fail = true }
        val journal = ImportJournal(settings).apply { attach("account") }
        val row = journal.begin("receipt.pdf")
        journal.update(row.id) { it.copy(state = ImportState.SAVED) }
        assertTrue(journal.persistenceFailed)
        assertEquals(ImportState.SAVED, journal.entries.single().state)
    }
    @Test fun nativeDigestHasStableSha256Vectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", importPdfDigest(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", importPdfDigest("abc".encodeToByteArray()))
    }
}
