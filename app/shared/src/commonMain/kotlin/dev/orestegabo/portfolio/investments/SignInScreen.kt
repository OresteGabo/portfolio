package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.kigali_assets_36653828

@Composable
internal fun SignInScreen(
    model: PortfolioModel,
    continueAsGuest: (() -> Unit)? = null,
    openIssueReport: (() -> Unit)? = null,
    openLegal: () -> Unit
) {
    val actions = LocalNativeActions.current
    val autofill = LocalAutofillManager.current
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
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

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter
    ) {
        // 1. Immersive Full-Height Atmospheric Kigali Photo Background Layer
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(Res.drawable.kigali_assets_36653828),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Soft atmospheric gradient fading downward behind the lower form stack
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 1800f
                    )
                )
            )
        }

        // 2. Core Layout: Everything stays in a single unified LazyColumn with imePadding
        LazyColumn(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .imePadding(), // Resizes layout cleanly when soft keyboard appears without overlapping elements
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Keep the brand on the left; wrap language choices on narrow screens or large text.
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            bottomStart = 8.dp,
                            topEnd = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StructMark(Modifier.size(32.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                AppText(
                                    "STRUCT CAPITAL",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                AppText(
                                    if (register) "Create account" else "Welcome back",
                                    Modifier.semantics { heading() },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    LanguageFlagSwitch(compact = true)
                }
            }

            // Room for Kigali photography
            item {
                Spacer(Modifier.height(100.dp))
            }

            // Main Auth Panel Card
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CredentialField(
                            label = "Email",
                            value = email,
                            change = { email = it },
                            icon = Icons.Outlined.AlternateEmail,
                            input = CredentialInput.USERNAME,
                            content = (if (register) ContentType.NewUsername else ContentType.Username) + ContentType.EmailAddress,
                            enabled = !model.busy,
                            imeAction = ImeAction.Next,
                            onIme = { passwordFocus.requestFocus() }
                        )

                        CredentialField(
                            label = "Password",
                            value = password,
                            change = { password = it.take(128) },
                            icon = Icons.Outlined.Lock,
                            input = if (register) CredentialInput.NEW_PASSWORD else CredentialInput.PASSWORD,
                            content = if (register) ContentType.NewPassword else ContentType.Password,
                            enabled = !model.busy,
                            focusRequester = passwordFocus,
                            helper = if (register && password.length < 15) "At least 15 characters required" else null,
                            imeAction = if (register) ImeAction.Next else ImeAction.Go,
                            onIme = { if (register) confirmationFocus.requestFocus() else submit() }
                        )

                        if (register) {
                            CredentialField(
                                label = "Confirm password",
                                value = confirmation,
                                change = { confirmation = it.take(128) },
                                icon = Icons.Outlined.Lock,
                                input = CredentialInput.NEW_PASSWORD,
                                content = ContentType.NewPassword,
                                enabled = !model.busy,
                                focusRequester = confirmationFocus,
                                imeAction = ImeAction.Next,
                                onIme = { invitationFocus.requestFocus() }
                            )
                            CredentialField(
                                label = "Invitation code",
                                value = invitation,
                                change = { invitation = it },
                                icon = Icons.Outlined.Key,
                                enabled = !model.busy,
                                focusRequester = invitationFocus,
                                imeAction = ImeAction.Done,
                                onIme = ::submit
                            )
                        }

                        val canRemember = model.secureSignInAvailable && !model.busy
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 42.dp)
                                .toggleable(
                                    value = savePassword,
                                    enabled = canRemember,
                                    role = Role.Checkbox,
                                    onValueChange = { savePassword = it }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                savePassword,
                                null,
                                modifier = Modifier.size(24.dp),
                                enabled = canRemember
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                AppText(
                                    "Save sign-in with ${localized(model.unlockLabel)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                AppText(
                                    if (model.secureSignInAvailable) "Securely saved on this device" else "Set up device authentication first",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = ::submit,
                            enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (model.busy) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LocalContentColor.current
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            AppText(if (model.busy) "Signing in…" else if (register) "Create account" else "Sign in")
                        }

                        TextButton(
                            onClick = {
                                autofill?.cancel()
                                focus.clearFocus()
                                register = !register
                            },
                            enabled = !model.busy,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            AppText(if (register) "Already have an account? Sign in" else "Have an invitation code? Create account")
                        }
                    }
                }
            }

            // Footer Links inside the scrollable column to prevent overlap collisions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (model.guestPath != null) {
                        OutlinedButton(
                            onClick = model::returnToGuest,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
                        ) {
                            AppText("Return to Guest")
                        }
                    } else {
                        continueAsGuest?.let {
                            OutlinedButton(
                                onClick = it,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
                            ) {
                                AppText("Continue as Guest")
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (openIssueReport != null) {
                            TextButton(onClick = openIssueReport) { AppText("Report a problem") }
                        }
                        TextButton(onClick = openLegal) { AppText("Legal & privacy") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
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
            unfocusedBorderColor = colours.onSurface.copy(alpha = .18f),
            focusedBorderColor = colours.primary
        ),
    )
}
