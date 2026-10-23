package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(model: PortfolioModel, theme: String, changeTheme: (String) -> Unit,
    openSimulator: () -> Unit, openEducation: () -> Unit, openBond: (JsonRecord) -> Unit) {
    val actions = LocalNativeActions.current
    var setupSecure by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var ownerSection by rememberSaveable { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<JsonRecord?>(null) }
    var registration by remember { mutableStateOf<JsonRecord?>(null) }
    var bondSearch by rememberSaveable { mutableStateOf("") }
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    val email = model.user?.text("email").orEmpty()
    val owner = model.user?.optBoolean("isOwner") == true
    fun loadOwner(section: String) {
        ownerSection = section
        model.action {
            if (section == "accounts") accounts = model.api.request("/api/bonds/auth/accounts")
            else registration = model.api.request("/api/bonds/auth/registration")
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Outlined.Person, null, Modifier.padding(14.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (owner) "Owner account" else "Personal account", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SettingsSection("Appearance") {
            SettingsRow(Icons.Outlined.Palette, "Theme", when (theme) { "dark" -> "Dark"; "light" -> "Light"; else -> "Follow device" },
                onClick = { dialog = "theme" })
        } }
        item { SettingsSection("Security") {
            SettingsRow(when (model.unlockLabel) { "Face ID" -> Icons.Outlined.Face; "Touch ID" -> Icons.Outlined.Fingerprint; else -> Icons.Outlined.Lock },
                "Secure sign-in", if (model.secureSignInEnabled) "Unlock with ${model.unlockLabel}"
                else if (model.secureSignInAvailable) "Remember sign-in on this device" else "Set up a device screen lock first",
                enabled = !model.busy && (model.secureSignInAvailable || model.secureSignInEnabled), toggleState = model.secureSignInEnabled,
                onClick = { if (model.secureSignInEnabled) model.disableSecureSignIn() else setupSecure = true },
                trailing = {
                    Switch(checked = model.secureSignInEnabled, onCheckedChange = null, enabled = !model.busy && (model.secureSignInAvailable || model.secureSignInEnabled),
                        modifier = Modifier.clearAndSetSemantics {})
                })
            if (model.secureSignInEnabled) {
                SettingsDivider()
                SettingsRow(Icons.Outlined.LockClock, "Lock app now", "Require device authentication to reopen", enabled = !model.busy,
                    onClick = model::lock)
            }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Password, "Passwords & AutoFill", "Use your phone’s password manager", onClick = { dialog = "passwords" })
        } }
        item { SettingsSection("Privacy") {
            SettingsRow(Icons.Outlined.PrivacyTip, "PDF imports & your data", "How your documents are handled", onClick = { dialog = "privacy" })
        } }
        item { SettingsSection("Tools") {
            SettingsRow(Icons.Outlined.Calculate, "Investment simulator", onClick = openSimulator)
            SettingsDivider()
            SettingsRow(Icons.Outlined.MenuBook, "Bond education", onClick = openEducation)
            SettingsDivider()
            SettingsRow(Icons.Outlined.Search, "Find bond purchases", onClick = { ownerSection = "bonds" })
        } }
        item { SettingsSection("Links", "These links open in your browser.") {
            val links = listOf(
                Triple("/portfolio", "Portfolio website", Icons.Outlined.AccountBalanceWallet),
                Triple("/create-password", "Password setup", Icons.Outlined.Key),
            )
            links.forEachIndexed { index, (path, title, icon) ->
                if (index > 0) SettingsDivider()
                SettingsRow(icon, title, onClick = { actions.openUrl(model.origin + path) },
                    trailing = { Icon(Icons.Outlined.OpenInNew, "Opens in browser", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
        } }
        if (owner) item { SettingsSection("Administration") {
            SettingsRow(Icons.Outlined.ManageAccounts, "Accounts", "View registered accounts", enabled = !model.busy, onClick = { loadOwner("accounts") })
            SettingsDivider()
            SettingsRow(Icons.Outlined.PersonAdd, "Invitations", "Create and manage invitations", enabled = !model.busy, onClick = { loadOwner("invitations") })
        } }
        item { SettingsSection("Session") {
            SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", enabled = !model.busy,
                tint = MaterialTheme.colorScheme.error, onClick = { dialog = "logout" }, trailing = {})
        } }
    }

    if (setupSecure) AlertDialog(
        onDismissRequest = { setupSecure = false; password = "" }, title = { Text("Enable secure sign-in") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Confirm your password to unlock with ${model.unlockLabel}. The app locks when you leave it and remembers your sign-in after a restart.")
            OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, platformImeOptions = credentialImeOptions(CredentialInput.PASSWORD)))
        } },
        confirmButton = { TextButton(enabled = password.isNotBlank() && !model.busy, onClick = {
            model.enableSecureSignIn(password); password = ""; setupSecure = false
        }) { Text("Enable") } }, dismissButton = { TextButton(onClick = { setupSecure = false; password = "" }) { Text("Cancel") } })

    if (dialog == "theme") AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Choose appearance") }, text = {
        Column {
            listOf("system" to "Follow device", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { changeTheme(value); dialog = null }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == value, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
    if (dialog == "privacy" || dialog == "passwords") AlertDialog(onDismissRequest = { dialog = null },
        title = { Text(if (dialog == "privacy") "PDF imports & your data" else "Passwords & AutoFill") }, text = {
            Text(if (dialog == "privacy") "PDF imports are read on your device. Review the filled details before saving a purchase to your account. Your portfolio is private to your signed-in account."
                else "Your phone’s password manager can fill your login. Enable Password AutoFill in your phone’s settings to use saved passwords.\n\nSecure sign-in is separate: it lets you reopen this app with device authentication without typing your password.")
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } })
    if (dialog == "logout") AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Sign out?") },
        text = { Text("Saved secure sign-in will be removed from this device. Your portfolio and password-manager entries remain available.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { dialog = null; model.logout() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })

    LaunchedEffect(ownerSection, model.historyComplete, model.busy) {
        if (ownerSection == "bonds" && !model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
    }
    if (ownerSection != null && (owner || ownerSection == "bonds")) ModalBottomSheet(onDismissRequest = { ownerSection = null }) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when (ownerSection) { "accounts" -> "Accounts"; "bonds" -> "Bond purchases"; else -> "Invitations" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(enabled = !model.busy, onClick = { if (ownerSection == "bonds") model.loadInvestmentHistory(force = true) else loadOwner(ownerSection!!) }) { Icon(Icons.Outlined.Refresh, "Refresh ${ownerSection}") }
                IconButton(onClick = { ownerSection = null }) { Icon(Icons.Outlined.Close, "Close") }
            } }
            if (model.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            model.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (ownerSection == "bonds") {
                if (model.purchases.isNotEmpty()) item { Field("Search bonds, ISIN, broker", bondSearch, { bondSearch = it }) }
                val bonds = (if (model.historyComplete) model.purchases else emptyList()).filter { purchase -> listOf("bondName", "isin", "broker").any { purchase.text(it).contains(bondSearch, ignoreCase = true) } }
                if (bonds.isEmpty() && !model.busy && model.historyComplete) item {
                    EmptyState(if (model.purchases.isEmpty()) "No bond purchases yet" else "No matches",
                        if (model.purchases.isEmpty()) EmptyArtwork.BONDS else EmptyArtwork.SEARCH, compact = true,
                        actionLabel = if (bondSearch.isNotBlank()) "Clear search" else null,
                        onAction = if (bondSearch.isNotBlank()) ({ bondSearch = "" }) else null)
                }
                bonds.forEach { purchase -> item {
                    ListItem(headlineContent = { Text(purchase.text("bondName"), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${purchase.text("isin")} · ${purchase.text("status")}") },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                        modifier = Modifier.clickable { ownerSection = null; openBond(purchase) })
                } }
            } else if (ownerSection == "accounts") {
                val rows = accounts?.rows("accounts")
                if (rows != null && rows.isEmpty() && !model.busy) item { EmptyState("No accounts yet", EmptyArtwork.ACCOUNTS, compact = true) }
                rows?.forEach { account -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(account.text("email"), fontWeight = FontWeight.SemiBold)
                        Fact("Account", "${account.text("kind")} · ${account.text("status")}")
                        account.optJSONObject("portfolio")?.let { Fact("Bond purchases", it.text("purchaseCount")) }
                        HorizontalDivider()
                    }
                } }
            } else registration?.let { settings ->
                item { Text(if (settings.optBoolean("available")) "Invitations are available" else "Invitations are unavailable", style = MaterialTheme.typography.bodyMedium) }
                settings.optJSONObject("invitation")?.let { invitation -> item { Panel {
                    Fact("Active invitation", invitation.text("email")); Fact("Expires", invitation.text("expiresAt"))
                } } }
                if (settings.optBoolean("available")) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field("Invitation email (optional)", inviteEmail, { inviteEmail = it }, enabled = !model.busy)
                        Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = { model.action {
                            val response = model.api.request("/api/bonds/auth/registration", "POST", JsonRecord().put("email", inviteEmail))
                            registration = model.api.request("/api/bonds/auth/registration")
                            actions.shareText("${model.origin}/join#invite=${response.text("invite")}")
                        } }) { Text("Create & share invitation") }
                        OutlinedButton(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = { model.action {
                            model.api.request("/api/bonds/auth/registration", "DELETE")
                            registration = model.api.request("/api/bonds/auth/registration")
                        } }) { Text("Close invitations") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, footnote: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
        footnote?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary, toggleState: Boolean? = null, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    ListItem(headlineContent = { Text(title, color = if (enabled) tint.takeIf { it == MaterialTheme.colorScheme.error } ?: MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant) }, supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = { Icon(icon, null, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = trailing ?: { Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().then(if (toggleState != null) Modifier.toggleable(toggleState, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)))
}

@Composable private fun SettingsDivider() { HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) }
