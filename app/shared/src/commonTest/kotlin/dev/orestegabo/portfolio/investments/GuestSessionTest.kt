@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class GuestSessionTest {
    private class Settings(var choice: String? = null) : ServerSettings {
        var rejectWrite = false
        override fun readSessionChoice() = choice
        override fun writeSessionChoice(value: String?) { check(!rejectWrite); choice = value }
        override fun readOrigin() = "https://fixture.example.test"
        override fun writeOrigin(origin: String) {}
    }
    private fun TestScope.model(settings: Settings, defer: Boolean = true, response: suspend (String) -> String = { "{}" }, factory: () -> Unit = {}, vault: SecureSignIn? = null, observe: (HttpMethod, String) -> Unit = { _, _ -> }): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(settings, vault = vault, assets = { "[]".encodeToByteArray() }, deferAccountStartup = defer, apiFactory = { origin ->
            factory()
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> observe(request.method, request.url.encodedPath); respond(response(request.url.encodedPath), headers = headersOf(HttpHeaders.ContentType,"application/json")) }
            }))
        })
    }
    private fun dispose(model: PortfolioModel) { ViewModelStore().apply { put("fixture", model); clear() }; Dispatchers.resetMain() }

    @Test fun guestStartupAndRestartNeverConstructAClientOrImpersonateAnAccount() = runTest {
        for (path in GuestPath.entries) {
            val settings = Settings(path.stored)
            repeat(2) {
                var clients = 0
                val model = model(settings, defer = false, factory = { clients++ })
                try {
                    model.foreground(); model.background(); advanceUntilIdle()
                    assertEquals(SessionState.Guest(path), model.sessionState)
                    assertNull(model.user); assertTrue(model.portfolio.purchases.isEmpty()); assertFalse(model.busy)
                    assertEquals(0, clients)
                    assertFailsWith<IllegalStateException> { model.api }
                } finally { dispose(model) }
            }
        }
    }
    @Test fun onboardingCanEnterGuestWithoutWaitingForAccountStartup() = runTest {
        val settings = Settings()
        var requests = 0
        val model = model(settings, response = { requests++; "{}" })
        try {
            assertTrue(model.enterGuest(GuestPath.PERSONAL)); advanceUntilIdle()
            assertEquals(GuestPath.PERSONAL.stored, settings.choice)
            assertEquals(SessionState.Guest(GuestPath.PERSONAL), model.sessionState)
            assertEquals(0, requests)
        } finally { dispose(model) }
    }
    @Test fun lateAccountSessionCannotReplaceGuestSelection() = runTest {
        val waiting = CompletableDeferred<Unit>()
        val model = model(Settings(), defer = false, response = {
            if (it.endsWith("/auth/session")) withContext(NonCancellable) { waiting.await(); """{"authenticated":true,"user":{"id":"cloud","email":"fixture@example.test"}}""" }
            else "{}"
        })
        try {
            runCurrent(); assertTrue(model.busy)
            assertTrue(model.enterGuest(GuestPath.SAMPLE)); waiting.complete(Unit); advanceUntilIdle()
            assertEquals(SessionState.Guest(GuestPath.SAMPLE), model.sessionState)
            assertNull(model.user); assertTrue(model.portfolio.purchases.isEmpty())
        } finally { dispose(model) }
    }
    @Test fun sampleAndPersonalSwitchesNeverCopyExamplesIntoRealRecords() = runTest {
        val settings = Settings()
        val model = model(settings)
        try {
            model.enterGuest(GuestPath.SAMPLE)
            assertTrue(guestSamplePortfolio().purchases.all { it.text("id").startsWith("sample:") && it.text("bondName").contains("fictional") })
            model.enterGuest(GuestPath.PERSONAL)
            assertTrue(model.purchases.isEmpty()); assertTrue(model.holdings.isEmpty()); assertTrue(model.documents.isEmpty())
            assertEquals(GuestPath.PERSONAL.stored, settings.choice)
        } finally { dispose(model) }
    }
    @Test fun explicitSignInUsesExistingAuthAndDoesNotUploadSampleData() = runTest {
        val settings = Settings(GuestPath.SAMPLE.stored)
        val calls = mutableListOf<String>()
        val writes = mutableListOf<String>()
        val model = model(settings, observe = { method, path -> if (method != HttpMethod.Get) writes += path }, response = { path ->
            calls += path
            when {
                path.endsWith("/auth/login") -> """{"user":{"id":"cloud","email":"fixture@example.test"}}"""
                path.endsWith("/mobile-overview") -> """{"version":1,"purchases":[],"holdings":[]}"""
                else -> "{}"
            }
        })
        try {
            model.beginAccountSignIn(); assertEquals(SessionState.SignedOut, model.sessionState)
            assertEquals(GuestPath.SAMPLE.stored, settings.choice)
            model.login("fixture@example.test","synthetic-password"); advanceUntilIdle()
            assertEquals(SessionState.Authenticated("cloud","https://fixture.example.test"), model.sessionState)
            assertEquals(MANUAL_ACCOUNT_ENTRY, settings.choice)
            assertTrue(calls.contains("/api/bonds/auth/login"))
            assertEquals(listOf("/api/bonds/auth/login"), writes)
            assertTrue(model.purchases.isEmpty())
        } finally { dispose(model) }
    }
    @Test fun failedLoginCanReturnToGuestAndKeepsRestartChoice() = runTest {
        val settings = Settings(GuestPath.PERSONAL.stored)
        val model = model(settings, response = { error("offline") })
        try {
            model.beginAccountSignIn(); model.login("fixture@example.test","synthetic-password"); advanceUntilIdle()
            assertEquals(SessionState.SignedOut, model.sessionState)
            assertEquals(GuestPath.PERSONAL.stored, settings.choice)
            model.returnToGuest(); assertEquals(SessionState.Guest(GuestPath.PERSONAL), model.sessionState)
        } finally { dispose(model) }
    }
    @Test fun guestActionsDoNotCallRemoteApis() = runTest {
        var clients = 0
        val model = model(Settings(GuestPath.PERSONAL.stored), factory = { clients++ })
        try {
            model.loadMarket(); model.loadInvestmentHistory(); model.refresh(); model.confirmCoupon("sample:bond","2027-01-01")
            model.login("fixture@example.test","synthetic-password"); advanceUntilIdle()
            assertEquals(0, clients); assertTrue(model.isGuest)
        } finally { dispose(model) }
    }
    @Test fun failedPreferenceWriteDoesNotPretendGuestWasPersisted() = runTest {
        val settings = Settings().apply { rejectWrite = true }
        val model = model(settings)
        try {
            assertFalse(model.enterGuest(GuestPath.PERSONAL)); advanceUntilIdle()
            assertEquals(SessionState.SignedOut, model.sessionState); assertNull(settings.choice)
            assertNotNull(model.error)
        } finally { dispose(model) }
    }
    @Test fun unknownChoiceFailsClosedWithoutAccountStartup() = runTest {
        var clients = 0
        val model = model(Settings("guest-v99"), defer = false, factory = { clients++ })
        try { advanceUntilIdle(); assertEquals(SessionState.SignedOut, model.sessionState); assertEquals(0,clients) }
        finally { dispose(model) }
    }
    @Test fun guestAndManualAccountChoicesNeverReadSavedCredentialsOnStartup() = runTest {
        for (choice in listOf(GuestPath.PERSONAL.stored, MANUAL_ACCOUNT_ENTRY)) {
            var savedChecks = 0; var reads = 0
            val vault = object : SecureSignIn {
                override val available = true
                override val unlockLabel = "Device unlock"
                override fun hasSaved(origin: String): Boolean { savedChecks++; return true }
                override suspend fun read(origin: String): String? { reads++; error("Credentials must not be read") }
                override suspend fun save(origin: String, value: String) = error("No credential writes")
                override fun delete(origin: String) = error("No credential deletion")
                override fun lock() {}
            }
            val model = model(Settings(choice), defer = false, vault = vault, factory = { error("No account startup") })
            try {
                model.foreground(); advanceUntilIdle()
                assertEquals(0, savedChecks); assertEquals(0, reads); assertFalse(model.locked)
            } finally { dispose(model) }
        }
    }
    @Test fun switchingFromAnAccountClearsCloudDataWithoutTurningItIntoGuestHoldings() = runTest {
        val model = model(Settings(), defer = false, response = { path -> when {
            path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"cloud","email":"fixture@example.test"}}"""
            path.endsWith("/mobile-overview") -> """{"version":1,"purchases":[{"id":"owned","status":"active","currency":"RWF"}],"holdings":[],"recentPurchaseIds":[],"recentHoldingIds":[],"monthlyInvestmentTotals":[]}"""
            else -> "{}"
        } })
        try {
            advanceUntilIdle(); assertIs<SessionState.Authenticated>(model.sessionState)
            assertTrue(model.purchases.isNotEmpty())
            model.enterGuest(GuestPath.PERSONAL)
            assertEquals(SessionState.Guest(GuestPath.PERSONAL), model.sessionState)
            assertNull(model.user); assertTrue(model.purchases.isEmpty()); assertTrue(model.documents.isEmpty())
        } finally { dispose(model) }
    }

}
