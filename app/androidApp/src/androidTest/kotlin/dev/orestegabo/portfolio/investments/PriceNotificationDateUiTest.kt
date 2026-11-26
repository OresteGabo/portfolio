package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class PriceNotificationDateUiTest {
    @Test fun historicalCloseAndComparisonDatesStayVisibleAcrossThemesLanguagesAndNumberPrivacy() {
        val ui=UsabilityTestDriver()
        UsabilityPreferences().use {
            for(language in listOf("en","fr")) for(dark in listOf(false,true)) {
                val hidden=mutableStateOf(false)
                val reads=AtomicInteger()
                val event=JsonRecord().put("id","synthetic-close").put("ticker","BOK").put("kind","prices")
                    .put("date","2026-11-25").put("previousDate","2026-11-24").put("previousPrice",721)
                    .put("closingPrice",720).put("impact",-1000).put("percent",-0.14).put("totalGain",9000)
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    scene.onActivity { host ->
                        AppLanguage.initialize(object : ServerSettings {
                            override fun readOrigin(): String?=null
                            override fun writeOrigin(origin: String) {}
                            override fun readLanguage()=language
                        })
                        host.setContent {
                            val density=LocalDensity.current
                            CompositionLocalProvider(LocalNumbersHidden provides hidden.value,
                                LocalDensity provides Density(density.density,if(dark)1.8f else 1f)) {
                                MaterialTheme(colorScheme=structCapitalColorScheme(dark)) {
                                    Surface(Modifier.fillMaxSize()) {
                                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                                            PriceNotificationCard(event) { reads.incrementAndGet() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val date=if(language=="en") "Market close · 25 Nov 2026" else "Clôture du marché · 25 nov 2026"
                    val comparison=if(language=="en") "Compared with 24 Nov 2026" else "Par rapport au 24 nov 2026"
                    val explanation=localized("Historical close; the latest market price may differ.")
                    ui.await(date);ui.await(comparison);ui.await("RWF 721 → RWF 720")
                    assertFalse(ui.nodes().any { ui.matches(it,explanation) })
                    val labelBounds=Rect().also(ui.await(localized("Holding value change"))::getBoundsInScreen)
                    val amountBounds=Rect().also(ui.await(money(-1000.0))::getBoundsInScreen)
                    assertTrue("Holding change label must not overlap the amount",labelBounds.right<=amountBounds.left)

                    val bitmap=requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                    try { bitmap.writeGolden(File(ui.instrumentation.targetContext.cacheDir,"price-date-$language-$dark.png")) }
                    finally { bitmap.recycle() }
                    ui.click(date);ui.scroll(explanation);assertEquals(1,reads.get())
                    ui.scroll(date);ui.click(date);assertEquals(1,reads.get())
                    scene.onActivity { hidden.value=true }
                    ui.await(localized("Financial numbers hidden"));ui.await(date);ui.await(comparison)
                    assertFalse(ui.nodes().any { it.isVisibleToUser && it.text?.toString()?.contains("721") == true })
                    assertEquals(720.0,event.optDouble("closingPrice"),0.0)
                    assertEquals("2026-11-25",event.text("date"))
                }
            }
        }
    }
}
