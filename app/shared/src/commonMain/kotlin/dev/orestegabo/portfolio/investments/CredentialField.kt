package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun CredentialField(
    label: String,
    value: String,
    change: (String) -> Unit,
    icon: ImageVector,
    input: CredentialInput? = null,
    content: ContentType? = null,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    helper: String? = null,
    imeAction: ImeAction,
    onIme: () -> Unit,
) {
    var revealed by remember(input) { mutableStateOf(false) }
    val password = input == CredentialInput.PASSWORD || input == CredentialInput.NEW_PASSWORD
    val colours = MaterialTheme.colorScheme
    val fill = colours.onSurface.copy(alpha = .025f).compositeOver(colours.surface)

    OutlinedTextField(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (content != null) Modifier.semantics { contentType = content } else Modifier),
        label = { AppText(label) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = { AppIcon(icon, null, Modifier.size(21.dp), tint = colours.onSurfaceVariant) },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                    AppIcon(
                        if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (revealed) "Hide password" else "Show password",
                        Modifier.size(21.dp)
                    )
                }
            }
        } else null,
        supportingText = helper?.let { { AppText(it) } },
        visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else if (input == CredentialInput.USERNAME) KeyboardType.Email else KeyboardType.Ascii,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = imeAction,
            platformImeOptions = input?.let(::credentialImeOptions)
        ),
        keyboardActions = KeyboardActions(onNext = { onIme() }, onGo = { onIme() }, onDone = { onIme() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            unfocusedBorderColor = colours.outline,
            focusedBorderColor = colours.primary
        ),
    )
}
