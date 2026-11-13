@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class ApplicationBidModelTest {
    @Test fun bidPostsOnlyAmountAndRetryIdentityAndAppearsPendingInPortfolioTotals() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val today=bondSubscriptionDate(kotlin.time.Clock.System.now())
        val close=LocalDate(today.year,today.month,1).plus(DatePeriod(months=1)).minus(DatePeriod(days=1))
        val issue=JsonRecord().put("id","synthetic-issue").put("title","Synthetic bond").put("tenorYears",5)
            .put("closingBookDate",close.toString()).put("openBookDate",close.minus(DatePeriod(days=2)).toString())
            .put("settlementDate",close.plus(DatePeriod(days=2)).toString()).put("maturityDate",close.plus(DatePeriod(years=5)).toString())
        val form=JsonRecord().put("id","shared-form").put("category",BOND_APPLICATION_CATEGORY)
            .put("applicationForm",JsonRecord().put("calendarId",issue.text("id")))
            .put("bidTerms",JsonRecord(issue.toString()).put("bondName","FXD SYNTHETIC").put("issuanceDate",close.toString()))
        val posted=mutableListOf<JsonRecord>();var saved: JsonRecord?=null
        val settings=object: ServerSettings { override fun readOrigin()="https://bid.example.test";override fun writeOrigin(origin: String) {} }
        val model=PortfolioModel(settings,assets={ name -> (if(name=="issuance-calendar.json") JsonList(listOf(issue)).toString() else "[]").encodeToByteArray() },apiFactory={ origin -> PortfolioApi(origin,MockEngine(MockEngineConfig().apply { dispatcher=StandardTestDispatcher(testScheduler);addHandler { request ->
            val body=when(request.url.encodedPath) {
                "/api/bonds/auth/session" -> """{"authenticated":true,"user":{"id":"synthetic-account","isOwner":false}}"""
                "/api/bonds/application-forms/shared-form/bids" -> {
                    posted+=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                    saved=pendingBondInput(issue,posted.last().optDouble("amount"),today).put("id","saved-bid").put("feesPaid",0).put("applicationFormDocumentId","shared-form")
                    JsonRecord().put("purchase",saved).toString()
                }
                "/api/bonds/purchases" -> JsonRecord().put("purchases",JsonList(listOfNotNull(saved))).toString()
                "/api/bonds/documents" -> JsonRecord().put("documents",JsonList(listOf(form))).toString()
                "/api/bonds/equities" -> """{"holdings":[]}"""
                else -> "{}"
            }
            respond(body,headers=headersOf(HttpHeaders.ContentType,"application/json"))
        } })) })
        val store=ViewModelStore().apply { put("bid",model) }
        try {
            advanceUntilIdle();assertNotNull(model.user,model.error)
            model.startApplicationBid(form);assertSame(form,model.applicationBidForm)
            var completed=false
            model.saveApplicationBid(form,1000000.0,"00000000-0000-4000-8000-000000000001") { completed=true }
            advanceUntilIdle()
            assertTrue(completed);assertNull(model.applicationBidForm);assertEquals(1,posted.size)
            assertEquals(setOf("amount","bidId"),Json.parseToJsonElement(posted.single().toString()).jsonObject.keys)
            assertEquals("submitted",model.purchases.single().text("status"))
            assertEquals("saved-bid",model.portfolio.recentBonds(today).single().text("id"))
            assertEquals("saved-bid",model.portfolio.recentBonds(Today).single().text("id"))
            assertEquals(1000000.0,model.portfolio.summary(today).single().bondFaceValue)
        } finally { store.clear();Dispatchers.resetMain() }
    }
}
