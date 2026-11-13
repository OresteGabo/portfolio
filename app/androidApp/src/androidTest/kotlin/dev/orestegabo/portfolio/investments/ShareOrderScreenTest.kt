package dev.orestegabo.portfolio.investments

import android.view.WindowManager
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Synthetic account/quote only. The tested button records a request, never a completed trade. */
class ShareOrderScreenTest {
    @Test fun marketPriceDefaultsAndCommissionMatchesRequestedExample() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val writes=AtomicInteger(); val equityWrites=AtomicInteger(); val bondRequests=AtomicInteger(); val contractRequests=AtomicInteger()
        val settings=object: ServerSettings {
            override fun readOrigin()="https://share-order.example.test"
            override fun writeOrigin(origin: String) {}
            override fun readOnboardingCompleted()=true
            override fun readLanguage()="en"
        }
        val nativeActions=object: NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) {}
            override fun shareText(text: String) {}
        }
        var saved: JsonRecord?=null
        fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if(root==null) emptyList() else listOf(root)+(0 until root.childCount).flatMap {nodes(root.getChild(it))}
        fun await(text: String): AccessibilityNodeInfo {
            val deadline=System.currentTimeMillis()+15000
            while(System.currentTimeMillis()<deadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {(it.text?.toString()==text || it.contentDescription?.toString()==text)}?.let {return it}
                Thread.sleep(80)
            }
            error("Expected UI text: $text")
        }
        fun click(text: String, lastMatch: Boolean = false) { var node=await(text);if(lastMatch)node=nodes(instrumentation.uiAutomation.rootInActiveWindow).last {it.isVisibleToUser && (it.text?.toString()==text || it.contentDescription?.toString()==text)};while(!node.isClickable && node.parent!=null)node=node.parent;assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync() }
        val priorLanguage=AppLanguage.preference
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: PortfolioModel
            scenario.onActivity { activity ->
                model=PortfolioModel(settings,assets={"[]".encodeToByteArray()},apiFactory={origin -> PortfolioApi(origin,MockEngine { request ->
                    val path=request.url.encodedPath
                    val response=when {
                        path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"synthetic-order-account","email":"synthetic@example.test"}}"""
                        path.endsWith("/purchases") -> """{"purchases":[{"id":"synthetic-pending-bond","issuer":"Government of Rwanda","currency":"RWF","status":"submitted","bondName":"SYNTHETIC pending 10-year bond","purchaseDate":"$Today","settlementDate":"$Today","maturityDate":"2036-11-13","tenorYears":10,"faceValue":1000000,"pricePercent":100,"feesPaid":11000,"couponRate":0,"withholdingTaxRate":0.05}]}"""
                        path.endsWith("/coupon-terms") -> """{"terms":[{"isin":"RWREFERENCE","issuer":"Government of Rwanda","currency":"RWF","maturityDate":"2036-08-01","tenorYears":10,"couponRate":0.123}]}"""
                        path.endsWith("/share-orders") -> JsonRecord().put("orders",JsonList(saved?.let {listOf(it)} ?: emptyList<JsonRecord>())).toString()
                        path.contains("/share-orders/") && request.method==HttpMethod.Put -> {
                            val data=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                            assertEquals(720.0,data.getDouble("pricePerShare"),0.0); assertEquals(if(writes.get()==0) 1000.0 else 1100.0,data.getDouble("shares"),0.0)
                            val budget=shareOrderEstimate(data.getDouble("shares"),data.getDouble("pricePerShare"))
                            saved=data.put("id",path.substringAfterLast('/')).put("version",writes.get()+1).put("status","pending").put("currency","RWF").put("estimatedTotal",budget.total)
                            writes.incrementAndGet();JsonRecord().put("order",saved!!).toString()
                        }
                        path.endsWith("/equities") -> { if(request.method==HttpMethod.Post)equityWrites.incrementAndGet();"""{"holdings":[{"id":"synthetic-owned-shares","instrumentType":"equity","exchange":"RSE","currency":"RWF","securityName":"SYNTHETIC Bralirwa","ticker":"BLR","shares":100,"pricePerShare":571,"grossConsideration":57100,"totalCharges":0,"netAmountPayable":57100,"status":"active","tradeDate":"$Today","settlementDate":"$Today"}]}""" }
                        path.endsWith("/rse/quotes") -> """{"source":{"status":"available","publishedAt":"2026-11-13T12:00:00Z"},"quotes":[{"ticker":"BOK","name":"SYNTHETIC BK Group","closing":720},{"ticker":"BLR","name":"SYNTHETIC Bralirwa","closing":571}]}"""
                        else -> "{}"
                    }
                    respond(response,headers=headersOf(HttpHeaders.ContentType,"application/json"))
                })})
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) // Synthetic test content only.
                activity.viewModelStore.put("share-order-test",model)
                AppLanguage.initialize(settings)
                activity.setContent { CompositionLocalProvider(LocalNativeActions provides nativeActions) { MaterialTheme(colorScheme=structCapitalColorScheme(InstrumentationRegistry.getArguments().getString("dark")=="true")) {
                    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    var order by remember { mutableStateOf<JsonRecord?>(null) }
                    if (order == null) PortfolioScreen(model, uploadPending={},openMissing={},allMissing={},seeAll={},openCalendar={},detail={},openShare={},
                        editShareOrder={ order=it }, addTemporaryBond={bondRequests.incrementAndGet()})
                    else Box(Modifier.fillMaxSize().safeDrawingPadding()) { ShareOrderScreen(model,order!!,back={order=null},recordExecution={error("Do not record execution during order creation")}, uploadContract={ assertEquals(saved!!.text("id"),it.text("id"));contractRequests.incrementAndGet() }) }
                    }
                } } }
            }
            val readyDeadline=System.currentTimeMillis()+15000
            var ready=false
            while(!ready && System.currentTimeMillis()<readyDeadline) {
                instrumentation.runOnMainSync { ready=model.user!=null && model.shareOrdersAvailable && !model.busy && model.sharedCouponTerms.isNotEmpty() }
                if(!ready)Thread.sleep(50)
            }
            assertTrue("Synthetic account and orders API ready",ready)
            await("Annual net coupon");await("≈ 116,850")
            scenario.onActivity {
                val total=model.portfolio.summary(Today).single()
                assertEquals(116850.0,total.netAnnualCoupon,0.0)
                assertEquals(0.0,total.confirmedAnnualCoupon,0.0)
                assertEquals(116850.0,total.pendingAnnualCoupon,0.0)
            }
            click("Valuation basis details")
            await("Confirmed annual net coupon");await("Pending annual net coupon")
            Thread.sleep(300) // Capture the dialog after its opening fade.
            val estimateScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-coupon-assumptions.png").outputStream().use {estimateScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            estimateScreenshot.recycle()
            click("Done")
            val menuDeadline=System.currentTimeMillis()+15000
            while(nodes(instrumentation.uiAutomation.rootInActiveWindow).none {it.contentDescription?.toString()=="Investment actions"} && System.currentTimeMillis()<menuDeadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync();Thread.sleep(80)
            }
            await("Investment actions")
            assertFalse(nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.text?.toString()=="Add shares"})
            val homeScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-share-order-home.png").outputStream().use {homeScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            homeScreenshot.recycle()
            click("Investment actions")
            await("Add pending investment"); await("Confirm later with your broker’s PDF.")
            Thread.sleep(250) // Capture the menu after its opening fade.
            val menuScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-investment-menu.png").outputStream().use {menuScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            menuScreenshot.recycle()
            click("Bond")
            assertEquals(1,bondRequests.get())
            click("Investment actions");await("Add pending investment");click("Shares",lastMatch=true)
            await("Security name"); click("Security name")
            await("SYNTHETIC Bralirwa (BLR)")
            click("SYNTHETIC BK Group (BOK)")
            assertFalse(nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.text?.toString() in listOf("Ticker","Broker","Notes","Expected price per share (RWF)","Request date · YYYY-MM-DD")})
            await("Market commission · 1.71%")
            repeat(10) { click("Increase shares") }
            await("RWF 732,312")
            click("Increase shares")
            await("RWF 805,543.2")
            click("Decrease shares")
            await("RWF 732,312")
            val counter=await("1,000")
            assertFalse("Share count is read-only",counter.isEditable)
            assertFalse("Only the stepper can change the quantity",nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.isEditable})
            counter.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertFalse(nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.text?.toString()=="Done"})
            val screenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-share-order-screen.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            screenshot.recycle()
            click("Save pending order")
            val deadline=System.currentTimeMillis()+5000
            while(writes.get()==0 && System.currentTimeMillis()<deadline)Thread.sleep(50)
            assertEquals(1,writes.get());assertEquals(0,equityWrites.get())
            assertEquals(732312.0,saved!!.getDouble("estimatedTotal"),0.0)
            fun pendingCardVisible() = nodes(instrumentation.uiAutomation.rootInActiveWindow).any {
                val text=it.text?.toString().orEmpty()
                it.isVisibleToUser && (text=="Pending" || text.contains("Bank of Kigali") && text.contains("Pending"))
            }
            val cardDeadline=System.currentTimeMillis()+15000
            while(!pendingCardVisible() && System.currentTimeMillis()<cardDeadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync();Thread.sleep(80)
            }
            assertTrue("Pending shares appear in the normal investment list",pendingCardVisible())
            nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            instrumentation.waitForIdleSync()
            assertFalse(nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.text?.toString()=="Pending share orders"})
            assertTrue("Share cards use the short company name",nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.text?.toString()?.contains("Bank of Kigali")==true})
            scenario.onActivity { assertEquals(1,model.holdings.size);assertEquals("synthetic-owned-shares",model.holdings.single().text("id")) }
            val cardsScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-pending-share-card.png").outputStream().use {cardsScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            cardsScreenshot.recycle()
            scenario.onActivity { assertEquals(789412.0,model.portfolio.summary(Today).single().shareCashCost,0.0) }
            var pendingCard = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.toString()?.contains("Bank of Kigali") == true }
            while(!pendingCard.isClickable && pendingCard.parent!=null)pendingCard=pendingCard.parent
            assertTrue(pendingCard.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync()
            await("Edit")
            assertFalse("Saved orders do not show save or remove controls",nodes(instrumentation.uiAutomation.rootInActiveWindow).any {
                it.text?.toString() in listOf("Save pending order","Save changes","Cancel pending order")
            })
            assertFalse("Saved quantity has no steppers",nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.contentDescription?.toString() in listOf("Increase shares","Decrease shares")})
            await("Order price")
            val viewScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-pending-share-view.png").outputStream().use {viewScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            viewScreenshot.recycle()
            click("Edit")
            click("Increase shares")
            await("RWF 805,543.2")
            val cancelDeadline=System.currentTimeMillis()+5000
            while(nodes(instrumentation.uiAutomation.rootInActiveWindow).none {it.text?.toString()=="Cancel" && it.isVisibleToUser} && System.currentTimeMillis()<cancelDeadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync()
            }
            click("Cancel")
            await("RWF 732,312")
            assertEquals("Cancel editing never writes or removes the order",1,writes.get())
            val uploadDeadline=System.currentTimeMillis()+5000
            while(nodes(instrumentation.uiAutomation.rootInActiveWindow).none {it.text?.toString()=="Upload contract" && it.isVisibleToUser} && System.currentTimeMillis()<uploadDeadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync()
            }
            click("Upload contract")
            assertEquals(1,contractRequests.get());assertEquals(0,equityWrites.get())
            val editDeadline=System.currentTimeMillis()+5000
            while(nodes(instrumentation.uiAutomation.rootInActiveWindow).none {it.text?.toString()=="Edit" && it.isVisibleToUser} && System.currentTimeMillis()<editDeadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                instrumentation.waitForIdleSync()
            }
            click("Edit");click("Increase shares");await("RWF 805,543.2")
            val saveDeadline=System.currentTimeMillis()+5000
            while(nodes(instrumentation.uiAutomation.rootInActiveWindow).none {it.text?.toString()=="Save changes" && it.isVisibleToUser} && System.currentTimeMillis()<saveDeadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync()
            }
            Thread.sleep(100) // Let the edited total and button state settle after scrolling.
            val editScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-pending-share-edit.png").outputStream().use {editScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            editScreenshot.recycle()
            click("Save changes")
            val savedDeadline=System.currentTimeMillis()+5000
            while(writes.get()<2 && System.currentTimeMillis()<savedDeadline)Thread.sleep(50)
            assertEquals(2,writes.get());assertEquals(1100.0,saved!!.optDouble("shares"),0.0)
            assertEquals(0,equityWrites.get())
        } } finally { AppLanguage.select(priorLanguage) }
    }
}
