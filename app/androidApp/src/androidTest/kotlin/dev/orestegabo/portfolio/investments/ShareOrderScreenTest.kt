package dev.orestegabo.portfolio.investments

import android.view.WindowManager
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
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
        val writes=AtomicInteger(); val equityWrites=AtomicInteger()
        val settings=object: ServerSettings {
            override fun readOrigin()="https://share-order.example.test"
            override fun writeOrigin(origin: String) {}
            override fun readOnboardingCompleted()=true
            override fun readLanguage()="en"
        }
        var saved: JsonRecord?=null
        fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if(root==null) emptyList() else listOf(root)+(0 until root.childCount).flatMap {nodes(root.getChild(it))}
        fun await(text: String): AccessibilityNodeInfo {
            val deadline=System.currentTimeMillis()+15000
            while(System.currentTimeMillis()<deadline) {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.text?.toString()==text}?.let {return it}
                Thread.sleep(80)
            }
            error("Expected UI text: $text")
        }
        fun click(text: String) { var node=await(text);while(!node.isClickable && node.parent!=null)node=node.parent;assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) }
        val priorLanguage=AppLanguage.preference
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: PortfolioModel
            scenario.onActivity { activity ->
                model=PortfolioModel(settings,assets={"[]".encodeToByteArray()},apiFactory={origin -> PortfolioApi(origin,MockEngine { request ->
                    val path=request.url.encodedPath
                    val response=when {
                        path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"synthetic-order-account","email":"synthetic@example.test"}}"""
                        path.endsWith("/share-orders") -> JsonRecord().put("orders",JsonList(saved?.let {listOf(it)} ?: emptyList<JsonRecord>())).toString()
                        path.contains("/share-orders/") && request.method==HttpMethod.Put -> {
                            val data=JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                            assertEquals(720.0,data.getDouble("pricePerShare"),0.0); assertEquals(1000.0,data.getDouble("shares"),0.0)
                            val budget=shareOrderEstimate(data.getDouble("shares"),data.getDouble("pricePerShare"))
                            saved=data.put("id",path.substringAfterLast('/')).put("version",1).put("status","pending").put("estimatedTotal",budget.total)
                            writes.incrementAndGet();JsonRecord().put("order",saved!!).toString()
                        }
                        path.endsWith("/equities") -> { if(request.method==HttpMethod.Post)equityWrites.incrementAndGet();"""{"holdings":[]}""" }
                        path.endsWith("/rse/quotes") -> """{"source":{"status":"available","publishedAt":"2026-11-13T12:00:00Z"},"quotes":[{"ticker":"BOK","name":"SYNTHETIC BK Group","closing":720},{"ticker":"BLR","name":"SYNTHETIC Bralirwa","closing":571}]}"""
                        else -> "{}"
                    }
                    respond(response,headers=headersOf(HttpHeaders.ContentType,"application/json"))
                })})
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) // Synthetic test content only.
                activity.viewModelStore.put("share-order-test",model)
                AppLanguage.initialize(settings)
                activity.setContent { MaterialTheme(colorScheme=structCapitalColorScheme(false)) {
                    var order by remember { mutableStateOf<JsonRecord?>(null) }
                    if (order == null) PortfolioScreen(model, uploadPending={},openMissing={},allMissing={},seeAll={},openCalendar={},detail={},openShare={},
                        editShareOrder={ order=it.put("shares",1000) })
                    else ShareOrderScreen(model,order!!,back={},recordExecution={error("Do not record execution during order creation")})
                } }
            }
            val readyDeadline=System.currentTimeMillis()+15000
            var ready=false
            while(!ready && System.currentTimeMillis()<readyDeadline) {
                instrumentation.runOnMainSync { ready=model.user!=null && model.shareOrdersAvailable && !model.busy }
                if(!ready)Thread.sleep(50)
            }
            assertTrue("Synthetic account and orders API ready",ready)
            await("Add shares")
            val homeScreenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-share-order-home.png").outputStream().use {homeScreenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            homeScreenshot.recycle()
            click("Add shares")
            await("Security name"); click("Security name")
            await("SYNTHETIC Bralirwa (BLR)")
            click("SYNTHETIC BK Group (BOK)")
            assertFalse(nodes(instrumentation.uiAutomation.rootInActiveWindow).any {it.text?.toString() in listOf("Ticker","Broker","Notes","Expected price per share (RWF)","Request date · YYYY-MM-DD")})
            await("Market commission · 1.71%")
            await("RWF 732,312")
            val screenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"synthetic-share-order-screen.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
            screenshot.recycle()
            click("Save pending order")
            val deadline=System.currentTimeMillis()+5000
            while(writes.get()==0 && System.currentTimeMillis()<deadline)Thread.sleep(50)
            assertEquals(1,writes.get());assertEquals(0,equityWrites.get())
            assertEquals(732312.0,saved!!.getDouble("estimatedTotal"),0.0)
        } } finally { AppLanguage.select(priorLanguage) }
    }
}
