package dev.orestegabo.portfolio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orestegabo.portfolio.investments.*
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.gabo_mark

@Composable
fun App() {
    val settings = rememberServerSettings()
    remember(settings) { AppLanguage.initialize(settings) }
    val systemLanguage = platformSystemLanguage()
    SideEffect { AppLanguage.systemLanguage = systemLanguage }
    val actions = rememberNativeActions()
    val vault = rememberSecureSignIn()
    val model = viewModel { PortfolioModel(settings, vault = vault) }
    NativePrivacyCover(protectCaptures = model.captureProtectionEnabled)
    var privacyCover by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { privacyCover = false; model.foreground() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { privacyCover = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.background(systemInteraction = actions.interactingWithSystem) }
    CompositionLocalProvider(LocalNativeActions provides actions) {
        CaptureBackdropHost(enabled = model.captureProtectionEnabled) {
            Box(Modifier.fillMaxSize()) {
                Box(if (privacyCover) Modifier.semantics { hideFromAccessibility() } else Modifier) { InvestmentApp(model) }
                if (privacyCover) Surface(Modifier.fillMaxSize(), color = Color(0xFFFAF8F2)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Image(painterResource(Res.drawable.gabo_mark), "Gabo", Modifier.size(64.dp)) }
                }
            }
        }
    }
}
