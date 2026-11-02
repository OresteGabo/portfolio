package dev.orestegabo.portfolio.investments

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=26)
class LocalEngineResilienceTest {
    @Test fun aLostRendererFailsSafelyAndAnExplicitRetryRecreatesThePrivateSandbox(): Unit=runBlocking {
        ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
            lateinit var engine: LocalInvestmentEngine
            lateinit var previous: WebView
            scene.onActivity { host ->
                engine=LocalInvestmentEngine(host,privateDocuments=true)
                assertFalse(engine.hostRequested)
                host.setContent { InvestmentEngineHost(engine) }
            }
            val input=JsonRecord().put("assumptions",JsonRecord().put("horizonYears",1)
                .put("auctionFillRate",0).put("contributionPeriods",JsonList()))
            try {
                assertEquals(12,withTimeout(25000) { engine.run("projection",input) }.getJSONArray("rows").length())
                scene.onActivity {
                    previous=engine.view
                    assertTrue(previous.settings.blockNetworkLoads)
                    assertTrue(previous.webViewClient.onRenderProcessGone(previous,object: RenderProcessGoneDetail() {
                        override fun didCrash()=true
                        override fun rendererPriorityAtExit()=WebView.RENDERER_PRIORITY_BOUND
                    }))
                    assertFalse(engine.hostRequested)
                }
                // Use the real Compose host: its renderer-generation key must attach the replacement.
                assertEquals(12,withTimeout(25000) { engine.run("projection",input) }.getJSONArray("rows").length())
                scene.onActivity {
                    assertNotSame(previous,engine.view)
                    assertTrue(engine.view.settings.blockNetworkLoads)
                    assertFalse(engine.view.settings.allowFileAccess)
                }
            } finally { scene.onActivity { engine.destroy() } }
        }
    }
}
