package dev.orestegabo.portfolio.investments

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: PortfolioModel, theme: String, changeTheme: (String) -> Unit,
    chartStyle: String, changeChartStyle: (String) -> Unit,
    openSimulator: () -> Unit, openEducation: () -> Unit, openBond: (JsonRecord) -> Unit, openLegal: () -> Unit = {}, openAbout: () -> Unit = {}, openIntroduction: () -> Unit = {}, openInvestmentTools: () -> Unit = {}, openSignInActivity: () -> Unit = {}, openGuest: () -> Unit = {}, openIssueReport: () -> Unit = {}) {
    val actions = LocalNativeActions.current
    var setupSecure by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var deletionPassword by remember { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var ownerSection by rememberSaveable { mutableStateOf<String?>(null) }
    val administration = remember(model) { PortfolioAdministration({ model.api }, model::action) }
    var publisherChange by remember { mutableStateOf<JsonRecord?>(null) }
    val email = model.user?.text("email").orEmpty()
    val owner = model.user?.optBoolean("isOwner") == true
    fun loadOwner(section: String) {
        ownerSection = section
        administration.load(section)
    }

    publisherChange?.takeIf { owner }?.let { account ->
        val enabled = !account.optBoolean("canPublishPublicData")
        AlertDialog(onDismissRequest = { publisherChange = null },
            title = { AppText(if (enabled) "Allow shared-data publication?" else "Remove publication permission?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(account.text("email"))
                AppText("This permission allows publishing reference data that affects all users. It does not grant owner administration access.")
            } },
            confirmButton = { TextButton(enabled = !model.busy, onClick = {
                administration.setPublisher(account, enabled) { publisherChange = null }
            }) { AppText(if (enabled) "Allow publication" else "Remove permission") } },
            dismissButton = { TextButton(onClick = { publisherChange = null }) { AppText("Cancel") } })
    }

    if (dialog == "guestPrivacy") AlertDialog(onDismissRequest = { dialog = null }, title = { AppText("Private on-device imports") }, text = { AppText("PDFs are parsed and saved locally. They are not sent to Inzira or an AI service. Selecting a file from a cloud provider may download it through that provider. Sharing a PDF sends it only to the destination you choose.") }, confirmButton = { TextButton(onClick = { dialog = null }) { AppText("Done") } })

    // In-scene financial editors must overlay the list, not become another child of the app's Column.
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (model.isGuest) item { GuestStorageSettings(model, openGuest) }
            else item {
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
            if (!model.isGuest) item { SettingsSection("Notifications") {
                SettingsRow(Icons.Outlined.Notifications, "Notification preferences", "Categories & coupon receipts", onClick={ dialog="notifications" })
                if(NativePushBridge.available) {
                    SettingsDivider()
                    SettingsRow(Icons.Outlined.PhoneIphone, "Phone notifications",if(NativePushBridge.allowed) "Enabled" else "Disabled",onClick=NativePushBridge::requestPermission)
                }
            } }
            if (HomeWidgetBridge.available && !model.isGuest) item { SettingsSection("Home screen widgets") {
                SettingsRow(Icons.Outlined.CalendarMonth, "What’s next · Small", "One upcoming event · no amounts", onClick = { HomeWidgetBridge.requestPin(false) })
                SettingsDivider()
                SettingsRow(Icons.Outlined.CalendarMonth, "What’s next · Medium", "Two upcoming events · no amounts", onClick = { HomeWidgetBridge.requestPin(true) })
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
                SettingsRow(Icons.Outlined.PrivacyTip, "PDF imports & your data", "How your documents are handled", onClick = { if (model.isGuest) dialog = "guestPrivacy" else dialog = "privacy" })
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
                SettingsRow(Icons.Outlined.AccountBalanceWallet, "Cash & transactions", "Sales, dividends, deposits and withdrawals", onClick = { model.openCashLedger() })
                SettingsDivider()
                if (model.isGuest && !model.sampleGuest) {
                    SettingsRow(Icons.Outlined.Lock, "Encrypted backup", "Back up or restore your private guest workspace", onClick = { model.backupRequested = true })
                    SettingsDivider()
                }
                Column(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()) {
                    SettingsRow(Icons.Outlined.Flag, "Monthly investment target", privateFinancialValue(money(model.monthlyInvestmentTarget.toDouble(), "RWF")),
                        onClick = { targetExpanded = !targetExpanded },
                        modifier = Modifier.semantics { stateDescription = localized(if (targetExpanded) "Expanded" else "Collapsed") },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(enabled = model.canEditInvestments, onClick = { dialog = "investmentTarget" }) {
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
            if (!model.isGuest) item { SettingsSection("Security") {
                SettingsRow(Icons.Outlined.Devices, "Sign-in activity", "Review recorded devices", onClick = openSignInActivity)
                SettingsDivider()
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
            if (!model.isGuest) item { SettingsSection("Tools") {
                SettingsRow(Icons.Outlined.PictureAsPdf, "Portfolio report", "All years · PDF export", enabled = !model.busy,
                    onClick = { dialog = "report" })
                SettingsDivider()
                SettingsRow(Icons.Outlined.Payments, "Investment outlook", "Scheduled cash flows & recorded buying fees", onClick = openInvestmentTools)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Calculate, "Investment simulator", onClick = openSimulator)
                SettingsDivider()
                SettingsRow(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    "Bond education",
                    onClick = openEducation
                )
                SettingsDivider()
                SettingsRow(Icons.Outlined.Search, "Find bond purchases", onClick = { ownerSection = "bonds" })
            } }
            item { SettingsSection("About") {
                SettingsRow(Icons.Outlined.Info, "About Inzira", "The app, the brand and how to reach us", onClick = openAbout)
                SettingsDivider()
                SettingsRow(Icons.Outlined.AutoStories, "Replay introduction", "A quick tour of Inzira", onClick = openIntroduction)
            } }
            item { SettingsSection("Help & support") {
                SettingsRow(Icons.Outlined.BugReport, "Report a problem", "Bugs, incorrect data or anything that is not working", onClick = openIssueReport)
                SettingsDivider()
                SettingsRow(Icons.Outlined.Email, "Contact support", BrandContacts.SUPPORT, onClick = { actions.openEmail(BrandContacts.SUPPORT) })
            } }
            item { SettingsSection("Legal & privacy") {
                SettingsRow(Icons.Outlined.Policy, "Legal documents", "Terms, privacy, data rights & credits", onClick = openLegal)
            } }
            item { SettingsSection("Links", "These links open in your browser.") {
                val links = listOf(
                    Triple(OFFICIAL_WEBSITE_ORIGIN, "Inzira", Icons.Outlined.Language),
                    *if (!model.isGuest) arrayOf(Triple(model.origin + "/portfolio", "Portfolio website", Icons.Outlined.AccountBalanceWallet)) else emptyArray(),
                    *if (!model.isGuest) arrayOf(Triple(model.origin + "/create-password", "Password setup", Icons.Outlined.Key)) else emptyArray(),
                )
                links.forEachIndexed { index, (url, title, icon) ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(icon, title, onClick = { actions.openUrl(url) },
                        trailing = {
                            AppIcon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                "Opens in browser",
                                Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        })
                }
            } }
            if (owner) item { SettingsSection("Administration") {
                SettingsRow(Icons.Outlined.ManageAccounts, "Accounts", "View registered accounts", enabled = !model.busy, onClick = { loadOwner("accounts") })
                SettingsDivider()
                SettingsRow(Icons.Outlined.PersonAdd, "Invitations", "Create and manage invitations", enabled = !model.busy, onClick = { loadOwner("invitations") })
            } }
            if (!model.isGuest) item { SettingsSection("Guest data") {
                SettingsRow(Icons.Outlined.PhoneIphone, "Transfer Guest Data", "Review data saved on this device", enabled = !model.busy, onClick = model::reviewGuestMigration)
            } }
            if (!model.isGuest) item { SettingsSection("Session") {
                SettingsRow(Icons.Outlined.PersonOutline, "Continue as Guest", onClick = openGuest, trailing = {})
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
        if (dialog == "report") PortfolioReportDialog(model) { dialog = null }
        PortfolioReportProgressDialog(model)
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

        SettingsRecordsSheet(model, ownerSection, administration, dismiss = { ownerSection = null },
            changePublisher = { publisherChange = it }, openBond = openBond)
    }
}
