@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.OriginBoundDataApi
import dev.orestegabo.portfolio.network.PortfolioSessionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class SessionDependencyBoundaryTest {
    private val origin = "https://first.example.test"
    private class Settings(var origin: String) : ServerSettings {
        override fun readOrigin() = origin
        override fun writeOrigin(origin: String) { this.origin = origin }
    }
    private class Session(override val origin: String) : PortfolioSessionApi {
        val requests = mutableListOf<String>()
        var closed = 0
        var cleared = 0
        var presenceChecks = 0
        var restored: String? = null
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            requests += path
            if (path == "/api/bonds/auth/login") throw ApiException(401, "Synthetic rejected login")
            return JsonRecord().put("authenticated", false)
        }
        override suspend fun bytes(path: String, method: String, body: JsonRecord?) = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
        override suspend fun hasSession(): Boolean { presenceChecks++; return false }
        override suspend fun sessionSnapshot() = "opaque-client-state"
        override suspend fun restoreSession(snapshot: String) { restored = snapshot }
        override suspend fun clearSession() { cleared++; restored = null }
        override fun close() { closed++ }
    }

    @Test fun deferredStartupAndOriginReplacementUseOnlyTheInjectedSessionAndOwnItsLifecycle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val sessions = mutableListOf<Session>()
        val settings = Settings(origin)
        try {
            val model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, deferAccountStartup = true,
                apiFactory = { Session(it).also(sessions::add) })
            store.put("model", model); advanceUntilIdle()
            assertTrue(sessions.isEmpty())
            model.startAccountSession(); advanceUntilIdle()
            assertEquals(listOf("/api/bonds/auth/session"), sessions.single().requests)
            model.connect("https://next.example.test"); advanceUntilIdle()
            assertEquals(listOf(origin, "https://next.example.test"), sessions.map { it.origin })
            assertEquals(1, sessions.first().cleared); assertEquals(1, sessions.first().closed)
            assertEquals("https://next.example.test", settings.origin)
            assertEquals(listOf("/api/bonds/auth/session"), sessions.last().requests)
            store.clear()
            assertEquals(listOf(1, 1), sessions.map { it.closed })
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun normalStartupResolvesTheInjectedSessionWithoutAnHttpEngine() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore(); val session = Session(origin)
        try {
            val model = PortfolioModel(Settings(origin), assets = { "[]".encodeToByteArray() }, apiFactory = { session })
            store.put("model", model); advanceUntilIdle()
            assertNull(model.error); assertFalse(model.busy)
            assertEquals(listOf("/api/bonds/auth/session"), session.requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun savedSessionPresenceDoesNotRequireTheModelToUnderstandItsWireFormat() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore(); val session = Session(origin)
        var deleted = false
        val vault = object : SecureSignIn {
            override val available = true
            override val unlockLabel = "Test unlock"
            override fun hasSaved(origin: String) = true
            override suspend fun read(origin: String) = JsonRecord().put("origin", origin).put("email", "test@example.test")
                .put("password", "synthetic-password").put("session", "not-json-client-state").toString()
            override suspend fun save(origin: String, value: String) = true
            override fun delete(origin: String) { deleted = true }
            override fun lock() {}
        }
        try {
            val model = PortfolioModel(Settings(origin), vault = vault, assets = { "[]".encodeToByteArray() }, apiFactory = { session })
            store.put("model", model); advanceUntilIdle()
            assertTrue(model.locked)
            model.unlock(); advanceUntilIdle()
            assertEquals(1, session.presenceChecks)
            assertEquals(listOf("/api/bonds/auth/login"), session.requests)
            assertTrue(deleted); assertFalse(model.locked)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun migrationRequestAdapterAcceptsOriginBoundDataWithoutSessionOwnership() = runTest {
        val requests = mutableListOf<String>()
        val data = object : OriginBoundDataApi {
            override val origin = this@SessionDependencyBoundaryTest.origin
            override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
                requests += path; return JsonRecord()
            }
            override suspend fun bytes(path: String, method: String, body: JsonRecord?) = byteArrayOf()
        }
        val remote = ApiGuestMigrationRemote(data)
        assertFailsWith<IllegalStateException> { remote.status(MigrationAccount("https://other.example.test", "owner", "")) }
        assertTrue(requests.isEmpty())
        remote.status(MigrationAccount(origin, "owner", ""))
        assertEquals(listOf("/api/bonds/guest-migration/status?accountId=owner"), requests)
    }
}
