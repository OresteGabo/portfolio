package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

internal fun reportFixture(language: String = "en", lots: Int = 8): PortfolioReportData {
    val bonds = (0 until lots).map { index -> JsonRecord().put("id", "bond-$index").put("bondName", "Government of Rwanda - Long term investment bond $index")
        .put("isin", "RW000A2QK7$index").put("currency", "RWF").put("status", if (index == 0) "submitted" else "active")
        .put("purchaseDate", "${2024 + index % 3}-02-12").put("settlementDate", "${2024 + index % 3}-02-15")
        .put("maturityDate", "2035-02-15").put("faceValue", 1200000).put("feesPaid", 11000)
        .put("couponRate", .125).put("withholdingTaxRate", .05).put("broker", "BK Capital") }
    val shares = listOf(
        JsonRecord("""{"id":"a","ticker":"BOK","securityName":"BANK OF KIGALI","currency":"RWF","status":"active","tradeDate":"2024-01-01","shares":1000,"pricePerShare":300,"netAmountPayable":310000}"""),
        JsonRecord("""{"id":"b","ticker":"IMR","securityName":"I&M Bank Rwanda","currency":"RWF","status":"active","tradeDate":"2025-02-01","shares":1000,"pricePerShare":125,"netAmountPayable":130000}"""),
        JsonRecord("""{"id":"c","ticker":"UNKNOWN","securityName":"Société sans cours","currency":"USD","status":"active","tradeDate":"2026-03-01","shares":20,"netAmountPayable":1500}"""))
    val quotes = JsonRecord("""{"source":{"publishedAt":"2026-10-23"},"quotes":[{"ticker":"BOK","closing":400},{"ticker":"IMR","closing":115}]}""")
    return PortfolioReportData(PortfolioSnapshot(bonds, shares, listOf(JsonRecord("""{"id":"document","label":"Contrat d'achat - échantillon","createdAt":"2026-01-01"}"""))),
        quotes, emptyList(), "demo@example.test", 300000, LocalDate(2026,10,25), language)
}

class PortfolioReportTest {
    @Test fun reportHasValidCrossReferencesAndLossesAndAllYears() {
        val data = reportFixture()
        val bytes = generatePortfolioReport(data)
        val pdf = bytes.decodeToString()
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.contains("RWF -15,000 / -11.5%"))
        assertTrue(pdf.contains("RWF 90,000 / 29.0%"))
        assertEquals(listOf(2026, 2025, 2024), reportYears(data))
        val start = Regex("startxref\\n(\\d+)").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pdf.substring(start).startsWith("xref\n"))
        val xref = pdf.substring(start).lines()
        val count = xref[1].split(' ')[1].toInt()
        for (id in 1 until count) {
            val offset = xref[id + 2].take(10).toInt()
            assertTrue(pdf.substring(offset).startsWith("$id 0 obj\n"))
        }
        assertTrue(pdf.contains("/EmbeddedFiles"))
        assertTrue(pdf.contains("current-setting-not-historical"))
        assertFalse(pdf.contains("NaN"))
    }
    @Test fun archivePreservesEveryRecordAndUnicodeWithoutCredentials() {
        val data = reportFixture("fr")
        val pdf = generatePortfolioReport(data).decodeToString()
        val attachment = pdf.substringAfter("/Subtype /application#2Fjson /Length ").substringAfter("stream\n").substringBefore("\nendstream")
        val archive = JsonRecord(attachment)
        assertEquals(data.snapshot.purchases.size, archive.rows("purchases").size)
        assertEquals("Société sans cours", archive.rows("holdings").last().text("securityName"))
        assertEquals(300000.0, archive.optDouble("monthlyTargetRwf"))
        assertFalse(archive.has("session"))
        assertTrue(pdf.contains("Objectifs d'investissement"))
    }
    @Test fun emptyAndUnpricedReportsRemainValidAndCompactOverviewIsRejected() {
        val empty = reportFixture().copy(snapshot = PortfolioSnapshot(), quotes = null)
        assertTrue(generatePortfolioReport(empty).decodeToString().contains("No investments recorded yet."))
        assertTrue(generatePortfolioReport(reportFixture().copy(quotes = null)).decodeToString().contains("unpriced position"))
        assertFailsWith<IllegalArgumentException> { generatePortfolioReport(empty.copy(snapshot = PortfolioSnapshot(recentHoldingIds = emptySet()))) }
    }
}
