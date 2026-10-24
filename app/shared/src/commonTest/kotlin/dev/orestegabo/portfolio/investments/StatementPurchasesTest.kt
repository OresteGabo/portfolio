@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class StatementPurchasesTest {
    private fun bondReview() = JsonRecord("""{"kind":"bond","missingFields":[],"warnings":[],"fields":{"bondName":"SYNTHETIC OCTOBER","isin":"TEST0000001","issuer":"Government of Rwanda","currency":"RWF","tenorYears":20,"faceValue":1200000,"pricePercent":100,"couponRate":0.12,"purchaseDate":"2026-10-21","settlementDate":"2026-10-23","maturityDate":"2046-09-28","firstCouponDate":"2027-03-28"}}""")
    private fun row() = JsonRecord("""{"kind":"bond","date":"2026-10-26","amount":1200000,"title":"October Bond Cost"}""")
    @Test fun journalDebitMatchesActualExecutionCostWithoutInventingPrincipalOrIncludingCommission() {
        assertTrue(statementConfirmationMatches(row(), bondReview()))
        assertFalse(statementConfirmationMatches(row().put("amount", 1211000), bondReview()))
        assertFalse(statementConfirmationMatches(row().put("date", "2026-11-01"), bondReview()))
        assertFalse(statementConfirmationMatches(row().put("kind", "equity"), bondReview()))
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(JsonRecord().put("kind", "statement").put("missingFields", JsonList()).put("fields", row())) }
    }
    @Test fun wrongShareContractCannotCompleteAMissingStatementPurchase() {
        val review = JsonRecord("""{"kind":"equity","missingFields":[],"warnings":[],"fields":{"broker":"BK Capital","currency":"RWF","contractNote":"10000000001#01","ticker":"IMR","tradeDate":"2026-07-22","shares":200,"pricePerShare":90,"netAmountPayable":18400}}""")
        val row = JsonRecord("""{"kind":"equity","date":"2026-07-22","amount":18400,"ticker":"IMR","contractNote":"10000000001#01","shares":200,"pricePerShare":90}""")
        assertTrue(statementConfirmationMatches(row, review))
        assertFalse(statementConfirmationMatches(row.put("contractNote", "10000000002#01"), review))
    }
    @Test fun statementRemindersAreServerRecordsOutsideTotalsAndAreClearedAtLogout() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        var stored = false
        var posted: JsonRecord? = null
        val model = PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://synthetic.example.test"
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> when (request.url.encodedPath) {
                    "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"synthetic"}}""")
                    "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                    "/api/bonds/purchases" -> respond("""{"purchases":[]}""")
                    "/api/bonds/equities" -> respond("""{"holdings":[]}""")
                    "/api/bonds/documents" -> respond("""{"documents":[]}""")
                    "/api/bonds/statement-items" -> if (request.method == HttpMethod.Post) {
                        posted = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        stored = true
                        respond("""{"rows":[${row().put("state", "missing")}]}""")
                    } else respond(if (stored) """{"items":[${row().put("id", "hint")}]}""" else """{"items":[]}""")
                    else -> respond("{}")
                } }
            }))
        })
        store.put("model", model)
        try {
            advanceUntilIdle()
            var reviewed = false
            model.checkStatement(JsonRecord().put("kind", "statement").put("rows", JsonList(listOf(row())))) { reviewed = true }
            advanceUntilIdle()
            assertTrue(reviewed)
            assertEquals("BK Capital", posted!!.rows("items").single().text("broker"))
            assertEquals(1, model.statementItems.size)
            assertTrue(model.portfolio.purchases.isEmpty())
            assertTrue(model.portfolio.summary(Today).isEmpty())
            model.logout(); advanceUntilIdle()
            assertTrue(model.statementItems.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
