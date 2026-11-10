@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

class StartupInitializationTest {
    @Test fun immediateAssetFailureCanClearLockedStateDuringConstruction() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val vault = object : SecureSignIn {
            override val available = true
            override val unlockLabel = "Unlock"
            override fun hasSaved(origin: String) = true
            override suspend fun read(origin: String): String? = null
            override suspend fun save(origin: String, value: String) = true
            override fun delete(origin: String) {}
            override fun lock() {}
        }
        try {
            val model = PortfolioModel(object : ServerSettings {
                override fun readOrigin() = "https://startup.example.test"
                override fun writeOrigin(origin: String) {}
            }, vault = vault, assets = { error("Synthetic missing public asset") },
                apiFactory = { PortfolioApi(it, MockEngine { respond("{}") }) })
            store.put("startup", model)
            assertTrue(model.locked)
            assertFalse(model.busy)
            assertNotNull(model.error)
            assertNull(model.applicationFormsError)
            assertNull(model.documentPreview)
            assertTrue(model.purchases.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
