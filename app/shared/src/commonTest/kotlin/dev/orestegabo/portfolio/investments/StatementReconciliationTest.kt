@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class StatementReconciliationTest {
    @Test fun manualReviewSendsExplicitReasonAndRevisionWithoutCreatingAHolding() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        val store=ViewModelStore();val decisions=mutableListOf<JsonRecord>();var financialWrites=0
        var row=JsonRecord().put("id","synthetic-item").put("kind","bond").put("state","review-required").put("reconciliationStatus","review-required").put("decisions",JsonList())
        val model=PortfolioModel(object:ServerSettings {
            override fun readOrigin()="https://reconciliation.example.test"
            override fun writeOrigin(origin:String){}
        },assets={"[]".encodeToByteArray()},apiFactory={origin->PortfolioApi(origin,MockEngine(MockEngineConfig().apply {
            this.dispatcher=dispatcher
            addHandler { request ->
                when(request.url.encodedPath) {
                    "/api/bonds/auth/session"->respond("""{"authenticated":true,"user":{"id":"synthetic"}}""")
                    "/api/bonds/mobile-overview"->respond("{}",HttpStatusCode.NotFound)
                    "/api/bonds/purchases","/api/bonds/equities"->{if(request.method!=HttpMethod.Get)financialWrites++;respond("{}")}
                    "/api/bonds/statement-items"->respond(JsonRecord().put("items",JsonList(listOf(row))).toString())
                    "/api/bonds/statement-items/synthetic-item/resolve"->{
                        val body=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString());decisions+=body
                        row=JsonRecord(row.toString()).put("reconciliationStatus",if(body.text("action")=="link")"matched" else "review-required")
                            .put("decisions",JsonList(listOf(JsonRecord().put("at","2026-10-30T12:00:00Z").put("action",body.text("action")))))
                        respond(JsonRecord().put("rows",JsonList(listOf(row))).toString())
                    }
                    else->respond("{}")
                }
            }
        }))})
        store.put("model",model)
        try {
            advanceUntilIdle();model.showStatementDocumentReview("synthetic-doc");advanceUntilIdle()
            assertEquals(1,model.statementReviewRows!!.size)
            var completed=false
            model.resolveStatementRecord(row,"synthetic-purchase","Checked original contract and statement") {completed=true};advanceUntilIdle()
            assertTrue(completed);assertEquals("link",decisions.single().text("action"));assertEquals("",decisions.single().text("expectedDecisionAt"))
            assertEquals("matched",model.statementReviewRows!!.single().text("reconciliationStatus"))
            model.resolveStatementRecord(row,null,"Correcting an earlier link");advanceUntilIdle()
            assertEquals("clear",decisions.last().text("action"));assertEquals("2026-10-30T12:00:00Z",decisions.last().text("expectedDecisionAt"))
            assertEquals(0,financialWrites);assertTrue(model.purchases.isEmpty());assertTrue(model.holdings.isEmpty())
            model.logout();advanceUntilIdle();assertNull(model.statementReviewRows)
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun legacyAndNewStatesStayDistinctFromNonpurchaseEvidence() {
        assertEquals("matched",statementReconciliationStatus(JsonRecord().put("state","recorded")))
        assertEquals("unmatched",statementReconciliationStatus(JsonRecord().put("state","missing")))
        assertEquals("review-required",statementReconciliationStatus(JsonRecord().put("state","review-required")))
        assertEquals("evidence-only",statementReconciliationStatus(JsonRecord().put("reconciliationStatus","evidence-only")))
    }
}
