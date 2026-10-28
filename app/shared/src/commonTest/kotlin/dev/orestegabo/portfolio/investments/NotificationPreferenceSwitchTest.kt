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

class NotificationPreferenceSwitchTest {
    private class Fixture {
        val store = ViewModelStore()
        val categories = linkedMapOf("prices" to false, "coupons" to true, "maturities" to false,
            "subscriptions" to true, "pending" to false, "documents" to true, "targets" to false, "reports" to true, "security" to true)
        var auto = false
        var target = 200000L
        var failAuto = false
        var partial = false
        var slowRead = false
        var signedIn = true
        val writes = mutableListOf<JsonRecord>()
        fun preferences() = JsonRecord().put("configured", true).put("autoCoupon", auto).put("monthlyTarget", target)
            .put("categories", JsonRecord().apply { categories.forEach { (key, value) -> put(key, value) } })
    }
    private fun TestScope.model(f: Fixture): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://notification-switches.example.test"
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> when(request.url.encodedPath) {
                    "/api/bonds/auth/session" -> respond("""{"authenticated":${f.signedIn},"user":{"id":"switch-test"}}""")
                    "/api/bonds/mobile-overview" -> respond("""{"version":1,"recentPurchaseIds":[],"recentHoldingIds":[],"purchases":[],"holdings":[],"monthlyInvestmentTotals":[]}""")
                    "/api/bonds/documents" -> respond("""{"documents":[]}""")
                    "/api/bonds/notifications" -> respond("""{"notifications":[],"unreadCount":0}""")
                    "/api/bonds/notifications/preferences" -> if (request.method == HttpMethod.Put) {
                        val change = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        f.writes += change
                        delay(1000)
                        if (f.failAuto && change.has("autoCoupon")) respond("{}", HttpStatusCode.ServiceUnavailable)
                        else {
                            if (change.has("autoCoupon")) f.auto = change.optBoolean("autoCoupon")
                            change.optJSONObject("categories")?.let { rows -> f.categories.keys.toList().forEach { key ->
                                assertTrue(rows.has(key), "The API requires the full category map")
                                f.categories[key] = rows.optBoolean(key)
                            } }
                            change.text("monthlyTarget").toLongOrNull()?.let { f.target = it }
                            val saved = if (!f.partial) f.preferences() else JsonRecord().apply {
                                if (change.has("autoCoupon")) put("autoCoupon", f.auto)
                                if (change.has("categories")) put("categories", f.preferences().optJSONObject("categories"))
                                if (change.has("monthlyTarget")) put("monthlyTarget", f.target)
                            }
                            respond(saved.toString())
                        }
                    } else {
                        val snapshot = f.preferences().toString()
                        if (f.slowRead) delay(10000)
                        respond(snapshot)
                    }
                    "/api/bonds/auth/logout" -> { f.signedIn = false; respond("""{"authenticated":false}""") }
                    else -> respond("{}")
                } }
            }))
        }).also { f.store.put("model", it) }
    }
    private fun assertCategories(model: PortfolioModel, values: Map<String, Boolean>) {
        values.forEach { (key, expected) ->
            assertEquals(expected, model.notificationPreferenceEnabled(key), key)
            assertFalse(model.notificationPreferenceSaving(key), "Untouched switch $key must stay enabled")
        }
    }
    @Test fun checkingAndUncheckingAutoConfirmLeavesEveryOtherSwitchAlone() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle(); val initial = f.categories.toMap()
            for (enabled in listOf(true, false)) {
                m.updateNotificationPreference(enabled = enabled)
                assertEquals(enabled, m.notificationPreferenceEnabled())
                assertTrue(m.notificationPreferenceSaving())
                assertCategories(m, initial)
                runCurrent(); assertCategories(m, initial)
                advanceUntilIdle()
                assertEquals(enabled, m.notificationPreferences.optBoolean("autoCoupon"))
                assertFalse(m.notificationSettingsSaving)
                assertCategories(m, initial); assertEquals(initial, f.categories)
                assertFalse(f.writes.last().has("categories"))
            }
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun rapidCategoryChangesPreserveThePrecedingAcknowledgedChange() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.updateNotificationPreference("prices", true)
            m.updateNotificationPreference("maturities", true)
            assertTrue(m.notificationPreferenceEnabled("prices")); assertTrue(m.notificationPreferenceEnabled("maturities"))
            assertTrue(m.notificationPreferenceSaving("prices")); assertTrue(m.notificationPreferenceSaving("maturities"))
            assertFalse(m.notificationPreferenceSaving("security"))
            advanceUntilIdle()
            assertEquals(2, f.writes.size)
            val second = f.writes[1].getJSONObject("categories")
            assertTrue(second.optBoolean("prices")); assertTrue(second.optBoolean("maturities"))
            assertTrue(f.categories.getValue("prices")); assertTrue(f.categories.getValue("maturities"))
            assertFalse(f.auto); assertFalse(m.notificationSettingsSaving)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun anAutoConfirmAndCategoryChangeDoNotOverwriteEachOther() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.updateNotificationPreference(enabled = true)
            m.updateNotificationPreference("coupons", false)
            assertFalse(m.notificationPreferenceEnabled("coupons")); assertTrue(m.notificationPreferenceEnabled())
            assertFalse(m.notificationPreferenceSaving("prices"))
            advanceUntilIdle()
            assertTrue(f.auto); assertFalse(f.categories.getValue("coupons"))
            assertTrue(m.notificationPreferenceEnabled()); assertFalse(m.notificationPreferenceEnabled("coupons"))
            assertFalse(f.writes[1].has("autoCoupon"))
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun failureRevertsOnlyTheFailedSwitchAndDoesNotCancelAnotherChange() = runTest {
        val f = Fixture().apply { failAuto = true }; val m = model(f)
        try {
            advanceUntilIdle()
            m.updateNotificationPreference(enabled = true)
            m.updateNotificationPreference("prices", true)
            advanceUntilIdle()
            assertFalse(m.notificationPreferenceEnabled()); assertFalse(f.auto)
            assertTrue(m.notificationPreferenceEnabled("prices")); assertTrue(f.categories.getValue("prices"))
            assertFalse(m.notificationPreferenceEnabled("maturities"))
            assertNotNull(m.error); assertFalse(m.notificationSettingsSaving)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aPartialSaveResponseDoesNotResetCategoriesWhileTheRefreshIsPending() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle(); val initial = f.categories.toMap()
            f.partial = true; f.slowRead = true
            m.updateNotificationPreference(enabled = true); runCurrent()
            advanceTimeBy(1000); runCurrent()
            assertTrue(m.notificationPreferences.optBoolean("autoCoupon"))
            assertFalse(m.notificationSettingsSaving)
            assertCategories(m, initial)
            advanceUntilIdle(); assertCategories(m, initial)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun anOlderRefreshDoesNotBounceThePendingSwitchBack() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle(); f.slowRead = true
            m.refreshNotifications(); runCurrent()
            m.updateNotificationPreference(enabled = true); runCurrent()
            assertTrue(m.notificationPreferenceEnabled()); assertTrue(f.writes.isEmpty())
            advanceTimeBy(10000); runCurrent()
            assertTrue(m.notificationPreferenceEnabled())
            advanceUntilIdle()
            assertTrue(m.notificationPreferences.optBoolean("autoCoupon")); assertTrue(f.auto)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun repeatedTapsOnTheSamePendingSwitchDoNotSendDuplicateWrites() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.updateNotificationPreference(enabled = true)
            m.updateNotificationPreference(enabled = true)
            advanceUntilIdle()
            assertEquals(1, f.writes.size); assertTrue(f.auto)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun logoutDropsPendingValuesAndPreventsAQueuedWriteForTheOldAccount() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.updateNotificationPreference(enabled = true); m.updateNotificationPreference("prices", true)
            runCurrent(); m.logout(); runCurrent(); advanceUntilIdle()
            assertNull(m.user); assertFalse(m.notificationSettingsAvailable)
            assertFalse(m.notificationSettingsSaving); assertFalse(m.notificationPreferences.has("autoCoupon"))
            assertEquals(1, f.writes.size)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aMonthlyTargetResponseCannotReplaceTheNotificationSwitchState() = runTest {
        val f = Fixture().apply { auto = true; partial = true }; val m = model(f)
        try {
            advanceUntilIdle(); val initial = f.categories.toMap()
            m.updateMonthlyInvestmentTarget(300000); advanceUntilIdle()
            assertEquals(300000, m.notificationPreferences.optInt("monthlyTarget"))
            assertTrue(m.notificationPreferenceEnabled()); assertCategories(m, initial)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
}
