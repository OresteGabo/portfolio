@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.DEFAULT_ORIGIN
import dev.orestegabo.portfolio.network.LEGACY_WEBSITE_API_ORIGIN
import dev.orestegabo.portfolio.network.PortfolioApi

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class DirectBackendMigrationTest {
    private class Settings(var saved: String?, var choice: String? = null) : ServerSettings {
        override fun readOrigin() = saved
        override fun writeOrigin(origin: String) { saved=origin }
        override fun readSessionChoice() = choice
        override fun writeSessionChoice(value: String?) { choice=value }
    }
    private class Vault : SecureSignIn {
        override val available = true
        override val unlockLabel = "Device unlock"
        var reads=0; var writes=0; var deletes=0; val checked=mutableListOf<String>()
        override fun hasSaved(origin: String): Boolean { checked+=origin; return origin==LEGACY_WEBSITE_API_ORIGIN }
        override suspend fun read(origin: String): String? { reads++; error("Legacy credentials must not be read") }
        override suspend fun save(origin: String, value: String): Boolean { writes++; error("Legacy credentials must not be copied") }
        override fun delete(origin: String) { deletes++ }
        override fun lock() {}
    }
    @Test fun existingAccountMovesItsEndpointWithoutReadingCopyingOrDeletingOldCredentials() = runTest {
        for (saved in listOf(LEGACY_WEBSITE_API_ORIGIN,"https://invest.orestegabo.dev",null)) {
            val settings=Settings(saved);val vault=Vault();val store=ViewModelStore();val requests=mutableListOf<String>()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model=PortfolioModel(settings,vault=vault,assets={"[]".encodeToByteArray()},apiFactory={ origin ->
                PortfolioApi(origin,MockEngine(MockEngineConfig().apply {
                    dispatcher=StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        requests+=request.url.host
                        assertEquals(DEFAULT_ORIGIN,request.headers["Origin"])
                        respond("""{"authenticated":false}""")
                    }
                }))
            }).also {store.put("model",it)}
            try {
                advanceUntilIdle()
                assertEquals(DEFAULT_ORIGIN,model.origin);assertEquals(DEFAULT_ORIGIN,settings.saved)
                assertEquals(MANUAL_ACCOUNT_ENTRY,settings.choice)
                assertFalse(model.locked);assertFalse(model.secureSignInEnabled);assertNull(model.user)
                assertEquals("Please sign in again to reconnect your account.",model.message)
                assertEquals(0,vault.reads);assertEquals(0,vault.writes);assertEquals(0,vault.deletes)
                assertTrue(requests.isEmpty())
                model.startAccountSession();advanceUntilIdle()
                assertTrue(requests.isNotEmpty());assertTrue(requests.all { it=="gabo-portfolio-api-639858675564.europe-west1.run.app" })
                assertEquals(0,vault.reads)
            } finally {store.clear();Dispatchers.resetMain()}
        }
    }
    @Test fun guestModeRemainsOfflineAndDevelopmentOverrideDoesNotMigrateProductionSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store=ViewModelStore();val vault=Vault();val settings=Settings(LEGACY_WEBSITE_API_ORIGIN,GuestPath.PERSONAL.stored)
        val model=PortfolioModel(settings,vault=vault,assets={"[]".encodeToByteArray()},apiFactory={error("Guest mode must not create an API")}).also {store.put("guest",it)}
        try {
            advanceUntilIdle();assertTrue(model.isGuest);assertEquals(GuestPath.PERSONAL.stored,settings.choice)
            assertTrue(vault.checked.isEmpty());assertEquals(0,vault.reads)
            val developmentSettings=Settings(LEGACY_WEBSITE_API_ORIGIN,MANUAL_ACCOUNT_ENTRY)
            val dev=PortfolioModel(developmentSettings,initialOrigin="http://localhost:8080",assets={"[]".encodeToByteArray()},apiFactory={error("Manual entry must not create an API")}).also {store.put("dev",it)}
            advanceUntilIdle();assertEquals("http://localhost:8080",dev.origin)
            assertEquals(LEGACY_WEBSITE_API_ORIGIN,developmentSettings.saved)
        } finally {store.clear();Dispatchers.resetMain()}
    }
}
