package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GuestReleaseReadinessTest {
    @Test fun protectedLocalDashboardColdWarmAndForegroundRemainInteractiveOffline() {
        val i = InstrumentationRegistry.getInstrumentation()
        val prefs = i.targetContext.getSharedPreferences("connection",0)
        val previous = prefs.all.toMap()
        val language = AppLanguage.preference
        val name="test-release-${java.util.UUID.randomUUID()}"
        val storage=AndroidGuestSnapshotStorage(i.targetContext,name)
        val repo=LocalGuestPortfolioRepository(storage,documentStorage=AndroidGuestDocumentStorage(i.targetContext))
        val bond = JsonRecord().put("bondName","Synthetic bond — UI test").put("isin","TEST-BOND-UI").put("instrumentType","treasury")
            .put("currency","RWF").put("status","active").put("purchaseDate","2026-07-15").put("settlementDate","2026-07-16")
            .put("maturityDate","2033-07-15").put("faceValue",2200000).put("pricePercent",100).put("feesPaid",0)
            .put("accruedInterestPaid",0).put("couponRate",.115).put("withholdingTaxRate",.05).put("couponFrequency",2)
            .put("scheduleConfidence","confirmed").put("couponDates",JsonList(listOf("2027-01-15","2027-07-15","2033-07-15")))
        val share = JsonRecord().put("securityName","Synthetic company — UI test").put("ticker","TEST").put("currency","RWF")
            .put("status","active").put("tradeDate","2026-07-15").put("settlementDate","2026-07-16").put("shares",400).put("pricePerShare",90)
            .put("brokerageCommission",615.60).put("rseTransactionFee",0).put("csdTransactionLevy",0).put("cmaTransactionFee",0)
        runBlocking { repo.importPdf(readerFixturePdf(),"synthetic-ui.pdf","bond",bond); repo.saveShare(share) }
        val settings = object: ServerSettings {
            var choice: String? = "guest-personal-v1"
            override fun readSessionChoice() = choice
            override fun writeSessionChoice(value: String?) { choice = value }
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) {}
            override fun readScreenCaptureAllowed() = true
        }
        val engine = object: InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String,payload: JsonRecord,pdf: ByteArray?): JsonRecord = error("Not needed for this local read-only journey")
            override fun cancel() {}
            override fun destroy() {}
        }
        val actions = object: NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String,bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No browser or network")
            override fun shareText(text: String) {}
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            i.uiAutomation.clearCache()
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo) { if (n.isVisibleToUser) nodes += n; for (c in 0 until n.childCount) n.getChild(c)?.let(::visit) }
            i.uiAutomation.rootInActiveWindow?.let(::visit)
            return nodes
        }
        fun find(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
        fun await(label: String) {
            val until = android.os.SystemClock.uptimeMillis() + 12_000
            while (android.os.SystemClock.uptimeMillis() < until) { if (find(label) != null) return; Thread.sleep(100) }
            File(i.targetContext.filesDir,"guest-entry-failure.txt").writeText(nodes().joinToString("\n") { "${it.text} | ${it.contentDescription}" })
            throw AssertionError("Guest control missing: $label")
        }
        fun scroll(label: String) {
            if (find(label) != null) return
            for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) repeat(12) {
                if (find(label) != null) return
                nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }?.performAction(direction)
                i.waitForIdleSync(); Thread.sleep(100)
            }
            await(label)
        }
        fun click(label: String) {
            scroll(label)
            for (candidate in nodes().filter { it.text?.toString() == label || it.contentDescription?.toString() == label }) {
                var n: AccessibilityNodeInfo? = candidate
                while (n != null) {
                    if (n.isClickable) { assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); i.waitForIdleSync(); Thread.sleep(300); return }
                    n = n.parent
                }
            }
            throw AssertionError("Guest action missing: $label")
        }

        val measurements=mutableListOf<String>()
        var model: PortfolioModel?=null
        try {
            prefs.edit().putBoolean("onboardingCompleted",true).putString("theme","light").commit()
            repeat(5) { index ->
                val start=android.os.SystemClock.elapsedRealtimeNanos()
                val loaded=runBlocking { LocalGuestPortfolioRepository(storage).load() }
                assertEquals(1,loaded.portfolio.purchases.size)
                measurements+="protected_load_$index=${(android.os.SystemClock.elapsedRealtimeNanos()-start)/1000}us"
            }
            repeat(2) { pass ->
                val start=android.os.SystemClock.elapsedRealtimeNanos()
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    scene.onActivity { host ->
                        model=PortfolioModel(settings,guestRepository=LocalGuestPortfolioRepository(storage,documentStorage=AndroidGuestDocumentStorage(i.targetContext)),apiFactory={error("Guest attempted account network")})
                        host.setContent { CompositionLocalProvider(LocalImportEngine provides engine,LocalNativeActions provides actions) { InvestmentApp(requireNotNull(model)) } }
                    }
                    await("Guest · On this device");assertTrue(model!!.portfolioReady)
                    measurements+="${if(pass==0)"cold_host" else "warm_host"}_first_data=${(android.os.SystemClock.elapsedRealtimeNanos()-start)/1000}us"
                    val interact=android.os.SystemClock.elapsedRealtimeNanos()
                    click("Calendar");await("Coupons")
                    measurements+="navigation_to_calendar_${pass}=${(android.os.SystemClock.elapsedRealtimeNanos()-interact)/1000}us"
                    scene.onActivity { model!!.background() }
                    scene.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    assertNull(model!!.guestPortfolio)
                    val resume=android.os.SystemClock.elapsedRealtimeNanos()
                    scene.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    scene.onActivity { model!!.foreground() }
                    click("Home");await("Guest · On this device");assertTrue(model!!.portfolioReady)
                    measurements+="foreground_$pass=${(android.os.SystemClock.elapsedRealtimeNanos()-resume)/1000}us"
                    scene.onActivity { model?.let { ViewModelStore().apply { put("test",it);clear() } } }
                }
            }
            File(i.targetContext.filesDir,"guest-release-measurements.txt").writeText(measurements.joinToString("\n"))
            assertEquals(1,runBlocking { repo.load() }.portfolio.documents.size)
        } finally {
            runBlocking { repo.deleteAll();storage.delete() }
            AppLanguage.select(language)
            prefs.edit().clear().apply { previous.forEach { (key,value) -> when(value) {
                is String -> putString(key,value);is Boolean -> putBoolean(key,value);is Int -> putInt(key,value);is Long -> putLong(key,value);is Float -> putFloat(key,value)
            } } }.commit()
        }
    }
}
