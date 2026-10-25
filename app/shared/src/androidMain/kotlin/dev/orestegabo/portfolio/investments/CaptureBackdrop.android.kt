package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable

// Android keeps native screenshot blocking until selective secure surfaces are verified.
@Composable internal actual fun CaptureBackdropHost(enabled: Boolean, content: @Composable () -> Unit) = content()
