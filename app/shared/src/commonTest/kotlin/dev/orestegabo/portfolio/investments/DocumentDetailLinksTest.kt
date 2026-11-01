package dev.orestegabo.portfolio.investments

import kotlin.test.*

class DocumentDetailLinksTest {
    private val doc=JsonRecord().put("id","source-pdf").put("instrumentName","Bank of Kigali")
    @Test fun linksUsePersistedEvidenceInsteadOfNamesOrSimilarDates() {
        val real=JsonRecord().put("id","bond").put("confirmationDocumentId","source-pdf")
        val unrelated=JsonRecord().put("id","other").put("bondName","Bank of Kigali").put("confirmationDocumentId","other-pdf")
        assertEquals(listOf("bond"),documentBonds(doc,listOf(real,unrelated)).map { it.text("id") })
        assertTrue(documentBonds(JsonRecord().put("id",""),listOf(JsonRecord())).isEmpty())
    }
    @Test fun aSharedReportCanLinkSeveralOwnedPurchasesWithoutChoosingOneArbitrarily() {
        val rows=listOf("a","b").map { JsonRecord().put("id",it).put("issuanceDocumentId","source-pdf") }
        assertEquals(2,documentBonds(doc,rows).size)
    }
    @Test fun lotsForTheSameShareCollapseButDifferentCurrenciesRemainSeparate() {
        fun lot(currency: String)=JsonRecord().put("contractDocumentId","source-pdf").put("ticker","BOK").put("currency",currency)
        assertEquals(2,documentShares(doc,listOf(lot("RWF"),lot("RWF"),lot("USD"))).size)
        assertTrue(documentShares(doc,listOf(JsonRecord().put("ticker","BOK").put("securityName","Bank of Kigali"))).isEmpty())
    }
}
