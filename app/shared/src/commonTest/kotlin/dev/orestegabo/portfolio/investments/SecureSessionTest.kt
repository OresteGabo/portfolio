@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds

class SecureSessionTest {
    private val origin = "https://secure.example.test"
    private class Vault : SecureSignIn {
        override val available = true
        override val unlockLabel = "Device unlock"
        var value: String? = null
        var cancel = false
        var locks = 0
        var reads = 0
        var saves = 0
        var readDelay = 0L
        override var authenticating = false
        override fun hasSaved(origin: String) = value != null
        override suspend fun read(origin: String): String? { reads++; delay(readDelay); return if (cancel) null else value }
        override suspend fun save(origin: String, value: String): Boolean { saves++; this.value = value; return true }
        override fun delete(origin: String) { value = null }
        override fun lock() { locks++ }
    }
    private class Scenario {
        val clock = TestTimeSource()
        val vault = Vault()
        val store = ViewModelStore()
        var sessionValid = true
        var rejectPassword = false
        var failLogout = false
        var requests = 0
        var logins = 0
        var requestDelay: (String) -> Long = { 0 }
        var shares = "[]"
        val paths = mutableListOf<String>()
        val failures = mutableMapOf<String, HttpStatusCode>()
        var offline = false
    }
    private fun seed(scenario: Scenario) {
        val cookie = JsonRecord().put("name", "__Host-bonds_session").put("value", "test-token").put("domain", "secure.example.test").put("path", "/").put("secure", true).put("httpOnly", true).put("encoding", "RAW")
        scenario.vault.value = JsonRecord().put("origin", origin).put("email", "saved@example.test").put("password", "synthetic-password-only")
            .put("session", JsonRecord().put("origin", origin).put("cookies", JsonList(listOf(cookie))).toString()).toString()
    }
    private fun TestScope.model(scenario: Scenario): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, vault = scenario.vault, clock = scenario.clock, apiFactory = { selected ->
            PortfolioApi(selected, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    scenario.requests++
                    scenario.paths += request.url.encodedPath
                    delay(scenario.requestDelay(request.url.encodedPath))
                    if (scenario.offline) throw IllegalStateException("Synthetic offline transport")
                    scenario.failures[request.url.encodedPath]?.let { status -> return@addHandler respond("{}", status) }
                    when (request.url.encodedPath) {
                        "/api/bonds/auth/session" -> respond(if (scenario.sessionValid && request.headers[HttpHeaders.Cookie].orEmpty().contains("test-token")) """{"authenticated":true,"user":{"id":"saved","email":"saved@example.test"}}""" else """{"authenticated":false}""")
                        "/api/bonds/auth/login" -> {
                            scenario.logins++
                            if (scenario.rejectPassword) respond("""{"error":"Invalid credentials"}""", HttpStatusCode.Unauthorized)
                            else { scenario.sessionValid = true; respond("""{"user":{"id":"saved","email":"saved@example.test"}}""", headers = headersOf(HttpHeaders.SetCookie, "__Host-bonds_session=test-token; Path=/; Secure; HttpOnly; Max-Age=43200")) }
                        }
                        "/api/bonds/auth/logout" -> if (scenario.failLogout) respond("{}", HttpStatusCode.ServiceUnavailable) else respond("{}")
                        "/api/bonds/purchases" -> respond("""{"purchases":[{"id":"private"}]}""")
                        "/api/bonds/equities" -> respond("""{"holdings":${scenario.shares}}""")
                        "/api/bonds/documents" -> respond("""{"documents":[]}""")
                        else -> respond("{}")
                    }
                }
            }))
        }).also { scenario.store.put("model", it) }
    }

    @Test fun controlledColdUnlockTiming() = runTest {
        val scenario = Scenario().also(::seed).apply {
            shares = """[{"id":"share","status":"active","tradeDate":"2020-01-01","shares":1,"ticker":"TEST"}]"""
            requestDelay = { path -> when (path) {
                "/api/bonds/auth/session", "/api/bonds/mobile-overview" -> 200
                "/api/bonds/purchases", "/api/bonds/equities" -> 300
                "/api/bonds/documents" -> 1000
                "/api/bonds/statement-items" -> 2000
                "/api/bonds/rse/quotes" -> 16000
                else -> 0
            } }
        }
        val model = model(scenario)
        try {
            advanceUntilIdle()
            val start = testScheduler.currentTime
            model.unlock()
            while (model.locked && testScheduler.currentTime - start < 25000) {
                advanceTimeBy(100); runCurrent()
            }
            println("CONTROLLED_UNLOCK_UI_MS=${testScheduler.currentTime - start}")
            assertEquals(200, testScheduler.currentTime - start)
            assertFalse(model.busy)
            assertTrue(model.portfolioSyncing)
            assertFalse(model.portfolioReady)
            assertNotNull(model.user)
            advanceUntilIdle()
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun coldThenWarmUnlockHaveSeparateWorkAndNoUnnecessaryVaultWrites() = runTest {
        val scenario = Scenario().also(::seed).apply { requestDelay = { 200 } }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.foreground(); advanceUntilIdle()
            val requests = scenario.requests
            val time = testScheduler.currentTime
            model.background(); advanceTimeBy(1000); scenario.clock += 1.seconds
            model.foreground(); runCurrent()
            assertFalse(model.locked); assertFalse(model.busy)
            assertEquals(requests, scenario.requests)
            assertEquals(1, scenario.vault.reads)
            assertEquals(0, scenario.vault.saves)
            println("CONTROLLED_WARM_RESUME_WAIT_MS=${testScheduler.currentTime - time - 1000}")
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun delayedPortfolioDoesNotOwnActionLockAndDuplicateUnlockDoesNotRevalidate() = runTest {
        val scenario = Scenario().also(::seed).apply { requestDelay = { if (it.contains("/auth/")) 100 else 20000 } }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); model.unlock(); model.foreground()
            advanceTimeBy(101); runCurrent()
            assertFalse(model.locked); assertFalse(model.busy); assertTrue(model.portfolioSyncing)
            var usable = false
            model.action { usable = true }; runCurrent()
            assertTrue(usable)
            model.startPortfolioSync(); model.unlock(); runCurrent()
            assertEquals(1, scenario.paths.count { it == "/api/bonds/auth/session" })
            assertEquals(1, scenario.vault.reads)
            advanceUntilIdle()
            assertTrue(model.portfolioReady); assertFalse(model.portfolioSyncing); assertFalse(model.documentsLoading)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun missingInternetAndUnavailableSessionNeverRevealProtectedDataOrRetryPassword() = runTest {
        for (offline in listOf(true, false)) {
            val scenario = Scenario().also(::seed).apply {
                this.offline = offline
                failures["/api/bonds/auth/session"] = HttpStatusCode.ServiceUnavailable
            }
            val model = model(scenario)
            try {
                advanceUntilIdle(); model.unlock(); advanceUntilIdle()
                assertTrue(model.locked); assertNull(model.user); assertTrue(model.purchases.isEmpty())
                assertEquals(0, scenario.logins); assertEquals(1, scenario.requests)
                assertFalse(model.busy); assertNotNull(model.error)
            } finally { scenario.store.clear(); Dispatchers.resetMain() }
        }
    }

    @Test fun failedPortfolioKeepsAuthorizedShellUsableAndCanBeRetried() = runTest {
        val scenario = Scenario().also(::seed).apply { failures["/api/bonds/mobile-overview"] = HttpStatusCode.ServiceUnavailable }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertFalse(model.locked); assertNotNull(model.user); assertFalse(model.busy)
            assertFalse(model.portfolioReady); assertNotNull(model.portfolioSyncError)
            scenario.failures.clear(); model.retryPortfolioSync(); advanceUntilIdle()
            assertTrue(model.portfolioReady); assertNull(model.portfolioSyncError)
            assertEquals(0, scenario.logins)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun slowDocumentsAndStatementsDoNotDelaySavedInvestments() = runTest {
        val scenario = Scenario().also(::seed).apply { requestDelay = { if (it.endsWith("documents") || it.endsWith("statement-items")) 20000 else 0 } }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); runCurrent()
            assertFalse(model.locked); assertTrue(model.portfolioReady); assertFalse(model.busy)
            assertTrue(model.documentsLoading); assertEquals(1, model.purchases.size)
            advanceUntilIdle(); assertFalse(model.documentsLoading)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun unauthorizedConcurrentDataRequestsLockAndNeverLaunchParallelCredentialRenewals() = runTest {
        val scenario = Scenario().also(::seed).apply {
            failures["/api/bonds/mobile-overview"] = HttpStatusCode.Unauthorized
            failures["/api/bonds/documents"] = HttpStatusCode.Unauthorized
            failures["/api/bonds/statement-items"] = HttpStatusCode.Unauthorized
        }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertTrue(model.locked); assertNull(model.user); assertTrue(model.purchases.isEmpty())
            assertEquals(0, scenario.logins); assertFalse(model.busy)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun lockCancelsLateSynchronizationResponses() = runTest {
        val scenario = Scenario().also(::seed).apply { requestDelay = { if (it.contains("/auth/")) 0 else 20000 } }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); runCurrent()
            assertFalse(model.locked); assertTrue(model.portfolioSyncing)
            model.lock(); advanceUntilIdle()
            assertTrue(model.locked); assertNull(model.user); assertTrue(model.purchases.isEmpty())
            assertTrue(model.documents.isEmpty()); assertFalse(model.portfolioSyncing); assertFalse(model.documentsLoading)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun passwordFallbackCancelsSlowServerVerification() = runTest {
        val scenario = Scenario().also(::seed).apply { requestDelay = { 20000 } }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); runCurrent()
            assertTrue(model.verifyingSession); assertTrue(model.locked)
            model.usePassword(); advanceUntilIdle()
            assertFalse(model.locked); assertNull(model.user); assertFalse(model.secureSignInEnabled)
            assertFalse(model.busy); assertFalse(model.verifyingSession)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun expiredCookieSkipsRedundantSessionRequestAndRenewsExactlyOnce() = runTest {
        val scenario = Scenario().also(::seed)
        val saved = JsonRecord(scenario.vault.value!!)
        val snapshot = JsonRecord(saved.text("session"))
        // JsonRecord wrappers are copies: build the changed serialized record explicitly.
        snapshot.put("cookies", JsonList(listOf(snapshot.rows("cookies").single().put("expiresAt", 1))))
        scenario.vault.value = saved.put("session", snapshot.toString()).toString()
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); model.unlock(); advanceUntilIdle()
            assertFalse(model.locked); assertNotNull(model.user)
            assertEquals(0, scenario.paths.count { it == "/api/bonds/auth/session" })
            assertEquals(1, scenario.logins); assertEquals(1, scenario.vault.saves)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun retryDoesNotLetCancelledSectionClearNewLoadingState() = runTest {
        val scenario = Scenario().also(::seed).apply {
            failures["/api/bonds/mobile-overview"] = HttpStatusCode.ServiceUnavailable
            requestDelay = { if (it.endsWith("documents")) 20000 else 0 }
        }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); runCurrent()
            assertNotNull(model.portfolioSyncError); assertTrue(model.documentsLoading)
            scenario.failures.clear(); model.retryPortfolioSync(); runCurrent()
            assertTrue(model.portfolioReady); assertTrue(model.documentsLoading)
            advanceUntilIdle(); assertFalse(model.documentsLoading)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun promptMustCompleteBeforeAnyServerRequestOrDataAppears() = runTest {
        val scenario = Scenario().also(::seed).apply { vault.readDelay = 1500 }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceTimeBy(1000); runCurrent()
            assertTrue(model.locked); assertNull(model.user); assertEquals(0, scenario.requests)
            advanceUntilIdle(); assertFalse(model.locked); assertNotNull(model.user)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun rejectedNotificationAuthorizationAlsoClearsTheSnapshot() = runTest {
        val scenario = Scenario().also(::seed).apply { failures["/api/bonds/notifications/preferences"] = HttpStatusCode.Unauthorized }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertTrue(model.locked); assertNull(model.user); assertTrue(model.purchases.isEmpty())
            assertFalse(model.busy); assertEquals(0, scenario.logins)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun coldStartCannotReadPrivateDataBeforeDeviceUnlock() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            advanceUntilIdle()
            assertTrue(model.locked); assertNull(model.user); assertEquals(0, scenario.requests)
            var executed = false
            model.action { executed = true }
            advanceUntilIdle(); assertFalse(executed)
            model.unlock(); advanceUntilIdle()
            assertFalse(model.locked); assertNotNull(model.user); assertEquals(1, model.purchases.size)
            assertEquals(0, scenario.logins)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancelledBiometricPromptLeavesSessionLocked() = runTest {
        val scenario = Scenario().also(::seed).apply { vault.cancel = true }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertTrue(model.locked); assertNull(model.user); assertEquals(0, scenario.requests)
            assertNotNull(scenario.vault.value)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun expiredServerSessionRenewsWithProtectedSavedCredentials() = runTest {
        val scenario = Scenario().also(::seed).apply { sessionValid = false }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertFalse(model.locked); assertNotNull(model.user); assertEquals(1, scenario.logins)
            assertTrue(JsonRecord(scenario.vault.value!!).text("session").contains("test-token"))
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun invalidSavedPasswordIsForgottenInsteadOfRetryingForever() = runTest {
        val scenario = Scenario().also(::seed).apply { sessionValid = false; rejectPassword = true }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertFalse(model.locked); assertFalse(model.secureSignInEnabled); assertNull(scenario.vault.value)
            assertNull(model.user); assertTrue(model.error.orEmpty().contains("password")); assertEquals(1, scenario.logins)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun backgroundClearsPrivateRecordsAndCancelsPendingActions() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            var wrote = false
            model.action { delay(60_000); wrote = true }
            runCurrent(); model.background(); advanceUntilIdle()
            assertFalse(wrote); assertTrue(model.locked); assertNull(model.user); assertTrue(model.purchases.isEmpty())
            assertNotNull(scenario.vault.value)
            model.foreground(); advanceUntilIdle()
            assertFalse(model.locked); assertNotNull(model.user)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun logoutClearsSavedPasswordEvenIfServerCannotBeReached() = runTest {
        val scenario = Scenario().also(::seed).apply { failLogout = true }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle(); model.logout(); advanceUntilIdle()
            assertNull(model.user); assertFalse(model.secureSignInEnabled); assertNull(scenario.vault.value)
            assertFalse(model.locked); assertTrue(model.purchases.isEmpty())
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun passwordFallbackRemovesSavedSignInWithoutNetworkAccess() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.usePassword(); advanceUntilIdle()
            assertFalse(model.locked); assertFalse(model.secureSignInEnabled); assertNull(scenario.vault.value)
            assertEquals(0, scenario.requests)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun rememberedLoginSurvivesACompletelyNewViewModel() = runTest {
        val scenario = Scenario()
        val first = model(scenario)
        try {
            advanceUntilIdle()
            first.login("saved@example.test", "synthetic-password-only", savePassword = true)
            advanceUntilIdle()
            assertTrue(first.secureSignInEnabled); assertNotNull(scenario.vault.value)
            scenario.store.clear()
            scenario.requests = 0
            val restarted = model(scenario)
            advanceUntilIdle()
            assertTrue(restarted.locked); assertNull(restarted.user); assertEquals(0, scenario.requests)
            restarted.unlock(); advanceUntilIdle()
            assertNotNull(restarted.user); assertFalse(restarted.locked); assertEquals(1, scenario.logins)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun corruptSavedDataNeverAppearsInAnErrorMessage() = runTest {
        val scenario = Scenario().apply { vault.value = "{broken: never-show-this-test-secret" }
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.unlock(); advanceUntilIdle()
            assertTrue(model.locked); assertNull(model.user)
            assertFalse(model.error.orEmpty().contains("never-show-this-test-secret"))
            assertEquals(0, scenario.requests)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun wrongConfirmationPasswordDoesNotSignOutTheCurrentAccount() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle(); model.login("saved@example.test", "synthetic-password-only"); advanceUntilIdle()
            scenario.rejectPassword = true
            model.enableSecureSignIn("wrong-synthetic-password"); advanceUntilIdle()
            assertNotNull(model.user); assertFalse(model.locked); assertFalse(model.secureSignInEnabled)
            assertNull(scenario.vault.value); assertTrue(model.error.orEmpty().contains("Password did not match"))
            assertFalse(model.error.orEmpty().contains("wrong-synthetic-password"))
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun passwordManagerRunsOnlyAfterTheServerAcceptsTheLogin() = runTest {
        val scenario = Scenario().apply { sessionValid = false }
        val model = model(scenario)
        try {
            advanceUntilIdle()
            var offered = 0
            model.login("saved@example.test", "synthetic-password-only") {
                assertEquals(1, scenario.logins)
                offered++
            }
            advanceUntilIdle()
            assertEquals(1, offered)
            assertNotNull(model.user)
            assertFalse(model.secureSignInEnabled) // Passwords and app-vault opt-in remain separate.
            assertNull(scenario.vault.value)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun rejectedCredentialsNeverReachThePasswordManager() = runTest {
        val scenario = Scenario().apply { rejectPassword = true }
        val model = model(scenario)
        try {
            advanceUntilIdle()
            var offered = false
            model.login("saved@example.test", "wrong-synthetic-password") { offered = true }
            advanceUntilIdle()
            assertFalse(offered)
            assertNull(model.user)
            assertNotNull(model.error)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun passwordManagerFailureCannotRejectAnAcceptedLoginOrExposeItsRawError() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            model.login("saved@example.test", "synthetic-password-only") { error("synthetic-password-only") }
            advanceUntilIdle()
            assertNotNull(model.user)
            assertNull(model.error)
            assertTrue(model.message.orEmpty().contains("could not be saved"))
            assertFalse(model.message.orEmpty().contains("synthetic-password-only"))
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun coldLaunchAutomaticallyUnlocksAfterStartupFinishesWithoutATap() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            model.foreground() // Resume can arrive while initial asset loading is busy.
            model.foreground()
            advanceUntilIdle()
            assertEquals(1, scenario.vault.reads)
            assertFalse(model.locked); assertNotNull(model.user)
            model.foreground(); advanceUntilIdle()
            assertEquals(1, scenario.vault.reads)
            model.background()
            advanceTimeBy(SESSION_BACKGROUND_GRACE_MILLIS); runCurrent()
            assertTrue(model.locked); assertNull(model.user)
            model.foreground(); advanceUntilIdle()
            assertEquals(2, scenario.vault.reads)
            assertFalse(model.locked); assertNotNull(model.user)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancellingAutomaticUnlockDoesNotLoopButNextVisitAndManualRetryWork() = runTest {
        val scenario = Scenario().also(::seed).apply { vault.cancel = true }
        val model = model(scenario)
        try {
            model.foreground(); advanceUntilIdle()
            assertEquals(1, scenario.vault.reads); assertTrue(model.locked)
            // The OS authentication sheet can send lifecycle events of its own.
            scenario.vault.authenticating = true
            model.background()
            model.foreground()
            scenario.vault.authenticating = false
            model.foreground(); advanceUntilIdle()
            assertEquals(1, scenario.vault.reads); assertEquals(0, scenario.requests)
            model.background(); model.foreground(); advanceUntilIdle()
            assertEquals(2, scenario.vault.reads); assertTrue(model.locked)
            scenario.vault.cancel = false
            model.unlock(); advanceUntilIdle()
            assertEquals(3, scenario.vault.reads); assertFalse(model.locked)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun noSavedSignInNeverLaunchesDeviceAuthentication() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            model.foreground(); advanceUntilIdle()
            model.background(); model.foreground(); advanceUntilIdle()
            assertEquals(0, scenario.vault.reads)
            assertFalse(model.locked)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun screenshotStyleInterruptionRetainsUnlockSnapshotAndMakesNoRequests() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            model.foreground(); advanceUntilIdle()
            val snapshot = model.portfolio
            val requests = scenario.requests
            val reads = scenario.vault.reads
            repeat(3) {
                model.background()
                advanceTimeBy(500)
                scenario.clock += 0.5.seconds
                model.foreground(); runCurrent()
                assertFalse(model.locked)
                assertSame(snapshot, model.portfolio)
            }
            advanceUntilIdle()
            assertEquals(reads, scenario.vault.reads)
            assertEquals(requests, scenario.requests)
            assertNotNull(model.user)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun suspendedBackgroundTimeoutIsCheckedBeforeResumingEvenIfTimerNeverRan() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            model.foreground(); advanceUntilIdle()
            model.background()
            scenario.clock += 31.seconds // Simulate iOS suspension without executing the coroutine timer.
            model.foreground()
            assertTrue(model.locked)
            assertNull(model.user)
            assertTrue(model.purchases.isEmpty())
            advanceUntilIdle()
            assertFalse(model.locked)
            assertEquals(2, scenario.vault.reads)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun recentDataIsReusedButExpiryExplicitRefreshAndWritesFetchAgain() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            model.foreground(); advanceUntilIdle()
            val initial = scenario.requests
            model.action { model.loadPrivateData() }; advanceUntilIdle()
            assertEquals(initial, scenario.requests)
            scenario.clock += 61.seconds
            model.action { model.loadPrivateData() }; advanceUntilIdle()
            assertTrue(scenario.requests > initial)
            val afterExpiry = scenario.requests
            model.refresh(); advanceUntilIdle()
            assertTrue(scenario.requests > afterExpiry)
            val afterRefresh = scenario.requests
            model.savePurchase(JsonRecord().put("faceValue", 100000), null) { }
            advanceUntilIdle()
            assertTrue(scenario.requests > afterRefresh + 1)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun marketCacheAvoidsRepeatedTabRequestsAndExplicitRefreshBypassesIt() = runTest {
        val scenario = Scenario().also(::seed)
        val model = model(scenario)
        try {
            model.foreground(); advanceUntilIdle()
            model.loadMarket(); advanceUntilIdle()
            val requests = scenario.requests
            model.loadMarket(); advanceUntilIdle()
            assertEquals(requests, scenario.requests)
            model.loadMarket(refresh = true); advanceUntilIdle()
            assertTrue(scenario.requests > requests)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun enablingSavedSignInDuringBackgroundStillHonorsTheOriginalTimeout() = runTest {
        val scenario = Scenario()
        val model = model(scenario)
        try {
            advanceUntilIdle()
            model.login("saved@example.test", "synthetic-password-only", savePassword = true)
            model.background()
            runCurrent()
            assertTrue(model.secureSignInEnabled)
            assertFalse(model.locked)
            advanceTimeBy(SESSION_BACKGROUND_GRACE_MILLIS); runCurrent()
            assertTrue(model.locked)
            assertNull(model.user)
        } finally { scenario.store.clear(); Dispatchers.resetMain() }
    }

}
