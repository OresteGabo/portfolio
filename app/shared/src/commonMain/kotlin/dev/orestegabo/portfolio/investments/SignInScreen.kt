package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.gabo_mark

@Composable
internal fun SignInScreen(model: PortfolioModel, openLegal: () -> Unit) {
    val actions = LocalNativeActions.current
    val autofill = LocalAutofillManager.current
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords and invitation secrets stay out of saved instance state.
    var password by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var savePassword by rememberSaveable { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    val invitationFocus = remember { FocusRequester() }
    val canSubmit = !model.busy && email.isNotBlank() && password.isNotEmpty()
    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        if (register && (password.length < 15 || password != confirmation || invitation.isBlank())) {
            model.error = "Enter an invitation and matching passwords of at least 15 characters."
            return
        }
        val submittedEmail = email.trim()
        val submittedPassword = password
        val submittedOrigin = model.origin
        model.login(submittedEmail, submittedPassword, if (register) invitation else "", savePassword = savePassword) {
            autofill?.commit()
            actions.offerPasswordSave(submittedOrigin, submittedEmail, submittedPassword)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 480.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(Res.drawable.gabo_mark), localized("Gabo logo"), Modifier.size(72.dp))
                    AppText(if (register) "Create account" else "Welcome back", Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Panel {
                    CredentialField("Email", email, { email = it }, Icons.Outlined.AlternateEmail,
                        input = CredentialInput.USERNAME,
                        content = (if (register) ContentType.NewUsername else ContentType.Username) + ContentType.EmailAddress,
                        enabled = !model.busy, imeAction = ImeAction.Next, onIme = { passwordFocus.requestFocus() })
                    CredentialField("Password", password, { password = it.take(128) }, Icons.Outlined.Lock,
                        input = if (register) CredentialInput.NEW_PASSWORD else CredentialInput.PASSWORD,
                        content = if (register) ContentType.NewPassword else ContentType.Password,
                        enabled = !model.busy, focusRequester = passwordFocus,
                        helper = if (register) "At least 15 characters" else null,
                        imeAction = if (register) ImeAction.Next else ImeAction.Go,
                        onIme = { if (register) confirmationFocus.requestFocus() else submit() })
                    if (register) {
                        CredentialField("Confirm password", confirmation, { confirmation = it.take(128) }, Icons.Outlined.Lock,
                            input = CredentialInput.NEW_PASSWORD, content = ContentType.NewPassword,
                            enabled = !model.busy, focusRequester = confirmationFocus,
                            imeAction = ImeAction.Next, onIme = { invitationFocus.requestFocus() })
                        CredentialField("Invitation code", invitation, { invitation = it }, Icons.Outlined.Key,
                            enabled = !model.busy, focusRequester = invitationFocus,
                            imeAction = ImeAction.Done, onIme = ::submit)
                    }
                    val canRemember = model.secureSignInAvailable && !model.busy
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(value = savePassword, enabled = canRemember, role = Role.Checkbox, onValueChange = { savePassword = it })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(savePassword, null, modifier = Modifier.size(32.dp), enabled = canRemember)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AppText("Save sign-in with ${localized(model.unlockLabel)}", style = MaterialTheme.typography.bodyMedium)
                            AppText(if (model.secureSignInAvailable) "Securely saved on this device" else "Set up device authentication first",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = ::submit, enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                        if (model.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                            Spacer(Modifier.width(10.dp))
                        }
                        AppText(if (model.busy) "Signing in…" else if (register) "Create account" else "Sign in")
                    }
                    TextButton(onClick = {
                        autofill?.cancel()
                        focus.clearFocus()
                        register = !register
                    }, enabled = !model.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        AppText(if (register) "Back to sign in" else "Use an invitation")
                    }
                }
            }
            item { TextButton(onClick = openLegal, modifier = Modifier.fillMaxWidth()) { AppText("Legal & privacy") } }
        }
    }
}

@Composable
private fun CredentialField(
    label: String, value: String, change: (String) -> Unit, icon: ImageVector,
    input: CredentialInput? = null, content: ContentType? = null, enabled: Boolean,
    focusRequester: FocusRequester? = null, helper: String? = null, imeAction: ImeAction, onIme: () -> Unit,
) {
    var revealed by remember(input) { mutableStateOf(false) }
    val password = input == CredentialInput.PASSWORD || input == CredentialInput.NEW_PASSWORD
    val colours = MaterialTheme.colorScheme
    val fill = colours.onSurface.copy(alpha = .025f).compositeOver(colours.surface)
    OutlinedTextField(value, change,
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (content != null) Modifier.semantics { contentType = content } else Modifier),
        label = { AppText(label) }, singleLine = true, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = { AppIcon(icon, null, Modifier.size(21.dp), tint = colours.onSurfaceVariant) },
        trailingIcon = if (password) { {
            IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                AppIcon(if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (revealed) "Hide password" else "Show password", Modifier.size(21.dp))
            }
        } } else null,
        supportingText = helper?.let { { AppText(it) } },
        visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else if (input == CredentialInput.USERNAME) KeyboardType.Email else KeyboardType.Ascii,
            autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = imeAction,
            platformImeOptions = input?.let(::credentialImeOptions)),
        keyboardActions = KeyboardActions(onNext = { onIme() }, onGo = { onIme() }, onDone = { onIme() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill,
            unfocusedBorderColor = colours.onSurface.copy(alpha = .18f),
            focusedBorderColor = colours.primary),
    )
}
