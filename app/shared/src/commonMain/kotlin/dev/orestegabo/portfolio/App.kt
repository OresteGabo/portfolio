package dev.orestegabo.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orestegabo.portfolio.investments.*

@Composable
fun App() {
    remember { if (allowsDevelopmentHttp) UnlockPerformance.enabled = true; true }
    val settings = rememberServerSettings()
    remember(settings) { AppLanguage.initialize(settings) }
    val systemLanguage = platformSystemLanguage()
    SideEffect { AppLanguage.systemLanguage = systemLanguage }
    val reducedMotion = rememberReducedMotion()
    val selectionFeedback = rememberSelectionFeedback()
    val actions = rememberNativeActions()
    val vault = rememberSecureSignIn()
    val model = viewModel {
        val timing = UnlockPerformance.begin("viewmodel_initialization")
        PortfolioModel(settings, vault = vault, initialOrigin = developmentApiOrigin()).also { timing?.end() }
    }
    val haptics = LocalHapticFeedback.current
    NativePrivacyCover(protectCaptures = model.captureProtectionEnabled, protectDocument = model.documentPreview != null)
    var privacyCover by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { privacyCover = false; model.foreground() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { privacyCover = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.background(systemInteraction = actions.interactingWithSystem) }
    ShakeNumberVisibility(
        enabled = model.shakeToHideEnabled && model.user != null && !model.locked &&
            !privacyCover && !actions.interactingWithSystem && !model.numbersRevealBlocked,
        onShake = {
            val previouslyHidden = model.numbersHidden
            model.toggleNumberVisibility()
            if (model.numbersHidden != previouslyHidden) {
                haptics.performHapticFeedback(
                    if (model.numbersHidden) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
            }
        },
    )
    CompositionLocalProvider(LocalNativeActions provides actions, LocalReducedMotion provides reducedMotion, LocalSelectionFeedback provides selectionFeedback) {
        CaptureBackdropHost(enabled = model.captureProtectionEnabled) {
            Box(Modifier.fillMaxSize()) {
                Box(if (privacyCover) Modifier.semantics { hideFromAccessibility() } else Modifier) { InvestmentApp(model) }
                if (privacyCover) Surface(Modifier.fillMaxSize(), color = Color(0xFFFAF8F2)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { StructMark(Modifier.size(64.dp), onDark = false) }
                }
            }
        }
    }
}
