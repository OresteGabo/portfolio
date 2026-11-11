@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*

class BondMonthReminderTest {
    @Test fun monthAndYearValidationDoesNotAcceptDatesOrInvalidMonths() {
        assertTrue(validBondReminderMonth("2027-03"))
        listOf("2027-3","2027-00","2027-13","2027-03-01","March").forEach { assertFalse(validBondReminderMonth(it)) }
    }
    @Test fun acknowledgedReminderSurvivesReloadAndFailedWritesLeaveTheSavedStateAlone() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store=ViewModelStore()
        val months=mutableSetOf<String>()
        var fail=false
        var readDelay=0L
        var cleanup=""
        NativePushBridge.installReminderCleanup { cleanup=it }
        val model=PortfolioModel(object : ServerSettings {
            override fun readOrigin()="https://reminders.example.test"
            override fun writeOrigin(origin: String) {}
        },assets={ "[]".encodeToByteArray() },apiFactory={ origin ->
            PortfolioApi(origin,MockEngine(MockEngineConfig().apply {
                this.dispatcher=dispatcher
                addHandler { request ->
                    val path=request.url.encodedPath
                    when {
                        path=="/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"reminder-test"}}""")
                        path=="/api/bonds/mobile-overview" -> respond("""{"version":1,"purchases":[],"holdings":[],"recentPurchaseIds":[],"recentHoldingIds":[],"monthlyInvestmentTotals":[]}""")
                        path.startsWith("/api/bonds/notifications/bond-reminders") -> {
                            if(request.method==HttpMethod.Put) {
                                delay(100)
                                if(fail) return@addHandler respond("{}",HttpStatusCode.ServiceUnavailable)
                                val enabled=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).optBoolean("enabled")
                                val month=path.substringAfterLast('/')
                                if(enabled) months+=month else months-=month
                            }
                            val snapshot=JsonRecord().put("months",JsonList(months)).toString()
                            if(request.method==HttpMethod.Get) delay(readDelay)
                            respond(snapshot)
                        }
                        else -> respond("{}")
                    }
                }
            }))
        })
        store.put("model",model)
        try {
            advanceUntilIdle()
            model.loadBondReminders(); advanceUntilIdle()
            assertTrue(model.bondRemindersLoaded)
            model.setBondMonthReminder("2027-03",true)
            assertEquals("2027-03",model.bondReminderSaving)
            assertTrue(model.bondReminderMonths.isEmpty())
            advanceUntilIdle()
            assertEquals(setOf("2027-03"),model.bondReminderMonths)
            assertEquals("2027-03",cleanup)
            assertNotNull(model.bondReminderFeedback)
            model.dismissBondReminderFeedback()
            assertNull(model.bondReminderFeedback)
            model.loadBondReminders(); advanceUntilIdle()
            assertEquals(setOf("2027-03"),model.bondReminderMonths)
            readDelay=1000
            model.loadBondReminders();runCurrent()
            model.setBondMonthReminder("2027-03",false);advanceUntilIdle()
            assertTrue(model.bondReminderMonths.isEmpty(),"A stale refresh must not restore a removed reminder")
            readDelay=0
            model.setBondMonthReminder("2027-03",true);advanceUntilIdle()
            fail=true
            model.setBondMonthReminder("2027-03",false);advanceUntilIdle()
            assertEquals(setOf("2027-03"),model.bondReminderMonths)
            assertNotNull(model.bondRemindersError)
            assertEquals(model.bondRemindersError,model.bondReminderFeedback)
            fail=false
            model.setBondMonthReminder("2027-03",false);advanceUntilIdle()
            assertTrue(model.bondReminderMonths.isEmpty())
            assertEquals("",cleanup)
            assertNull(model.bondRemindersError)
            model.setBondMonthReminder("2027-03",true);advanceUntilIdle()
            model.logout();advanceUntilIdle()
            assertTrue(model.bondReminderMonths.isEmpty())
            assertFalse(model.bondRemindersLoaded)
            assertEquals("",cleanup)
        } finally { store.clear(); NativePushBridge.installReminderCleanup {}; Dispatchers.resetMain() }
    }
}
