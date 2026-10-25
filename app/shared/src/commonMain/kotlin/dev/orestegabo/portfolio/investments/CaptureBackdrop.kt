package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

internal class CaptureBackdropRegistry {
    private val layers = linkedSetOf<Pair<GraphicsLayer, GraphicsLayer>>()
    var revision: Long = 1L
        private set
    var capturedRevision: Long = 0L
        private set
    fun invalidate() { revision++ }
    fun captured() { capturedRevision = revision }
    fun add(actual: GraphicsLayer, masked: GraphicsLayer) { layers += actual to masked; invalidate() }
    fun remove(actual: GraphicsLayer, masked: GraphicsLayer) {
        // A cached parent may retain the outgoing nodes until its next draw. Retire them masked.
        if (!actual.isReleased) actual.alpha = 0f
        if (!masked.isReleased) masked.alpha = 1f
        layers -= actual to masked
        invalidate()
    }
    fun <T> redacted(block: () -> T): T {
        val active = layers.filter { !it.first.isReleased && !it.second.isReleased }
        try {
            active.forEach { (actual, masked) -> actual.alpha = 0f; masked.alpha = 1f }
            return block()
        } finally {
            active.forEach { (actual, masked) -> actual.alpha = 1f; masked.alpha = 0f }
        }
    }
}
internal val LocalCaptureBackdropRegistry = staticCompositionLocalOf<CaptureBackdropRegistry?> { null }

/** Passive mask draws only during capture rendering, never receives input or accessibility focus. */
@Composable
internal fun CaptureRedactionRegion(
    modifier: Modifier = Modifier,
    masked: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val registry = LocalCaptureBackdropRegistry.current
    if (registry == null) { Box(modifier, propagateMinConstraints = true, content = content); return }
    SideEffect { registry.invalidate() }
    val actualLayer = rememberGraphicsLayer()
    val maskedLayer = rememberGraphicsLayer()
    remember(maskedLayer) { maskedLayer.alpha = 0f }
    DisposableEffect(registry, actualLayer, maskedLayer) {
        registry.add(actualLayer, maskedLayer)
        onDispose { registry.remove(actualLayer, maskedLayer) }
    }
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.drawWithContent {
            actualLayer.record { this@drawWithContent.drawContent() }
            drawLayer(actualLayer)
        }, propagateMinConstraints = true, content = content)
        Box(Modifier.matchParentSize().clearAndSetSemantics {}.drawWithContent {
            maskedLayer.record { this@drawWithContent.drawContent() }
            drawLayer(maskedLayer)
        }, propagateMinConstraints = true, content = masked)
    }
}

@Composable internal expect fun CaptureBackdropHost(enabled: Boolean, content: @Composable () -> Unit)
