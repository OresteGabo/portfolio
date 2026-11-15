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
    @Test fun readOnlyCopyOwnsNestedInputAndKeepsTheExactWireRepresentation() {
        val nested = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("amount" to kotlinx.serialization.json.JsonPrimitive(1234))
        val source = JsonRecord().put("id", "record").put("facts", kotlinx.serialization.json.JsonObject(nested))
            .put("unknown", JsonRecord().put("optional", null).put("enabled", true)).put("ratio", .0000001)
        val wire = source.toString()
        val snapshot = source.readOnlyCopy()
        source.put("id", "changed")
        nested["amount"] = kotlinx.serialization.json.JsonPrimitive(0)
        assertEquals(wire, snapshot.toString())
        assertEquals(1234, snapshot.getJSONObject("facts").getInt("amount"))
        assertFailsWith<IllegalStateException> { snapshot.put("id", "bypass") }
        val draft = JsonRecord(snapshot.toString()).put("id", "edited")
        assertEquals("edited", draft.text("id")); assertEquals("record", snapshot.text("id"))
        assertEquals(wire, JsonRecord(snapshot.toString()).toString())
    }

    @Test fun nestedReadsAreEditableDetachedDraftsRatherThanMutationChannels() {
        val snapshot = JsonRecord().put("details", JsonRecord().put("amount", 1000))
            .put("rows", JsonList(listOf(JsonRecord().put("version", 3)))).readOnlyCopy()
        snapshot.getJSONObject("details").put("amount", 0)
        snapshot.rows("rows").single().put("version", 99)
        assertEquals(1000, snapshot.getJSONObject("details").getInt("amount"))
        assertEquals(3, snapshot.rows("rows").single().getInt("version"))
    }

    @Test fun readOnlyListOwnsTheContainerAndItsRecords() {
        val source = JsonRecord().put("amount", 1000)
        val input = mutableListOf(source)
        val snapshot = JsonRecord.readOnlyList(input)
        source.put("amount", 0); input.clear()
        assertEquals(1000, snapshot.single().getInt("amount"))
        assertFailsWith<IllegalStateException> { snapshot.single().put("amount", 0) }
        assertTrue(runCatching { (snapshot as MutableList<JsonRecord>).clear() }.isFailure)
        assertEquals(1, snapshot.size)
        assertTrue(JsonRecord.readOnlyList(emptyList()).isEmpty())
    }

}
