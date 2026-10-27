package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: PortfolioModel, theme: String, changeTheme: (String) -> Unit,
    chartStyle: String, changeChartStyle: (String) -> Unit,
    openSimulator: () -> Unit, openEducation: () -> Unit, openBond: (JsonRecord) -> Unit, openLegal: () -> Unit = {}, openAbout: () -> Unit = {}, openIntroduction: () -> Unit = {}) {
    val actions = LocalNativeActions.current
    var setupSecure by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var deletionPassword by remember { mutableStateOf("") }
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

    // In-scene financial editors must overlay the list, not become another child of the app's Column.
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            AppIcon(Icons.Outlined.Person, null, Modifier.padding(14.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            AppText(if (owner) "Owner account" else "Personal account", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SettingsSection("Notifications") {
                SettingsRow(Icons.Outlined.Notifications, "Notification preferences", "Categories & coupon receipts", onClick={ dialog="notifications" })
                if(NativePushBridge.available) {
                    SettingsDivider()
                    SettingsRow(Icons.Outlined.PhoneIphone, "Phone notifications",if(NativePushBridge.allowed) "Enabled" else "Disabled",onClick=NativePushBridge::requestPermission)
                }
            } }
            item { SettingsSection("Privacy") {
                SettingsRow(Icons.Outlined.ScreenLockPortrait, "Protect screenshots & recordings",
                    captureProtectionDescription,
                    toggleState = model.captureProtectionEnabled,
                    onClick = { model.updateScreenCapturePermission(model.captureProtectionEnabled) },
                    trailing = { Switch(checked = model.captureProtectionEnabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {}) })
                SettingsDivider()
                SettingsRow(Icons.Outlined.VisibilityOff, "Hide numbers",
                    if (model.numbersRevealBlocked) "Temporarily required by capture protection" else "Conceal financial figures throughout the app",
                    enabled = !model.numbersRevealBlocked, toggleState = model.numbersConcealed, onClick = model::toggleNumberVisibility,
                    trailing = { Switch(checked = model.numbersConcealed, enabled = !model.numbersRevealBlocked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {}) })
                SettingsDivider()
                SettingsRow(Icons.Outlined.Vibration, "Shake to hide numbers", "Shake again to show them",
                    toggleState = model.shakeToHideEnabled, onClick = { model.updateShakeToHide(!model.shakeToHideEnabled) },
                    trailing = { Switch(checked = model.shakeToHideEnabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {}) })
                SettingsDivider()
                SettingsRow(Icons.Outlined.PrivacyTip, "PDF imports & your data", "How your documents are handled", onClick = { dialog = "privacy" })
                if (model.canDeleteAccount) {
                    SettingsDivider()
                    SettingsRow(Icons.Outlined.DeleteForever, "Delete account", "Permanently remove your account and data",
                        enabled = !model.busy, tint = MaterialTheme.colorScheme.error,
                        onClick = { deletionPassword = ""; dialog = "deleteAccount" })
                }
            } }
            item { SettingsSection("Appearance") {
                LanguageOptions()
                SettingsDivider()
                AppearanceOptions(theme, changeTheme)
                SettingsDivider()
                SettingsRow(Icons.AutoMirrored.Outlined.ShowChart, "Chart style", chartStyle.replaceFirstChar { it.uppercase() },
                    onClick = { dialog = "chartStyle" })
            } }
            item { SettingsSection("Investing") {
                Column(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()) {
                    SettingsRow(Icons.Outlined.Flag, "Monthly investment target", privateFinancialValue(money(model.monthlyInvestmentTarget.toDouble(), "RWF")),
                        onClick = { targetExpanded = !targetExpanded },
                        modifier = Modifier.semantics { stateDescription = localized(if (targetExpanded) "Expanded" else "Collapsed") },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(enabled = !model.busy, onClick = { dialog = "investmentTarget" }) {
                                    AppIcon(Icons.Outlined.Edit, "Edit monthly target", Modifier.size(20.dp))
                                }
                                AppIcon(if (targetExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        })
                    if (targetExpanded) {
                        SettingsDivider()
                        InvestmentGoalBreakdown(model)
                    }
                }
            } }
            item { SettingsSection("Security") {
                SettingsRow(when (model.unlockLabel) { "Face ID" -> Icons.Outlined.Face; "Touch ID" -> Icons.Outlined.Fingerprint; else -> Icons.Outlined.Lock },
                    "Secure sign-in", if (model.secureSignInEnabled) "Unlock with ${localized(model.unlockLabel)}"
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
            item { SettingsSection("Tools") {
                SettingsRow(Icons.Outlined.PictureAsPdf, "Portfolio report", "All years · PDF export", enabled = !model.busy,
                    onClick = { dialog = "report" })
                SettingsDivider()
                SettingsRow(Icons.Outlined.Calculate, "Investment simulator", onClick = openSimulator)
                SettingsDivider()
                SettingsRow(Icons.Outlined.MenuBook, "Bond education", onClick = openEducation)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Search, "Find bond purchases", onClick = { ownerSection = "bonds" })
            } }
            item { SettingsSection("About") {
                SettingsRow(Icons.Outlined.Info, "About Struct Capital", "The app, the brand and how to reach us", onClick = openAbout)
                SettingsDivider()
                SettingsRow(Icons.Outlined.AutoStories, "Replay introduction", "A quick tour of Struct Capital", onClick = openIntroduction)
            } }
            item { SettingsSection("Legal & privacy") {
                SettingsRow(Icons.Outlined.Email, "Contact support", BrandContacts.SUPPORT, onClick = { actions.openEmail(BrandContacts.SUPPORT) })
                SettingsDivider()
                SettingsRow(Icons.Outlined.Policy, "Legal documents", "Terms, privacy, data rights & credits", onClick = openLegal)
            } }
            item { SettingsSection("Links", "These links open in your browser.") {
                val links = listOf(
                    Triple(OFFICIAL_WEBSITE_ORIGIN, "Struct Capital", Icons.Outlined.Language),
                    Triple(model.origin + "/portfolio", "Portfolio website", Icons.Outlined.AccountBalanceWallet),
                    Triple(model.origin + "/create-password", "Password setup", Icons.Outlined.Key),
                )
                links.forEachIndexed { index, (url, title, icon) ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(icon, title, onClick = { actions.openUrl(url) },
                        trailing = { AppIcon(Icons.Outlined.OpenInNew, "Opens in browser", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) })
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

        if (dialog == "investmentTarget") InvestmentTargetDialog(model) { dialog = null }

        if (setupSecure) AlertDialog(
            onDismissRequest = { setupSecure = false; password = "" }, title = { AppText("Enable secure sign-in") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("Confirm your password to unlock with ${localized(model.unlockLabel)}. The app locks when you leave it and remembers your sign-in after a restart.")
                OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    singleLine = true, label = { AppText("Password") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, platformImeOptions = credentialImeOptions(CredentialInput.PASSWORD)))
            } },
            confirmButton = { TextButton(enabled = password.isNotBlank() && !model.busy, onClick = {
                model.enableSecureSignIn(password); password = ""; setupSecure = false
            }) { AppText("Enable") } }, dismissButton = { TextButton(onClick = { setupSecure = false; password = "" }) { AppText("Cancel") } })

        if (dialog == "chartStyle") AlertDialog(onDismissRequest = { dialog = null }, title = { AppText("Chart style") }, text = {
            Column {
                listOf("area" to Icons.Outlined.AreaChart, "line" to Icons.AutoMirrored.Outlined.ShowChart, "bars" to Icons.Outlined.BarChart).forEach { (value, icon) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = chartStyle == value, role = Role.RadioButton,
                        onClick = { changeChartStyle(value); dialog = null }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(icon, null)
                        AppText(value.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                        RadioButton(selected = chartStyle == value, onClick = null)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { dialog = null }) { AppText("Cancel") } })
        if (dialog == "privacy" || dialog == "passwords") AlertDialog(onDismissRequest = { dialog = null },
            title = { AppText(if (dialog == "privacy") "PDF imports & your data" else "Passwords & AutoFill") }, text = {
                AppText(if (dialog == "privacy") "PDFs are read on your device. Saving a purchase uploads its PDF to your private Documents. Review the figures before confirming."
                    else "Your phone’s password manager can fill your login. Enable Password AutoFill in your phone’s settings to use saved passwords.\n\nSecure sign-in is separate: it lets you reopen this app with device authentication without typing your password.")
            }, confirmButton = { TextButton(onClick = { dialog = null }) { AppText("Done") } })
        if(dialog=="notifications") NotificationPreferencesDialog(model) { dialog=null }
        if (dialog == "report") AlertDialog(onDismissRequest = { dialog = null },
            title = { AppText("Portfolio report") },
            text = { AppText("Export all bonds, shares, estimated gains and losses, and target progress. The PDF includes your financial figures even when Hide numbers is on. Original PDFs must be downloaded separately.") },
            confirmButton = { TextButton(enabled = !model.busy, onClick = {
                dialog = null; model.exportPortfolioReport(actions)
            }) { AppText("Generate PDF") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { AppText("Cancel") } })
        model.reportProgress?.let { progress ->
            AlertDialog(onDismissRequest = {}, title = { AppText("Portfolio report") }, text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    AppText(progress, Modifier.weight(1f))
                }
            }, confirmButton = {})
        }
        if (dialog == "deleteAccount" && model.reportProgress == null) AlertDialog(
            onDismissRequest = { dialog = null; deletionPassword = "" },
            title = { AppText("Delete account?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("Permanently delete $email, its investments, documents and active sessions. This cannot be undone. Files you downloaded and entries in your phone’s password manager remain on your device.")
                AppText("Save a report before deleting. It includes financial figures; download original PDFs separately from Documents.")
                OutlinedButton(enabled = !model.busy, onClick = {
                    deletionPassword = ""; model.exportPortfolioReport(actions)
                }) { AppIcon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); AppText("Save portfolio report") }
                OutlinedTextField(deletionPassword, { deletionPassword = it.take(128) }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { AppText("Confirm your password") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                        platformImeOptions = credentialImeOptions(CredentialInput.PASSWORD)))
            } },
            confirmButton = { TextButton(enabled = deletionPassword.isNotBlank() && !model.busy, onClick = {
                model.deleteAccount(deletionPassword); deletionPassword = ""; dialog = null
            }) { AppText("Delete account", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null; deletionPassword = "" }) { AppText("Cancel") } })
        if (dialog == "logout") AlertDialog(onDismissRequest = { dialog = null }, title = { AppText("Sign out?") },
            text = { AppText("Saved secure sign-in will be removed from this device. Your portfolio and password-manager entries remain available.") },
            confirmButton = { TextButton(enabled = !model.busy, onClick = { dialog = null; model.logout() }) { AppText("Sign out", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { AppText("Cancel") } })

        LaunchedEffect(ownerSection, model.historyComplete, model.busy) {
            if (ownerSection == "bonds" && !model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
        }
        if (ownerSection != null && (owner || ownerSection == "bonds")) ModalBottomSheet(onDismissRequest = { ownerSection = null }) {
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(when (ownerSection) { "accounts" -> "Accounts"; "bonds" -> "Bond purchases"; else -> "Invitations" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(enabled = !model.busy, onClick = { if (ownerSection == "bonds") model.loadInvestmentHistory(force = true) else loadOwner(ownerSection!!) }) { AppIcon(Icons.Outlined.Refresh, "Refresh ${ownerSection}") }
                    IconButton(onClick = { ownerSection = null }) { AppIcon(Icons.Outlined.Close, "Close") }
                } }
                if (model.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                model.error?.let { error -> item { AppText(error, color = MaterialTheme.colorScheme.error) } }
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
                        ListItem(headlineContent = { AppText(purchase.text("bondName"), fontWeight = FontWeight.SemiBold) },
                            supportingContent = { AppText("${purchase.text("isin")} · ${purchase.text("status")}") },
                            trailingContent = { AppIcon(Icons.Outlined.ChevronRight, null) },
                            modifier = Modifier.clickable { ownerSection = null; openBond(purchase) })
                    } }
                } else if (ownerSection == "accounts") {
                    val rows = accounts?.rows("accounts")
                    if (rows != null && rows.isEmpty() && !model.busy) item { EmptyState("No accounts yet", EmptyArtwork.ACCOUNTS, compact = true) }
                    rows?.forEach { account -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText(account.text("email"), fontWeight = FontWeight.SemiBold)
                            Fact("Account", "${account.text("kind")} · ${account.text("status")}")
                            account.optJSONObject("portfolio")?.let { Fact("Bond purchases", it.text("purchaseCount")) }
                            HorizontalDivider()
                        }
                    } }
                } else registration?.let { settings ->
                    item { AppText(if (settings.optBoolean("available")) "Invitations are available" else "Invitations are unavailable", style = MaterialTheme.typography.bodyMedium) }
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
                            } }) { AppText("Create & share invitation") }
                            OutlinedButton(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = { model.action {
                                model.api.request("/api/bonds/auth/registration", "DELETE")
                                registration = model.api.request("/api/bonds/auth/registration")
                            } }) { AppText("Close invitations") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, footnote: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(title, Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
        footnote?.let { AppText(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary, toggleState: Boolean? = null, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier) {
    ListItem(headlineContent = { AppText(title, color = if (enabled) tint.takeIf { it == MaterialTheme.colorScheme.error } ?: MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant) }, supportingContent = subtitle?.let { { FinancialText(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = { AppIcon(icon, null, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = trailing ?: { AppIcon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().then(if (toggleState != null) Modifier.toggleable(toggleState, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)))
}

@Composable private fun SettingsDivider() { HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) }

@Composable
private fun AppearanceOptions(selected: String, choose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
            val active = selected == value
            Surface(Modifier.weight(1f).semantics { contentDescription = localized(label) }.selectable(active, role = Role.RadioButton, onClick = { choose(value) }),
                shape = RoundedCornerShape(16.dp),
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (active) 2.dp else 1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(width = 44.dp, height = 62.dp)) {
                        val cream = Color(0xFFF8F5E9)
                        val ink = Color(0xFF242A20)
                        val accent = Color(0xFFE1C76B)
                        drawRoundRect(if (value == "dark") ink else cream, cornerRadius = CornerRadius(7.dp.toPx()))
                        if (value == "system") drawRoundRect(ink, topLeft = Offset(size.width / 2, 0f),
                            size = Size(size.width / 2, size.height), cornerRadius = CornerRadius(5.dp.toPx()))
                        drawRoundRect(accent, Offset(size.width * .15f, size.height * .2f), Size(size.width * .7f, size.height * .24f), CornerRadius(3.dp.toPx()))
                        repeat(3) { index ->
                            drawRoundRect(if (value == "dark") cream.copy(alpha = .3f) else ink.copy(alpha = .2f),
                                Offset(size.width * .15f, size.height * (.55f + index * .12f)),
                                Size(size.width * .7f, size.height * .045f), CornerRadius(2.dp.toPx()))
                        }
                    }
                    AppText(label, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
