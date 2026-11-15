package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ImportHistoryCodecTest {
    private val time = "2026-11-01T10:00:00Z"
    private fun receipt(id: Int = 1) = ImportReceipt(id.toString(16).padStart(32, '0'), "receipt.pdf", time,
        ImportState.SAVED, kind = "bond", digest = "b".repeat(64), documentId = "doc", purchaseId = "purchase",
        targetId = "target", statementId = "statement", outcome = ImportOutcome.PURCHASE, recorded = 2,
        missing = 1, attempts = 2, events = listOf(ImportEvent(time, ImportState.FAILED, "Could not read this PDF.")))

    @Test fun completeReceiptRetainsItsWireFieldsAndRoundTrips() {
        val source = receipt()
        val expected = """{"id":"00000000000000000000000000000001","fileName":"receipt.pdf","createdAt":"2026-11-01T10:00:00Z","state":"SAVED","updatedAt":"2026-11-01T10:00:00Z","kind":"bond","digest":"${"b".repeat(64)}","documentId":"doc","purchaseId":"purchase","targetId":"target","statementId":"statement","error":"","outcome":"PURCHASE","recorded":2,"missing":1,"attempts":2,"events":[{"at":"2026-11-01T10:00:00Z","state":"FAILED","error":"Could not read this PDF."}]}"""
        assertEquals(expected, source.json().toString())
        assertEquals("[$expected]", encodeImportHistory(listOf(source)))
        assertEquals(listOf(source), decodeImportHistory("[$expected]"))
    }

    @Test fun allStatesAndDocumentKindsKeepTheirRecoverySemantics() {
        for (state in ImportState.entries) for (kind in listOf("bond", "equity", "statement", "bond-issuance")) {
            val restored = decodeImportHistory(encodeImportHistory(listOf(receipt().copy(state = state, kind = kind)))).single()
            assertEquals(if (state in listOf(ImportState.READING, ImportState.REVIEW, ImportState.SAVING)) ImportState.INTERRUPTED else state, restored.state)
            assertEquals(kind, restored.kind)
        }
    }

    @Test fun unsafeAndUnknownMetadataIsNormalizedWithoutLosingValidRecords() {
        val row = receipt().json().put("fileName", "/private/receipt\n.pdf").put("updatedAt", "invalid")
            .put("kind", "unknown").put("digest", "bad").put("outcome", "unknown")
            .put("recorded", 999).put("missing", -1).put("attempts", 0).put("error", "private untrusted message")
            .put("documentId", "x".repeat(150))
        val restored = decodeImportHistory(JsonList(listOf(row)).toString()).single()
        assertEquals("receipt .pdf", restored.fileName)
        assertEquals(time, restored.updatedAt)
        assertEquals("", restored.kind); assertEquals("", restored.digest); assertNull(restored.outcome)
        assertEquals(500, restored.recorded); assertEquals(0, restored.missing); assertEquals(1, restored.attempts)
        assertEquals(GENERAL_ERROR_MESSAGE, restored.error)
        assertEquals(120, restored.documentId.length)
    }

    @Test fun malformedRowsAreDroppedAndDuplicateIdsKeepTheirFirstValidReceipt() {
        val original = receipt()
        val rows = listOf(original.json().put("id", "invalid"), original.json(), original.copy(fileName = "second.pdf").json(),
            receipt(2).json().put("createdAt", "invalid"), receipt(3).json().put("state", "UNKNOWN"))
        assertEquals(listOf(original), decodeImportHistory(JsonList(rows).toString()))
        for (raw in listOf(null, "", "not json", " ".repeat(4_000_001))) assertTrue(decodeImportHistory(raw).isEmpty())
    }

    @Test fun storedReceiptAndEventHistoryRemainBounded() {
        val rows = (0..1000).map { receipt(it) }
        val decoded = decodeImportHistory(encodeImportHistory(rows))
        assertEquals(rows.takeLast(1000), decoded)
        val events = (0..8).map { ImportEvent(time, ImportState.CANCELLED, "") }
        val row = receipt().copy(events = events).json()
        assertEquals(8, decodeImportHistory(JsonList(listOf(row)).toString()).single().events.size)
        row.put("events", JsonList(listOf(JsonRecord().put("at", time).put("state", "SAVED"),
            JsonRecord().put("at", "invalid").put("state", "FAILED"))))
        assertTrue(decodeImportHistory(JsonList(listOf(row)).toString()).single().events.isEmpty())
    }
}
