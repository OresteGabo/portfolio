package dev.orestegabo.portfolio.investments

import kotlin.test.*

class FinancialChartAccessibilityTest {
    private fun english() = AppLanguage.initialize(object : ServerSettings {
        override fun readOrigin(): String? = null
        override fun writeOrigin(origin: String) = Unit
        override fun readLanguage() = "en"
    })

    @Test fun summaryDescribesTheWholeObservedRangeAndDirection() {
        val language = AppLanguage.preference
        try {
            english()
            val points = listOf(100.0 to "First", 80.0 to "Middle", 120.0 to "Latest")
            val summary = chartAccessibleSummary(points, "area")
            listOf("3 observations", "First: First, RWF 100", "Latest: Latest, RWF 120",
                "Lowest: RWF 80", "Highest: RWF 120", "Trend: Rising").forEach { assertTrue(summary.contains(it), summary) }
            assertTrue(chartAccessibleSummary(points.reversed(), "line").contains("Trend: Falling"))
            assertTrue(chartAccessibleSummary(listOf(0.0 to "Only"), "bars").contains("Trend: Unchanged"))
            assertEquals("No chart observations", chartAccessibleSummary(emptyList(), "area"))
        } finally { restore(language) }
    }

    @Test fun selectedObservationRetainsItsSourceNoteAndFrenchTranslation() {
        val language = AppLanguage.preference
        try {
            english()
            val points = listOf(12.5 to "First", 15.0 to "Last")
            assertEquals("Observation 1 of 2: First, RWF 12.5. Source note", chartObservationDescription(points, 0, "Source note"))
            AppLanguage.select("fr")
            assertTrue(chartObservationDescription(points, 1, null).startsWith("Observation 2 sur 2 :"))
            val summary = chartAccessibleSummary(points, "area")
            assertTrue(summary.startsWith("Graphique Aire, 2 observations."))
            assertTrue(summary.endsWith("Tendance : En hausse."))
            assertEquals("Observation précédente", localized("Previous observation"))
            assertEquals("Observation suivante", localized("Next observation"))
            assertEquals("Dernière observation", localized("Latest observation"))
        } finally { restore(language) }
    }

    private fun restore(language: String) = AppLanguage.initialize(object : ServerSettings {
        override fun readOrigin(): String? = null
        override fun writeOrigin(origin: String) = Unit
        override fun readLanguage() = language
    })
}
