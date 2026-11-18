package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ExistingSharePurchaseLinkUiTest {
    @Test fun linkingAnImportedPurchaseKeepsOneHoldingAndItsPdfInBothThemesAndLargeText() {
        val ui = UsabilityTestDriver()
        UsabilityPreferences().use {
            for ((dark, scale) in listOf(false to 1f, true to 1.8f)) {
                val pending = JsonRecord().put("id", "pending-one").put("version", 2).put("status", "pending")
                    .put("ticker", "TEST").put("securityName", "Synthetic UI company").put("currency", "RWF")
                    .put("shares", 100).put("pricePerShare", 100).put("requestDate", "2000-02-01").put("estimatedTotal", 10171)
                val purchase = JsonRecord().put("id", "already-imported").put("status", "active")
                    .put("ticker", "TEST").put("securityName", "Synthetic UI company").put("currency", "RWF")
                    .put("shares", 100).put("pricePerShare", 90).put("tradeDate", "2000-01-01").put("settlementDate", "2000-01-03")
                    .put("grossConsideration", 9000).put("totalCharges", 0).put("netAmountPayable", 9000)
                    .put("contractNote", "SYNTHETIC-LINK").put("contractDocumentId", "saved-contract").put("broker", "Synthetic broker")
                val before = purchase.toString()
                val document = JsonRecord().put("id", "saved-contract").put("category", "Share contract").put("label", "Saved contract")
                UiQualityPortfolioFixture(purchaseRecords = listOf(purchase), documentRecords = listOf(document), pendingRecords = listOf(pending)).use { fixture ->
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        val closed = AtomicInteger()
                        scene.onActivity { host -> host.viewModelStore.put("link-existing", fixture.model); fixture.start() }
                        fixture.ready(ui) { block -> scene.onActivity { block() } }
                        scene.onActivity { host -> host.setContent {
                            val density = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalNumbersHidden provides false) {
                                MaterialTheme(colorScheme = structCapitalColorScheme(dark)) { Surface(Modifier.fillMaxSize()) {
                                    ShareOrderScreen(fixture.model, pending, back = { closed.incrementAndGet() }, recordExecution = { error("No new purchase") },
                                        uploadContract = { error("No upload") })
                                } }
                            }
                        } }
                        ui.scroll("Use imported purchase"); ui.click("Use imported purchase")
                        ui.await("Link imported purchase"); ui.scroll("Contract already in Library")
                        ui.click("TEST · 1 Jan 2000")
                        ui.scroll("Link purchase")
                        ui.waitUntil("selection enabled after fresh history") {
                            ui.nodes().filter { ui.matches(it, "Link purchase") }.any { node ->
                                var parent = node
                                while (!parent.isClickable && parent.parent != null) parent = parent.parent
                                parent.isEnabled
                            }
                        }
                        val bitmap = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                        try { bitmap.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "link-existing-$dark-$scale.png")) }
                        finally { bitmap.recycle() }
                        ui.click("Link purchase")
                        ui.waitUntil("existing purchase linked and refreshed") {
                            !fixture.model.busy && fixture.model.shareOrders.isEmpty() && closed.get() == 1
                        }
                        assertEquals(1, fixture.model.holdings.size)
                        assertEquals(before, fixture.model.holdings.single().toString())
                        assertEquals(0, fixture.uploadedContracts.get()); assertEquals(0, fixture.createdSharePurchases.get())
                        assertEquals(listOf("pending-one"), fixture.confirmedOrders.toList())
                        assertEquals(9000.0, fixture.model.portfolio.summary(Today).single().shareCashCost, 0.0)
                    }
                }
            }
        }
    }
}
