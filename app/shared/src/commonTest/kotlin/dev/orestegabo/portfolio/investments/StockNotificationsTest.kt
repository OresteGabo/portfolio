@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class StockNotificationsTest {
    @Test fun unreadStateChangesOnlyAfterServerAcknowledgementAndClearsAtLogout() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store=ViewModelStore()
        var failRead=true
        var signedIn=true
        var notificationFetches=0
        var read=false
        val model=PortfolioModel(object: ServerSettings {
            override fun readOrigin()="https://synthetic.example.test"
            override fun writeOrigin(origin: String) {}
        },assets={ "[]".encodeToByteArray() },apiFactory={ origin ->
            PortfolioApi(origin,MockEngine(MockEngineConfig().apply {
                this.dispatcher=dispatcher
                addHandler { request -> when(request.url.encodedPath) {
                    "/api/bonds/auth/session" -> respond(if(signedIn) """{"authenticated":true,"user":{"id":"synthetic-account"}}""" else """{"authenticated":false}""")
                    "/api/bonds/mobile-overview" -> respond("""{"version":1,"recentPurchaseIds":[],"recentHoldingIds":[],"purchases":[],"holdings":[],"monthlyInvestmentTotals":[]}""")
                    "/api/bonds/documents" -> respond("""{"documents":[]}""")
                    "/api/bonds/notifications" -> { notificationFetches++;respond("""{"notifications":[{"id":"alert-one","ticker":"IMR","read":$read}],"unreadCount":${if(read)0 else 1}}""") }
                    "/api/bonds/notifications/read" -> if(failRead)respond("{}",HttpStatusCode.ServiceUnavailable) else { read=true;respond("{}") }
                    "/api/bonds/auth/logout" -> { signedIn=false;respond("""{"authenticated":false}""") }
                    else -> respond("{}")
                } }
            }))
        })
        store.put("model",model)
        try {
            advanceUntilIdle()
            assertEquals(1,model.unreadNotifications)
            assertFalse(model.notifications.single().optBoolean("read"))
            model.readNotification("alert-one");advanceUntilIdle()
            assertEquals(1,model.unreadNotifications)
            assertNotNull(model.notificationsError)
            failRead=false
            model.readNotification("alert-one");advanceUntilIdle()
            assertEquals(0,model.unreadNotifications)
            assertTrue(model.notifications.single().optBoolean("read"))
            model.logout();advanceUntilIdle()
            assertNull(model.user);assertTrue(model.notifications.isEmpty());assertEquals(0,model.unreadNotifications)
            val count=notificationFetches
            model.refreshNotifications();advanceUntilIdle();assertEquals(count,notificationFetches)
        } finally { store.clear();Dispatchers.resetMain() }
    }    @Test fun failedPreferenceSaveKeepsAcknowledgedValueAndLateResponseCannotRestoreSignedOutData() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        val store=ViewModelStore();var fail=true
        val categories="""{"prices":true,"coupons":true,"maturities":true,"subscriptions":true,"pending":true,"documents":true,"targets":true,"reports":true,"security":true}"""
        fun prefs(auto: Boolean)="""{"configured":true,"categories":$categories,"autoCoupon":$auto,"monthlyTarget":200000}"""
        val model=PortfolioModel(object: ServerSettings {
            override fun readOrigin()="https://synthetic.example.test"
            override fun writeOrigin(origin: String) {}
        },assets={ "[]".encodeToByteArray() },apiFactory={ origin -> PortfolioApi(origin,MockEngine(MockEngineConfig().apply {
            this.dispatcher=dispatcher
            addHandler { request -> when(request.url.encodedPath) {
                "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"synthetic"}}""")
                "/api/bonds/notifications/preferences" -> if(request.method==HttpMethod.Put) {
                    if(fail)respond("{}",HttpStatusCode.ServiceUnavailable)
                    else { kotlinx.coroutines.delay(1000);respond(prefs(true)) }
                } else respond(prefs(false))
                "/api/bonds/notifications" -> respond("""{"notifications":[],"unreadCount":0}""")
                else -> respond("{}")
            } }
        })) })
        store.put("model",model)
        try {
            advanceUntilIdle();assertTrue(model.notificationSettingsAvailable)
            model.updateNotificationPreference(enabled=true);advanceUntilIdle()
            assertFalse(model.notificationPreferences.optBoolean("autoCoupon"));assertNotNull(model.error)
            fail=false;model.updateNotificationPreference(enabled=true);runCurrent()
            model.logout();runCurrent();advanceUntilIdle()
            assertNull(model.user);assertFalse(model.notificationSettingsAvailable)
            assertFalse(model.notificationPreferences.has("autoCoupon"))
        } finally { store.clear();Dispatchers.resetMain() }
    }

}
