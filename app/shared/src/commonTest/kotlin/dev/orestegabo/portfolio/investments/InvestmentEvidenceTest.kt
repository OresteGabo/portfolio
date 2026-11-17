package dev.orestegabo.portfolio.investments

import kotlin.test.*

class InvestmentEvidenceTest {
    @Test fun aStatementSourceIsLabelledSeparatelyFromAnAllocationConfirmation() {
        val links = investmentEvidence(JsonRecord().put("statementDocumentId", "statement-1"), emptyList())
        assertEquals("View source statement", links.single().label)
        assertEquals("BK Capital statement", links.single().document.text("label"))
        assertEquals("/api/bonds/documents/statement-1", links.single().document.text("downloadUrl"))
    }
    @Test fun actionsUseExplicitPersonalAndIssuanceLinksAndPreserveMetadata() {
        val investment = JsonRecord().put("confirmationDocumentId", "allocation-1").put("issuanceDocumentId", "issuance-2")
        val document = JsonRecord().put("id", "allocation-1").put("label", "Original allocation")
            .put("originalFileName", "allocation.pdf").put("downloadUrl", "https://untrusted.example/file")
        val links = investmentEvidence(investment, listOf(document))
        assertEquals(listOf("View allocation confirmation", "View issuance results"), links.map { it.label })
        assertEquals("/api/bonds/documents/allocation-1", links.first().document.text("downloadUrl"))
        assertEquals("allocation.pdf", links.first().document.text("originalFileName"))
        assertEquals("https://untrusted.example/file", document.text("downloadUrl")) // No mutation of saved metadata.
        assertEquals("/api/bonds/documents/issuance-2", links.last().document.text("downloadUrl"))
    }

    @Test fun contractOpensWithoutWaitingForTheLibraryAndDoesNotGuessByTicker() {
        val holding = JsonRecord().put("ticker", "IMR").put("contractDocumentId", "contract-3")
        assertEquals("View share contract", investmentEvidence(holding, emptyList()).single().label)
        assertTrue(investmentEvidence(JsonRecord().put("ticker", "IMR"), listOf(JsonRecord().put("id", "other").put("ticker", "IMR"))).isEmpty())
    }

    @Test fun malformedIdentifiersNeverBecomeActionsAndDuplicateLinksAppearOnce() {
        for (id in listOf("../auth/session", "https://external.test/file", "file?token=secret", "", "a".repeat(129))) {
            assertTrue(investmentEvidence(JsonRecord().put("contractDocumentId", id), emptyList()).isEmpty())
        }
        val same = JsonRecord().put("confirmationDocumentId", "same").put("issuanceDocumentId", "same")
        assertEquals(1, investmentEvidence(same, emptyList()).size)
    }

    @Test fun shareDocumentsIncludeEveryLinkedPurchaseOnceWithinTheSelectedCompanyAndCurrency() {
        fun lot(ticker: String, currency: String, document: String) = JsonRecord()
            .put("ticker", ticker).put("currency", currency).put("contractDocumentId", document)
        val selected = JsonRecord().put("ticker", "IMR").put("currency", "RWF")
        val lots = listOf(lot("IMR", "RWF", "contract-a"), lot("IMR", "RWF", "contract-b"),
            lot("IMR", "RWF", "contract-a"), lot("IMR", "USD", "foreign-currency"), lot("BLR", "RWF", "other-company"))
        val documents = listOf(JsonRecord().put("id", "contract-a").put("label", "First contract"),
            JsonRecord().put("id", "unrelated").put("ticker", "IMR"))
        val files = investmentEvidence(shareDetailLots(lots, selected), documents)
        assertEquals(listOf("contract-a", "contract-b"), files.map { it.document.text("id") })
        assertEquals("First contract", files.first().document.text("label"))
        assertEquals("/api/bonds/documents/contract-b", files.last().document.text("downloadUrl"))
        assertTrue(investmentEvidence(emptyList<JsonRecord>(), documents).isEmpty())
    }
}
