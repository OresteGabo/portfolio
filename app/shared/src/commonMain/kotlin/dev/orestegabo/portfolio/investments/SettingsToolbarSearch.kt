package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Composed only after the toolbar action is selected; query changes never steal focus again. */
@Composable
internal fun SettingsToolbarSearch(query: String, onQueryChange: (String) -> Unit) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val label = localized("Search settings")
    BasicTextField(query, onQueryChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(requester)
            .semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
        decorationBox = { input ->
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) AppText("Search settings", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    input()
                }
                if (query.isNotEmpty()) IconButton(onClick = {
                    onQueryChange("")
                    requester.requestFocus()
                    keyboard?.show()
                }) { AppIcon(Icons.Outlined.Close, "Clear search") }
            }
        })
    LaunchedEffect(Unit) {
        requester.requestFocus()
        // Let the focused platform text-input session attach before asking it to display the IME.
        withFrameNanos { }
        keyboard?.show()
    }
    DisposableEffect(focus, keyboard) {
        onDispose { focus.clearFocus(); keyboard?.hide() }
    }
}
