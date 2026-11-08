package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CashLedgerUiTest {
    @Test fun shareSaleRequiresReviewUpdatesRemainingSharesAndCanBeCorrectedOffline() {
        val i=InstrumentationRegistry.getInstrumentation()
        val storage=object: GuestSnapshotStorage {
            var data: ByteArray?=null
            override suspend fun read()=data?.copyOf()
            override suspend fun write(bytes: ByteArray) { data=bytes.copyOf() }
        }
        val repo=LocalGuestPortfolioRepository(storage)
        val holding=runBlocking { repo.saveShare(JsonRecord().put("securityName","Synthetic ledger company").put("ticker","TEST").put("currency","RWF").put("status","active")
            .put("tradeDate","2026-07-15").put("settlementDate","2026-07-16").put("shares",400).put("pricePerShare",90).put("brokerageCommission",615.6)
            .put("rseTransactionFee",0).put("csdTransactionLevy",0).put("cmaTransactionFee",0)) }.portfolio.holdings.single()
        val settings=object: ServerSettings {
            override fun readSessionChoice()="guest-personal-v1"
            override fun readOrigin(): String?=null
            override fun writeOrigin(origin: String) {}
            override fun readScreenCaptureAllowed()=true
        }
        fun nodes(): List<AccessibilityNodeInfo> {
            i.uiAutomation.clearCache()
            val result=mutableListOf<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo) { if(n.isVisibleToUser) result+=n;for(c in 0 until n.childCount)n.getChild(c)?.let(::visit) }
            i.uiAutomation.rootInActiveWindow?.let(::visit);return result
        }
        fun find(label: String)=nodes().firstOrNull { it.text?.toString()==label || it.contentDescription?.toString()==label || it.hintText?.toString()==label }
        fun await(label: String) { repeat(100) { if(find(label)!=null)return;Thread.sleep(100) };error("Missing control: $label") }
        fun scroll(label: String) {
            for(direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) repeat(16) {
                if(find(label)!=null)return
                nodes().filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }?.performAction(direction)
                i.waitForIdleSync();Thread.sleep(100)
            };await(label)
        }
        fun click(label: String) {
            scroll(label);var n=find(label)
            while(n!=null && !n.isClickable)n=n.parent
            assertTrue("Clickable $label",n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
            i.waitForIdleSync();Thread.sleep(200)
        }
        fun enter(label: String,value: String) {
            scroll(label)
            fun hasLabel(n: AccessibilityNodeInfo): Boolean {
                if(n.text?.toString()==label || n.hintText?.toString()==label || n.contentDescription?.toString()==label)return true
                for(c in 0 until n.childCount)if(n.getChild(c)?.let(::hasLabel)==true)return true
                return false
            }
            var target=nodes().firstOrNull { it.isEditable && hasLabel(it) }
            if(target==null) { var n=find(label);while(n!=null && !n.isEditable)n=n.parent;target=n }
            assertNotNull("Editable $label",target)
            assertTrue(target!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value) }))
            i.waitForIdleSync();Thread.sleep(150)
        }
        val oldLanguage=AppLanguage.preference
        try {
            AppLanguage.select("en")
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                var model: PortfolioModel?=null
                var window: android.view.Window?=null
                scene.onActivity { host ->
                    window=host.window
                    model=PortfolioModel(settings,guestRepository=repo,assets={"[]".encodeToByteArray()},apiFactory={error("No HTTP in offline ledger test")})
                    model!!.openCashLedger(holding.text("id"))
                    host.setContent { MaterialTheme { CompositionLocalProvider(LocalNumbersHidden provides false) { CashLedgerScreen(requireNotNull(model)) } } }
                }
                await("Add transaction")
                enter("Shares sold","100");enter("Sale price per share","110")
                click("Review transaction");await("Confirm transaction")
                assertTrue(runBlocking { repo.load() }.metadata.rows("cashLedger").isEmpty())
                readerFixtureScreenshot(requireNotNull(window),File(i.targetContext.filesDir,"ledger-sale-review.png"))
                click("Save transaction")
                repeat(100) { if(runBlocking { repo.load() }.metadata.rows("cashLedger").isEmpty())Thread.sleep(100) }
                val saved=runBlocking { repo.load() }
                assertEquals(300.0,saved.portfolio.holdings.single().optDouble("remainingShares"),0.0)
                assertEquals(1846.1,saved.metadata.rows("cashLedger").single().optDouble("realizedGain"),0.0)
                scroll("Recorded cash balance")
                readerFixtureScreenshot(requireNotNull(window),File(i.targetContext.filesDir,"ledger-sale-balance.png"))
                click("Remove transaction");click("Remove")
                repeat(100) { if(runBlocking { repo.load() }.metadata.rows("cashLedger").isNotEmpty())Thread.sleep(100) }
                assertEquals(400.0,runBlocking { repo.load() }.portfolio.holdings.single().optDouble("remainingShares"),0.0)
                scene.onActivity { model?.let { androidx.lifecycle.ViewModelStore().apply { put("ledger",it);clear() } } }
            }
        } finally { AppLanguage.select(oldLanguage) }
    }
}
