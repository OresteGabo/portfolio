package dev.orestegabo.portfolio.investments

import kotlin.test.*

class DocumentInvestmentLogoTest {
    @Test fun bondDocumentsUseBnrEvenWhenABrokerSubmittedThem() {
        val document = JsonRecord().put("category", "Bond confirmation").put("issuer", "BK Capital")
        assertEquals(DocumentInvestmentLogo(bond = true, name = "BNR"), documentInvestmentLogo(document))
    }
    @Test fun supportedShareNamesAndSymbolsUseTheirOwnLogos() {
        mapOf("Bank of Kigali Group PLC" to "BOK", "I&M Bank Rwanda" to "IMR", "Equity Group Holdings" to "EQTY",
            "MTN Rwanda" to "MTNR", "Cimerwa" to "CMR", "Bralirwa" to "BLR", "KCB" to "KCB").forEach { (name, ticker) ->
            val document = JsonRecord().put("category", "Share contract").put("instrumentName", name).put("issuer", "BK Capital")
            assertEquals(ticker, documentInvestmentLogo(document)?.ticker, name)
        }
    }
    @Test fun brokerAndGenericEquityWordsDoNotInventACompany() {
        assertNull(documentInvestmentLogo(JsonRecord().put("category", "Share contract").put("issuer", "BK Capital").put("label", "BK Capital equity contract")))
        assertNull(documentInvestmentLogo(JsonRecord().put("category", "Share contract").put("label", "Equity contract")))
    }
    @Test fun mixedStatementsAndAmbiguousContractsKeepAGenericIcon() {
        assertNull(documentInvestmentLogo(JsonRecord().put("category", "Statement").put("instrumentName", "Treasury bond").put("label", "BK Group and MTN investments")))
        assertNull(documentInvestmentLogo(JsonRecord().put("category", "Share contract").put("instrumentName", "BOK and IMR")))
    }
    @Test fun structuredSecurityFactsCanIdentifyAnIssuer() {
        val document = JsonRecord("""{"category":"Contract note","details":{"facts":[{"label":"Broker","value":"BK Capital"},{"label":"Security name","value":"I&M Bank Rwanda"}]}}""")
        assertEquals("IMR", documentInvestmentLogo(document)?.ticker)
    }
}
