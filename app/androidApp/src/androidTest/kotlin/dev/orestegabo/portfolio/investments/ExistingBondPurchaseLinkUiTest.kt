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

class ExistingBondPurchaseLinkUiTest {
    @Test fun linkingImportedConfirmationKeepsItsOriginalPrincipalCommissionAndPdf() {
        val ui = UsabilityTestDriver()
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = error("No new PDF import")
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external URL")
            override fun shareText(text: String) = error("No external sharing")
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No PDF parsing needed")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        UsabilityPreferences().use {
            for ((dark, scale) in listOf(false to 1f, true to 1.8f)) {
                val pending = JsonRecord().put("id", "pending-bond").put("status", "submitted").put("instrumentType", "treasury")
                    .put("issuer", "Government of Rwanda").put("currency", "RWF").put("market", "primary").put("bondName", "Synthetic pending bond")
                    .put("isin", "RWTEST123456").put("purchaseDate", "2000-01-01").put("settlementDate", "2000-01-03").put("maturityDate", "2035-01-03")
                    .put("faceValue", 2000000).put("feesPaid", 11000).put("pricePercent", 100).put("accruedInterestPaid", 0)
                    .put("couponRate", 0).put("couponFrequency", 2).put("withholdingTaxRate", .05).put("tenorYears", 35)
                    .put("firstCouponDate", "").put("couponDates", JsonList()).put("scheduleConfidence", "estimated")
                val purchase = JsonRecord(pending.toString()).put("id", "confirmed-bond").put("status", "active")
                    .put("bondName", "Synthetic confirmed bond").put("faceValue", 1000000).put("feesPaid", 23000).put("pricePercent", 99)
                    .put("couponRate", .12).put("firstCouponDate", "2000-07-03").put("couponDates", JsonList(listOf("2000-07-03", "2035-01-03")))
                    .put("confirmationDocumentId", "saved-confirmation").put("confirmationMethod", "document")
                val doc = JsonRecord().put("id", "saved-confirmation").put("category", "Bond confirmation")
                UiQualityPortfolioFixture(lotCount = 0, documentRecords = listOf(doc), bondRecords = listOf(pending, purchase)).use { fixture ->
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        val closed = AtomicInteger()
                        scene.onActivity { host -> host.viewModelStore.put("bond-link", fixture.model); fixture.start() }
                        fixture.ready(ui) { block -> scene.onActivity { block() } }
                        scene.onActivity { host -> host.setContent {
                            val density = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalNumbersHidden provides false, LocalNativeActions provides actions) {
                                MaterialTheme(colorScheme = structCapitalColorScheme(dark)) { Surface(Modifier.fillMaxSize()) {
                                    BondDetailsScreen(fixture.model, pending, engine, imported = { error("No import") }, back = { closed.incrementAndGet() }, edit = {})
                                } }
                            }
                        } }
                        ui.scroll("Use imported purchase"); ui.click("Use imported purchase")
                        ui.await("Link imported bond confirmation")
                        ui.scroll("Synthetic confirmed bond"); ui.click("Synthetic confirmed bond")
                        ui.scroll("Link purchase")
                        ui.waitUntil("bond selection ready") {
                            ui.nodes().filter { ui.matches(it, "Link purchase") }.any { node ->
                                var parent=node
                                while(!parent.isClickable && parent.parent!=null)parent=parent.parent
                                parent.isEnabled
                            }
                        }
                        val image=requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                        try { image.writeGolden(File(ui.instrumentation.targetContext.cacheDir,"link-bond-$dark-$scale.png")) }
                        finally { image.recycle() }
                        ui.click("Link purchase")
                        ui.waitUntil("one confirmed bond after refresh") {
                            !fixture.model.busy && closed.get()==1 && fixture.model.purchases.size==1
                        }
                        assertEquals(purchase.toString(),fixture.model.purchases.single().toString())
                        assertEquals(1000000.0,fixture.model.portfolio.summary(Today).single().bondFaceValue,0.0)
                        assertEquals(0,fixture.createdBondPurchases.get()); assertEquals(0,fixture.uploadedContracts.get())
                        assertEquals(listOf("pending-bond"),fixture.linkedBonds.toList())
                    }
                }
            }
        }
    }
}
