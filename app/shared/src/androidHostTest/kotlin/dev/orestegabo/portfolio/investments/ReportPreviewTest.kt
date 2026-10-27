package dev.orestegabo.portfolio.investments

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Synthetic exports for checking the actual shared renderer with a PDF viewer. */
class ReportPreviewTest {
    @Test fun writeEnglishAndFrenchPreviewReports() {
        val folder = File("build/reports/portfolio-pdf").apply { mkdirs() }
        listOf("en", "fr").forEach { language ->
            val bytes = generatePortfolioReport(reportFixture(language, lots = 15))
            assertTrue(bytes.size > 10000)
            File(folder, "portfolio-$language.pdf").writeBytes(bytes)
        }
        listOf("en", "fr").forEach { language ->
            File(folder, "annual-2025-$language.pdf").writeBytes(generateAnnualReport(reportFixture(language), 2025))
            File(folder, "annual-2026-$language.pdf").writeBytes(generateAnnualReport(reportFixture(language), 2026))
        }
        val stress = reportFixture("fr", lots = 2).let { data ->
            data.snapshot.purchases.forEach { it.put("faceValue", 300_000_000_000L).put("bondName", "Obligation à long terme / ".repeat(15)) }
            data.snapshot.holdings.forEach { it.put("netAmountPayable", 300_000_000_000L).put("securityName", "Société de développement et d'investissement / ".repeat(10)) }
            data.copy(monthlyTarget = 1_000_000_000_000L)
        }
        File(folder, "portfolio-layout-stress.pdf").writeBytes(generatePortfolioReport(stress))
    }
}
