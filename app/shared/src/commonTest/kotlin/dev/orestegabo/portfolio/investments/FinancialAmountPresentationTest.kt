package dev.orestegabo.portfolio.investments

import kotlin.test.*

class FinancialAmountPresentationTest {
    @Test fun absentInvalidAndNonFiniteAmountsAreNotReportedAsZero() {
        val before = AppLanguage.preference
        try {
            AppLanguage.select("en")
            for (record in listOf(JsonRecord(), JsonRecord("""{"value":null}"""), JsonRecord("""{"value":"invalid"}"""))) {
                assertEquals("Unavailable", recordAmountText(record, "value"))
            }
            for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                assertEquals("Unavailable", displayAmountOrUnavailable(value))
            }
            assertEquals("RWF 0", recordAmountText(JsonRecord().put("value", 0), "value"))
            assertEquals("RWF −1.25", recordAmountText(JsonRecord().put("value", -1.25), "value"))
            assertEquals("USD 1.01", recordAmountText(JsonRecord().put("value", 1.005), "value", "USD"))
            assertEquals("36,615.6", displayAmountOrUnavailable(36615.6))
            AppLanguage.select("fr")
            assertEquals("Indisponible", recordAmountText(JsonRecord(), "value"))
            assertEquals("RWF 36\u202f615,6", recordAmountText(JsonRecord().put("value", 36615.6), "value"))
        } finally { AppLanguage.select(before) }
    }

    @Test fun incompleteProjectionDoesNotBecomeAnArtificialZeroOrBridgeAMissingMonth() {
        val valid = listOf(JsonRecord().put("totalAccountValue", 0), JsonRecord().put("totalAccountValue", 120.25))
        assertEquals(listOf(0.0, 120.25), projectionChartValues(valid))
        assertNull(projectionChartValues(valid + JsonRecord()))
        assertNull(projectionChartValues(valid + JsonRecord("""{"totalAccountValue":null}""")))
    }
}
