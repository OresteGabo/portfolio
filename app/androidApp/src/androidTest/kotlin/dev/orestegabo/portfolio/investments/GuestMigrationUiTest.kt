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
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import java.util.concurrent.CopyOnWriteArrayList

class GuestMigrationUiTest {
    @Test fun explicitReviewDeclineAndSelectedTransferRetainTheLocalOriginal() {
        val i = InstrumentationRegistry.getInstrumentation()
        val prefs = i.targetContext.getSharedPreferences("connection",0)
        val previous = prefs.all.toMap()
        val language = AppLanguage.preference
        val theme=InstrumentationRegistry.getArguments().getString("theme") ?: "light"
        val disk = object: GuestSnapshotStorage {
            var bytes: ByteArray? = null
            override suspend fun read() = bytes?.copyOf()
            override suspend fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        }
        val originals = mutableMapOf<String,ByteArray>()
        val files = object: GuestDocumentStorage {
            override suspend fun read(id: String) = originals[id]?.copyOf()
            override suspend fun write(id: String, bytes: ByteArray) { originals[id] = bytes.copyOf() }
            override suspend fun delete(id: String) { originals.remove(id) }
        }
        val repo = LocalGuestPortfolioRepository(disk, documentStorage = files)
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
            var choice: String? = "account-manual-v1"
            override fun readSessionChoice() = choice
            override fun writeSessionChoice(value: String?) { choice = value }
            override fun readOrigin(): String? = "https://fixture.example.test"
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

        val requests=CopyOnWriteArrayList<Pair<String,String>>()
        val userId="00000000-0000-4000-8000-000000000001"
        val targetId="00000000-0000-4000-8000-000000000002"
        val api=PortfolioApi("https://fixture.example.test",MockEngine { request ->
            requests+=request.method.value to request.url.encodedPath
            val body=when {
                request.url.encodedPath.endsWith("/auth/login") -> """{"user":{"id":"$userId","email":"tester@example.test"}}"""
                request.url.encodedPath.endsWith("/guest-migration/status") -> """{"accountId":"$userId","items":[]}"""
                request.url.encodedPath.endsWith("/guest-migration/items") -> {
                    val data=JsonRecord((request.body as TextContent).text)
                    """{"sourceId":"${data.text("sourceId")}","kind":"${data.text("kind")}","targetId":"$targetId","status":"created"}"""
                }
                request.url.encodedPath.endsWith("/guest-migration/documents") -> error("Unselected original was uploaded")
                request.url.encodedPath.endsWith("/mobile-overview") -> """{"version":1,"purchases":[],"holdings":[]}"""
                else -> "{}"
            }
            respond(body,headers=headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val original=runBlocking { repo.load() }
        var model: PortfolioModel?=null
        try {
            prefs.edit().putBoolean("onboardingCompleted",true).putString("theme",theme).commit()
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                var window: android.view.Window?=null
                scene.onActivity { host ->
                    window=host.window
                    model=PortfolioModel(settings,guestRepository=repo,assets={"[]".encodeToByteArray()},apiFactory={api},deferAccountStartup=true)
                    host.setContent { CompositionLocalProvider(LocalImportEngine provides engine,LocalNativeActions provides actions) { InvestmentApp(requireNotNull(model)) } }
                    model.login("tester@example.test","synthetic-only-password")
                }
                await("Struct Capital");click("Settings");scroll("Transfer Guest Data");click("Transfer Guest Data")
                await("tester@example.test");scroll("Synthetic bond — UI test");click("Synthetic bond — UI test")
                scroll("Not now");click("Not now")
                assertTrue(requests.none { it.first=="POST" && it.second.contains("guest-migration") })
                click("Transfer Guest Data");await("tester@example.test")
                readerFixtureScreenshot(requireNotNull(window),File(i.targetContext.filesDir,"guest-migration-review-$theme.png"))
                click("Synthetic bond — UI test")
                click("I reviewed the selected data and consent to sending it to this account.")
                click("Transfer selected data");scroll("Saved to your account");await("Saved to your account")
                readerFixtureScreenshot(requireNotNull(window),File(i.targetContext.filesDir,"guest-migration-saved-$theme.png"))
                assertEquals(1,requests.count { it.first=="POST" && it.second.endsWith("/guest-migration/items") })
                assertEquals(0,requests.count { it.first=="POST" && it.second.endsWith("/guest-migration/documents") })
                assertEquals(original.revision,runBlocking { repo.load() }.revision)
                assertEquals(1,originals.size)
                scene.onActivity { model?.let { ViewModelStore().apply { put("test",it);clear() } } }
            }
        } finally {
            api.close();AppLanguage.select(language)
            prefs.edit().clear().apply { previous.forEach { (key,value) -> when(value) {
                is String -> putString(key,value);is Boolean -> putBoolean(key,value);is Int -> putInt(key,value);is Long -> putLong(key,value);is Float -> putFloat(key,value)
            } } }.commit()
        }
    }
}
