package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Protected in-scene form. User-entered reports stay in memory until explicitly handed off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueReportScreen(source: String, close: () -> Unit) {
    val actions = LocalNativeActions.current
    val version = rememberAppVersion()
    val details = remember(source, version, AppLanguage.code) {
        IssueReportDetails(source, version?.let { listOfNotNull(it.name, it.build?.let { build -> "($build)" }).joinToString(" ") }
            ?: localized("Unavailable"), issueReportPlatform(), AppLanguage.code)
    }
    // Do not put free-form issue text in saved-instance state, preferences or logs.
    var draft by remember { mutableStateOf(IssueReportDraft()) }
    var includeDetails by remember { mutableStateOf(true) }
    var reviewing by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    fun back() {
        if (reviewing) { reviewing = false; message = null }
        else if (draft.empty) close() else discard = true
    }
    PlatformBackHandler(true, ::back)
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { AppText("Discard this report?") }, text = { AppText("Your unsent report will be removed from this screen.") },
        confirmButton = { TextButton(onClick = close) { AppText("Discard report") } },
        dismissButton = { TextButton(onClick = { discard = false }) { AppText("Keep editing") } })

    Scaffold(Modifier.fillMaxSize().semantics { paneTitle = localized("Report a problem") }
        .pointerInput(Unit) { detectTapGestures {} }, containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { AppText(if (reviewing) "Review report" else "Report a problem") },
            navigationIcon = { IconButton(onClick = ::back, enabled = !opening) { AppIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
        key(reviewing) {
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (!reviewing) {
                    AppText("Describe the problem you noticed.", style = MaterialTheme.typography.bodyLarge)
                    AppText("Keep passwords and private financial details out of your report.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppText("Issue type", style = MaterialTheme.typography.labelMedium)
                                    AppText(draft.category.label)
                                }
                                AppIcon(Icons.Outlined.ExpandMore, null)
                            }
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            IssueCategory.entries.forEach { category ->
                                DropdownMenuItem(text = { AppText(category.label) }, onClick = { draft = draft.copy(category = category); categoryMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(value = draft.description, onValueChange = { draft = draft.copy(description = it.take(2000)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 4,
                        label = { AppText("What happened? (required)") },
                        supportingText = { Column { AppText("Describe the issue in 10–2,000 characters."); Text("${draft.description.length}/2000") } })
                    OutlinedTextField(value = draft.steps, onValueChange = { draft = draft.copy(steps = it.take(1000)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                        label = { AppText("Steps to reproduce (optional)") },
                        supportingText = { Column { AppText("What were you doing just before it happened?"); Text("${draft.steps.length}/1000") } })
                    OutlinedTextField(value = draft.expected, onValueChange = { draft = draft.copy(expected = it.take(1000)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                        label = { AppText("Expected result (optional)") },
                        supportingText = { Column { AppText("For incorrect data, describe what you expected and your source."); Text("${draft.expected.length}/1000") } })
                    Row(Modifier.fillMaxWidth().toggleable(includeDetails, role = Role.Checkbox, onValueChange = { includeDetails = it })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(includeDetails, onCheckedChange = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText("Include app details")
                            AppText("App version, platform, language and screen name only. You can review them before sharing.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { reviewing = true }, enabled = draft.valid, modifier = Modifier.fillMaxWidth()) { AppText("Review report") }
                } else {
                    val body = issueReportBody(draft, if (includeDetails) details else null, AppLanguage.code)
                    AppText("This is the exact text that will be added to your email draft.", style = MaterialTheme.typography.bodyLarge)
                    AppText(BrandContacts.SUPPORT, style = MaterialTheme.typography.titleMedium)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                        SelectionContainer { Text(body, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                    }
                    AppText("If helpful, attach a screenshot in your email app. Hide private information first.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    message?.let { AppText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
                    Button(enabled = !opening, modifier = Modifier.fillMaxWidth(), onClick = {
                        opening = true
                        scope.launch {
                            try {
                                val opened = actions.composeEmail(BrandContacts.SUPPORT,
                                    "Struct Capital · ${localized(draft.category.label)}", body)
                                message = if (opened) "Email draft opened. Send it from your email app; opening the draft does not send the report."
                                    else "No email app could be opened. Copy the report and email it to support@structcapital.net."
                            } catch (cancelled: CancellationException) { throw cancelled }
                              catch (_: Exception) { message = "No email app could be opened. Copy the report and email it to support@structcapital.net." }
                            finally { opening = false }
                        }
                    }) { AppText(if (opening) "Opening email…" else "Open email draft") }
                    OutlinedButton(enabled = !opening, modifier = Modifier.fillMaxWidth(), onClick = {
                        clipboard.setText(AnnotatedString("${BrandContacts.SUPPORT}\n\n$body"))
                        message = "Report copied. Paste it into an email to support@structcapital.net."
                    }) { AppText("Copy report") }
                    TextButton(onClick = { reviewing = false; message = null }, enabled = !opening, modifier = Modifier.fillMaxWidth()) { AppText("Edit report") }
                }
            }
        }
    }
}
