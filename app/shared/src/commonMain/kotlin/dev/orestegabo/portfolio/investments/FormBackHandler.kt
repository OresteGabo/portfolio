package dev.orestegabo.portfolio.investments

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*

/** Lets the existing app-bar Back action use the visible form's exit policy, like system Back. */
internal class FormBackDispatcher {
    private var owner: Any? = null
    private var handler: (() -> Unit)? = null

    fun install(owner: Any, handler: () -> Unit) { this.owner = owner; this.handler = handler }
    fun remove(owner: Any) { if (this.owner === owner) { this.owner = null; handler = null } }
    fun dispatch(fallback: () -> Unit) { (handler ?: fallback)() }
}

internal val LocalFormBackDispatcher = staticCompositionLocalOf<FormBackDispatcher?> { null }

/** Save-completion callbacks deliberately keep the original back action; only user exits are guarded. */
@Composable
internal fun rememberFormBackHandler(dirty: Boolean, busy: Boolean, back: () -> Unit): () -> Unit {
    val currentDirty by rememberUpdatedState(dirty)
    val currentBusy by rememberUpdatedState(busy)
    val currentBack by rememberUpdatedState(back)
    var confirmDiscard by remember { mutableStateOf(false) }
    val request = remember {
        {
            if (!currentBusy) {
                if (currentDirty) confirmDiscard = true else currentBack()
            }
        }
    }
    val dispatcher = LocalFormBackDispatcher.current
    val owner = remember { Any() }
    DisposableEffect(dispatcher, owner) {
        dispatcher?.install(owner, request)
        onDispose { dispatcher?.remove(owner) }
    }
    PlatformBackHandler(true, request)
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { AppText("Discard changes?") },
        text = { AppText("Your unsaved changes will be lost. Saved investments will not change.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { if (!currentBusy) { confirmDiscard = false; currentBack() } }) { AppText("Discard changes") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { AppText("Keep editing") } })
    return request
}
