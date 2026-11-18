package dev.orestegabo.portfolio.investments

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
import kotlin.math.abs

/** Synthetic PDFs only. Exercises the real replacement reader and its structure policy. */
class NativePdfPolicyTest {
    private fun equivalent(expected: JsonElement, actual: JsonElement, path: String) {
        when {
            expected is JsonObject && actual is JsonObject -> { assertEquals(path, expected.keys, actual.keys); expected.forEach { (key, value) -> equivalent(value, actual.getValue(key), "$path.$key") } }
            expected is JsonArray && actual is JsonArray -> { assertEquals(path, expected.size, actual.size); expected.forEachIndexed { i, value -> equivalent(value, actual[i], "$path[$i]") } }
            expected is JsonPrimitive && actual is JsonPrimitive && !expected.isString && !actual.isString && expected.doubleOrNull != null && actual.doubleOrNull != null -> assertTrue(path, abs(expected.double - actual.double) < 1e-8)
            else -> assertEquals(path, expected, actual)
        }
    }
    private suspend fun withEngine(action: suspend (LocalInvestmentEngine) -> Unit) {
        val engine = LocalInvestmentEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        try { action(engine) } finally { engine.destroy() }
    }

    @Test fun nativeReaderPreservesSignedDocumentsAndWrappedStatementRows(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        withEngine { engine ->
            for ((name, kind) in listOf("signed-issuance" to "bond-issuance", "signed-equity" to "equity", "signed-bond" to "bond", "signed-statement" to "statement", "positioned-statement" to "statement")) {
                val bytes = assets.open("native-pdf/$name.pdf").use { it.readBytes() }
                val result = engine.run("pdf", pdf = bytes)
                assertEquals(name, kind, result.text("kind"))
                assertFalse(name, result.optBoolean("usedOcr"))
                val expected = assets.open("native-pdf/$name.json").bufferedReader().use { Json.parseToJsonElement(it.readText()) }
                equivalent(expected, Json.parseToJsonElement(result.toString()), name)
                if (kind == "equity") assertEquals(340728.5, result.getJSONObject("fields").optDouble("netAmountPayable"), .001)
                if (kind == "statement") {
                    assertEquals(name, 4, result.rows("rows").size)
                    assertEquals("10000000001#01", result.rows("rows").first { it.text("kind") == "equity" }.text("contractNote"))
                }
            }
        }
    }
    @Test fun nativeReaderRejectsActionsAndNonSignatureFieldsAndThenRecovers(): Unit = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        withEngine { engine ->
            for (name in listOf("active-signature", "active-action", "text-widget")) {
                val bytes = assets.open("native-pdf/$name.pdf").use { it.readBytes() }
                assertTrue(name, runCatching { engine.run("pdf", pdf = bytes) }.exceptionOrNull() is IllegalArgumentException)
            }
            assertEquals("bond-issuance", engine.run("pdf", pdf = assets.open("native-pdf/signed-issuance.pdf").use { it.readBytes() }).text("kind"))
        }
    }
}
