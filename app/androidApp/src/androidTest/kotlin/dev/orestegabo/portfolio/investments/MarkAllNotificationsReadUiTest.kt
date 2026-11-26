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
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

class MarkAllNotificationsReadUiTest {
    @Test fun bulkReadWaitsForServerRecoversFromFailureAndKeepsDatedCards() {
        val ui=UsabilityTestDriver()
        UsabilityPreferences().use {
            for(dark in listOf(false,true)) {
                val language=if(dark)"fr" else "en"
                val rows=(1..3).map { JsonRecord().put("id","synthetic-$it").put("kind","security")
                    .put("date",Today.toString()).put("read",false) }
                UiQualityPortfolioFixture(notificationRecords=rows).use { fixture ->
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        scene.onActivity { host -> host.viewModelStore.put("bulk-read",fixture.model);fixture.start() }
                        fixture.ready(ui) { block -> scene.onActivity { block() } }
                        scene.onActivity { host ->
                            AppLanguage.select(language)
                            host.setContent {
                                val density=LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density,if(dark)1.8f else 1f)) {
                                    MaterialTheme(colorScheme=structCapitalColorScheme(dark)) { Surface(Modifier.fillMaxSize()) {
                                        NotificationsScreen(fixture.model,{}, {})
                                    } }
                                }
                            }
                        }
                        val action=localized("Mark all as read")
                        val error=localized("Could not mark all notifications as read. Try again.")
                        ui.await(action)
                        scene.onActivity { fixture.bulkReadFailure=true }
                        ui.click(action);ui.await(error)
                        assertEquals(3,fixture.model.unreadNotifications)
                        val gate=CompletableDeferred<Unit>()
                        scene.onActivity { fixture.bulkReadFailure=false;fixture.bulkReadGate=gate }
                        ui.click(action);ui.await(localized("Marking as read…"))
                        assertTrue(fixture.model.markingAllNotificationsRead)
                        assertEquals(3,fixture.model.unreadNotifications)
                        assertTrue(fixture.model.notifications.none { it.optBoolean("read") })
                        scene.onActivity { gate.complete(Unit) }
                        ui.waitUntil("all read after server acknowledgement") { !fixture.model.markingAllNotificationsRead && fixture.model.unreadNotifications==0 }
                        assertTrue(fixture.model.notifications.all { it.optBoolean("read") })
                        ui.await(localized("Today"))
                        assertEquals(3,fixture.model.notifications.size)
                        assertEquals(2,fixture.bulkReadCalls.get())
                        assertFalse(ui.nodes().any { ui.matches(it,action) || ui.matches(it,error) })
                    }
                }
            }
        }
    }
}
