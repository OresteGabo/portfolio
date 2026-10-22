package dev.orestegabo.portfolio.investments

import kotlin.test.*
class JsonRecordTest {
    @Test fun preservesNestedDocumentFactsAndNulls() {
        val original = """{"id":"doc","details":{"facts":[{"label":"Amount","value":"RWF 1,000"}]},"optional":null,"flags":[true,5,"x"]}"""
        val value = JsonRecord(original)
        assertEquals("RWF 1,000", value.getJSONObject("details").rows("facts").single().text("value"))
        assertTrue(value.isNull("optional"))
        assertEquals("", value.text("optional"))
        assertEquals(value.toString(), JsonRecord(value.toString()).toString())
    }
    @Test fun requestBuilderPreservesNumericAndBooleanTypes() {
        val value = JsonRecord().put("shares", 500).put("enabled", true).put("couponDates", JsonList(listOf("2027-01-01")))
        assertEquals(500, value.getInt("shares"))
        assertTrue(value.optBoolean("enabled"))
        assertEquals("2027-01-01", value.getJSONArray("couponDates").getString(0))
        assertEquals(1.0, value.optDouble("missing", 1.0))
    }
}
