package dev.orestegabo.portfolio

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController() = ComposeUIViewController(configure = {
    // Redaction swaps layers only while making the capture image. Serialize it with live rendering.
    parallelRendering = false
}) { App() }
