package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class AnnualReportTest {
    private fun bond(id: String, date: String, status: String = "active") = JsonRecord()
        .put("id", id).put("bondName", id).put("purchaseDate", date).put("settlementDate", date)
        .put("maturityDate", "2035-01-01").put("status", status).put("currency", "RWF").put("faceValue", 100000)

    @Test fun calendarYearIncludesBothBoundariesButNoAdjacentOrInvalidDates() {
        val data = reportFixture().copy(snapshot = PortfolioSnapshot(purchases = listOf(
            bond("prior", "2024-12-31"), bond("jan", "2025-01-01"), bond("dec", "2025-12-31"),
            bond("later", "2026-01-01"), bond("invalid", "bad"))))
        val scope = annualReportScope(data, 2025)
        assertEquals(listOf("jan", "dec"), scope.bonds.map { it.text("id") })
        assertEquals(listOf("prior", "jan", "dec"), scope.closingBonds.map { it.text("id") })
        assertEquals(LocalDate(2025,12,31), scope.cutoff)
        assertFailsWith<IllegalArgumentException> { annualReportScope(data, 2027) }
    }

    @Test fun currentYearStopsTodayAndExpiredBondsDoNotRemainInClosingValue() {
        val matured = bond("matured", "2024-01-01", "matured").put("maturityDate", "2025-06-01")
        val unsettled = bond("unsettled", "2025-12-30").put("settlementDate", "2026-01-02")
        val data = reportFixture().copy(snapshot = PortfolioSnapshot(purchases = listOf(matured, unsettled,
            bond("future", "2026-12-01"), bond("today", "2026-10-25"))))
        assertEquals(listOf("matured"), annualReportScope(data, 2024).closingBonds.map { it.text("id") })
        assertTrue(annualReportScope(data, 2025).closingBonds.isEmpty())
        assertEquals(listOf("today"), annualReportScope(data, 2026).bonds.map { it.text("id") })
        assertEquals(LocalDate(2026,10,25), annualReportScope(data, 2026).cutoff)
        assertTrue(generateAnnualReport(data, 2026).decodeToString().contains("YEAR TO DATE"))
    }

    @Test fun closingPriceNeverUsesLaterQuotesOrContractObservations() {
        val history = JsonRecord("""{"observations":[
            {"ticker":"BOK","date":"2024-12-31","closing":80},
            {"ticker":"BOK","date":"2025-12-30","closing":100},
            {"ticker":"BOK","date":"2026-01-01","closing":999}],
            "tradeObservations":[{"ticker":"BOK","date":"2025-12-31","price":800}]}""")
        val scope = annualReportScope(reportFixture(), 2025)
        val price = annualClosingPrice(history, reportFixture().quotes, "BOK", scope)
        assertEquals(100.0, price?.price)
        assertEquals(LocalDate(2025,12,30), price?.date)
        assertNull(annualClosingPrice(null, reportFixture().quotes, "BOK", scope))
        assertNull(annualClosingPrice(history, null, "UNKNOWN", scope))
    }

    @Test fun unavailableHistoricalPricesAndDisposalDatesAreNotInvented() {
        val data = reportFixture().copy(snapshot = reportFixture().snapshot.copy(
            purchases = listOf(bond("sold-without-date", "2024-01-01", "sold"))))
        assertEquals(1, annualReportScope(data,2025).uncertain.size)
        val pdf = generateAnnualReport(data,2025).decodeToString()
        assertTrue(pdf.contains("Unavailable"))
        assertFalse(pdf.contains("RWF 400")) // Fixture's current quote must not become a 2025 price.
    }

    @Test fun attachmentContainsOnlyYearTransactionsAndSeparatesCarryForwardPositions() {
        val data = reportFixture().copy(snapshot = reportFixture().snapshot.copy(purchases = listOf(
            bond("prior", "2024-01-01"), bond("annual", "2025-01-01"), bond("future", "2026-01-01"))),
            couponReceipts = listOf(JsonRecord().put("dueDate","2025-12-31"), JsonRecord().put("dueDate","2026-01-01")))
        val pdf = generateAnnualReport(data,2025).decodeToString()
        val archive = JsonRecord(pdf.substringAfter("/Subtype /application#2Fjson /Length ").substringAfter("stream\n").substringBefore("\nendstream"))
        assertEquals(listOf("annual"), archive.rows("purchases").map { it.text("id") })
        assertEquals(listOf("prior", "annual"), archive.rows("closingBonds").map { it.text("id") })
        assertEquals(listOf("b"), archive.rows("holdings").map { it.text("id") })
        assertEquals(1, archive.rows("couponReceipts").size)
        assertFalse(archive.has("documents"))
        assertFalse(archive.toString().contains("future"))
    }

    @Test fun virtualDocumentsCoverCarryForwardYearsWithoutMutatingStoredFiles() {
        val snapshot = PortfolioSnapshot(purchases = listOf(bond("old", "2023-05-01")))
        val rows = annualReportDocuments(snapshot, LocalDate(2026,10,27))
        assertEquals(listOf(2026,2025,2024,2023), rows.map { it.optInt("annualReportYear") })
        assertEquals("2025-12-31", rows[1].text("documentDate"))
        assertEquals("2026-10-27", rows[0].text("documentDate"))
        assertTrue(snapshot.documents.isEmpty())
        assertTrue(rows.none { it.has("downloadUrl") })
    }
}
