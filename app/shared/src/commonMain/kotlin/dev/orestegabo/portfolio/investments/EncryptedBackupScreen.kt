package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.orestegabo.portfolio.design.InziraDimensions
import dev.orestegabo.portfolio.design.InziraSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EncryptedBackupScreen(model: PortfolioModel) {
    val actions = LocalNativeActions.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var exportAttempted by remember { mutableStateOf(false) }
    var restoreAttempted by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val errors = backupFormErrors(password, confirmation, exporting = exportAttempted)
    val blocked = working || model.busy || model.guestStorageLoading
    rememberFormBackHandler(dirty = false, busy = blocked, back = { model.backupRequested = false })
    fun dismissRestore() {
        if (!working && !model.busy && !model.guestStorageLoading) { pending?.fill(0); pending = null }
    }
    DisposableEffect(Unit) { onDispose { pending?.fill(0) } }
    pending?.let { data -> AlertDialog(onDismissRequest = ::dismissRestore, title = { AppText("Replace guest workspace?") },
        text = { AppText("This replaces your current guest investments, transactions and PDFs with the selected backup. Account data on the server is unaffected. Export your current workspace first if you want to keep it.") },
        confirmButton = { TextButton(enabled = !blocked, onClick = {
            if (!working && !model.busy && !model.guestStorageLoading) model.restoreGuestBackup(data) { data.fill(0); pending = null; password = ""; confirmation = ""; exportAttempted = false; restoreAttempted = false; status = localized("Backup restored.") }
        }) { AppText(if (model.guestStorageLoading) "Working…" else "Replace and restore") } },
        dismissButton = { TextButton(enabled = !blocked, onClick = ::dismissRestore) { AppText("Cancel") } }) }
    FormContent { formModifier ->
        LazyColumn(formModifier.imePadding(), contentPadding = PaddingValues(InziraSpacing.screen), verticalArrangement = Arrangement.spacedBy(InziraSpacing.large)) {
            item { DetailHeading("Your private guest backup")
                DetailNote("Includes guest investments, cash transactions, coupon receipts and original PDFs. Choose a strong password you can keep safely. There is no password recovery. The encrypted file can be restored on Android or iPhone.") }
            item { OutlinedTextField(password, { password = it; status = null }, label = { AppText("Backup password") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                isError = (exportAttempted || restoreAttempted) && errors.password != null,
                supportingText = { AppText(if (exportAttempted || restoreAttempted) errors.password ?: "12–256 characters." else "12–256 characters.") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !blocked, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(confirmation, { confirmation = it; status = null }, label = { AppText("Confirm backup password (export)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                isError = exportAttempted && errors.confirmation != null,
                supportingText = if (exportAttempted && errors.confirmation != null) ({ AppText(errors.confirmation) }) else null,
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !blocked, modifier = Modifier.fillMaxWidth()) }
            item { Button(enabled = !working && !model.busy && !model.guestStorageLoading && model.guestPortfolio != null && !model.sampleGuest, modifier = Modifier.fillMaxWidth().heightIn(min = InziraDimensions.primaryActionMinHeight), onClick = export@{
                if (working || model.busy || model.guestStorageLoading) return@export
                exportAttempted = true
                if (!backupFormErrors(password, confirmation, exporting = true).valid) return@export
                focus.clearFocus()
                working = true; status = null
                scope.launch {
                    try {
                        require(password == confirmation) { "The backup passwords do not match." }
                        require(password.length in 12..256) { "Use a backup password between 12 and 256 characters." }
                        val plain = model.exportGuestBackup()
                        val encrypted = try { withContext(Dispatchers.Default) { encryptBackup(plain, password) } } finally { plain.fill(0) }
                        try {
                            if (actions.saveBackup("Inzira-$Today.scbackup", encrypted)) {
                                status = localized("Encrypted backup saved."); password = ""; confirmation = ""; exportAttempted = false; restoreAttempted = false
                            } else status = localized("Backup not saved. You can try again.")
                        }
                        finally { encrypted.fill(0) }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { status = failure.message ?: localized("Could not create backup.") }
                    finally { working = false }
                }
            }) { AppText("Export encrypted backup") } }
            item { OutlinedButton(enabled = !working && !model.busy && !model.guestStorageLoading && !model.sampleGuest, modifier = Modifier.fillMaxWidth().heightIn(min = InziraDimensions.primaryActionMinHeight), onClick = restore@{
                if (working || model.busy || model.guestStorageLoading) return@restore
                restoreAttempted = true
                if (!backupFormErrors(password, confirmation, exporting = false).valid) return@restore
                focus.clearFocus()
                working = true; status = null
                scope.launch {
                    try {
                        val encrypted = actions.pickBackup()
                        if (encrypted != null) {
                            pending = try { withContext(Dispatchers.Default) { decryptBackup(encrypted, password) } } finally { encrypted.fill(0) }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { status = failure.message ?: localized("Could not open backup.") }
                    finally { working = false }
                }
            }) { AppText("Select backup to restore") } }
            if (working || model.guestStorageLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            status?.let { value -> item { AppText(value, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            model.guestStorageError?.let { value -> item { AppText(value, color = MaterialTheme.colorScheme.error) } }
        }
    }
}
