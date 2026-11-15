package dev.orestegabo.portfolio.investments

import kotlin.test.*

class ImportReceiptPolicyTest {
    private val previous = ImportReceipt("a".repeat(32), "receipt.pdf", "2026-11-01T10:00:00Z",
        ImportState.FAILED, attempts = 3, error = "Could not read this PDF.", updatedAt = "2026-11-01T11:00:00Z")

    @Test fun everyStatePairPreservesTheExistingRetryPolicy() {
        for (from in ImportState.entries) for (to in ImportState.entries) {
            val source = previous.copy(state = from)
            val changed = source.copy(state = to, error = "", documentId = "document", attempts = 20)
            val next = updateImportReceipt(source, changed, "2026-11-02T10:00:00Z")
            val retry = from in listOf(ImportState.FAILED, ImportState.CANCELLED, ImportState.INTERRUPTED) &&
                to in listOf(ImportState.READING, ImportState.SAVING)
            assertEquals(if (retry) 4 else 20, next.attempts, "$from -> $to")
            assertEquals(if (retry) listOf(ImportEvent(source.updatedAt, from, source.error)) else emptyList(), next.events)
            assertEquals(to, next.state)
            assertEquals("document", next.documentId)
            assertEquals("", next.error)
            assertEquals("2026-11-02T10:00:00Z", next.updatedAt)
            assertEquals(3, source.attempts)
            assertTrue(source.events.isEmpty())
        }
    }

    @Test fun retryRetainsOnlyTheLastEightOriginalOutcomesAndDoesNotMutateItsInputs() {
        val history = (0..8).map { ImportEvent("event-$it", ImportState.FAILED, "error-$it") }
        val source = previous.copy(events = history)
        val changed = source.copy(state = ImportState.SAVING, events = emptyList(), attempts = 999)
        val next = updateImportReceipt(source, changed, "now")
        assertEquals(history.takeLast(7) + ImportEvent(source.updatedAt, source.state, source.error), next.events)
        assertEquals(4, next.attempts)
        assertEquals(9, history.size)
        assertTrue(changed.events.isEmpty())
        assertEquals(999, changed.attempts)
    }
}
