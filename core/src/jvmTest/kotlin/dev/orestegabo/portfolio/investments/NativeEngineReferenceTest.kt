package dev.orestegabo.portfolio.investments

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.math.*

/** Frozen outputs from the prior engine, recorded before switching mobile runtime ownership. */
class NativeEngineReferenceTest {
    private fun fixture(name: String) = Json.parseToJsonElement(requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() }).jsonArray
    private fun equivalent(expected: JsonElement, actual: JsonElement, path: String) {
        when {
            expected is JsonObject && actual is JsonObject -> { assertEquals(expected.keys, actual.keys, "$path keys"); expected.forEach { (key, value) -> equivalent(value, actual.getValue(key), "$path.$key") } }
            expected is JsonArray && actual is JsonArray -> { assertEquals(expected.size, actual.size, "$path length"); expected.forEachIndexed { i, value -> equivalent(value, actual[i], "$path[$i]") } }
            expected is JsonPrimitive && actual is JsonPrimitive && !expected.isString && !actual.isString && expected.doubleOrNull != null && actual.doubleOrNull != null -> {
                val e = expected.double; val a = actual.double
                assertTrue(abs(e - a) <= max(1e-8, abs(e) * 1e-12), "$path: expected $e, actual $a")
            }
            else -> assertEquals(expected, actual, path)
        }
    }
    @Test fun kotlinParsersPreserveEveryRecordedValidAndInvalidCase() {
        for ((index, value) in fixture("document-parser-reference.json").withIndex()) {
            val row = value.jsonObject; val name = row.getValue("name").jsonPrimitive.content; val args = row.getValue("args").jsonArray
            val text = (args[0] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(); val ocr = args.getOrNull(1)?.jsonPrimitive?.booleanOrNull ?: false
            val result = runCatching {
                when (name) {
                    "parsePurchasePdfText" -> InvestmentDocumentRules.parse(text, ocr)
                    "parseBondPdfText" -> InvestmentDocumentRules.parseBond(text, ocr)
                    "parseBondIssuancePdfText" -> InvestmentDocumentRules.parseIssuance(text, ocr)
                    "parseEquityPdfText" -> InvestmentDocumentRules.parseEquity(text, ocr)
                    "parseBkStatement" -> InvestmentDocumentRules.parseStatement(text, ocr)
                    "parseEquityPurchase" -> (args[0] as? JsonObject)?.let { validateEquityDocument(JsonRecord(it.toString())) }
                    else -> error(name)
                }
            }
            if (row.containsKey("error")) {
                assertTrue(result.isFailure, "case $index $name should fail")
                assertEquals(row.getValue("error").jsonPrimitive.content, (result.exceptionOrNull() as? DocumentParseException)?.code, "case $index $name error")
            } else {
                assertTrue(result.isSuccess, "case $index $name failed: ${result.exceptionOrNull()}")
                equivalent(row.getValue("result"), result.getOrNull()?.let { Json.parseToJsonElement(it.toString()) } ?: JsonNull, "case $index $name")
            }
        }
    }
    @Test fun kotlinSimulatorPreservesEveryMonthlyFieldAndSummary() = runTest {
        for ((index, value) in fixture("projection-reference.json").withIndex()) {
            val row = value.jsonObject
            val result = InvestmentProjection.project(JsonRecord(row.getValue("input").toString()))
            equivalent(row.getValue("result"), Json.parseToJsonElement(result.toString()), "projection $index")
        }
    }
}
