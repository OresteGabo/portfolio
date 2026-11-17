package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Surface(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FinancialText(text, Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = dismiss) { AppText("Dismiss") }
        }
    }
}

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        AppText(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AppText(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FinancialText(displayRecordValue(value).ifBlank { "—" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}


@Composable
fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, helper: String? = null, autofill: ContentType? = null, enabled: Boolean = true,
    numbersHidden: Boolean? = null, trailingIcon: (@Composable () -> Unit)? = null, onDone: (() -> Unit)? = null) {
    val hidden = numbersHidden ?: LocalNumbersHidden.current
    if (keyboard in listOf(KeyboardType.Number, KeyboardType.Decimal) && LocalCaptureBackdropRegistry.current != null) {
        CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = {
            // A disabled TextField still intercepts taps. Capture masks must contain no input nodes.
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                    AppText(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppText("••••", style = MaterialTheme.typography.bodyLarge)
                }
                helper?.let { AppText(it, Modifier.padding(start = 16.dp, top = 4.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }) { FieldContent(label, value, change, keyboard, helper, autofill, enabled, hidden, trailingIcon, onDone) }
    } else FieldContent(label, value, change, keyboard, helper, autofill, enabled, hidden, trailingIcon, onDone)
}

@Composable
private fun FieldContent(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType, helper: String?, autofill: ContentType?, enabled: Boolean,
    numbersHidden: Boolean, trailingIcon: (@Composable () -> Unit)?, onDone: (() -> Unit)?) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, change, Modifier.fillMaxWidth().then(if (autofill != null) Modifier.semantics { contentType = autofill } else Modifier), label = { AppText(label) }, singleLine = true, enabled = enabled,
        visualTransformation = if (numbersHidden && keyboard in listOf(KeyboardType.Number, KeyboardType.Decimal)) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = trailingIcon,
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onDone?.invoke() }, onNext = { focus.moveFocus(FocusDirection.Next) }),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (autofill == ContentType.Username) ImeAction.Next else ImeAction.Done, platformImeOptions = if (autofill == ContentType.Username) credentialImeOptions(CredentialInput.USERNAME) else null), supportingText = helper?.let { { AppText(it) } })
}

@Composable
fun <T : Enum<T>> ChoiceRow(
    choices: List<T>,
    selected: T,
    choose: (T) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { item ->
            FilterChip(
                selected = item == selected,
                onClick = { choose(item) },
                label = { AppText(item.toString()) }
            )
        }
    }
}

@Composable
fun ChoiceRow(
    choices: List<String>,
    selected: String,
    choose: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { item ->
            FilterChip(
                selected = item == selected,
                onClick = { choose(item) },
                label = { AppText(item.replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}
