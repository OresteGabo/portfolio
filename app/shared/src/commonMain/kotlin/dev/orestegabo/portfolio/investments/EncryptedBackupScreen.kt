package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    DisposableEffect(Unit) { onDispose { pending?.fill(0) } }
    pending?.let { data -> AlertDialog(onDismissRequest = { data.fill(0); pending = null }, title = { AppText("Replace guest workspace?") },
        text = { AppText("This replaces your current guest investments, transactions and PDFs with the selected backup. Account data on the server is unaffected. Export your current workspace first if you want to keep it.") },
        confirmButton = { TextButton(enabled = !model.busy && !model.guestStorageLoading, onClick = {
            model.restoreGuestBackup(data) { data.fill(0); pending = null; password = ""; confirmation = ""; status = localized("Backup restored.") }
        }) { AppText("Replace and restore") } },
        dismissButton = { TextButton(onClick = { data.fill(0); pending = null }) { AppText("Cancel") } }) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DetailHeading("Your private guest backup")
            DetailNote("Includes guest investments, cash transactions, coupon receipts and original PDFs. Choose a strong password you can keep safely. There is no password recovery. The encrypted file can be restored on Android or iPhone.") }
        item { OutlinedTextField(password, { password = it }, label = { AppText("Backup password") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(confirmation, { confirmation = it }, label = { AppText("Confirm backup password (export)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth()) }
        item { Button(enabled = !working && !model.busy && !model.guestStorageLoading && model.guestPortfolio != null && !model.sampleGuest, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                working = true; status = null
                try {
                    require(password == confirmation) { "The backup passwords do not match." }
                    require(password.length in 12..256) { "Use a backup password between 12 and 256 characters." }
                    val plain = model.exportGuestBackup()
                    val encrypted = try { withContext(Dispatchers.Default) { encryptBackup(plain, password) } } finally { plain.fill(0) }
                    try { if (actions.saveBackup("Struct-Capital-$Today.scbackup", encrypted)) { status = localized("Encrypted backup saved."); password = ""; confirmation = "" } }
                    finally { encrypted.fill(0) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { status = failure.message ?: localized("Could not create backup.") }
                finally { working = false }
            }
        }) { AppText("Export encrypted backup") } }
        item { OutlinedButton(enabled = !working && !model.busy && !model.guestStorageLoading && !model.sampleGuest, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                working = true; status = null
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
        status?.let { value -> item { AppText(value) } }
        model.guestStorageError?.let { value -> item { AppText(value, color = MaterialTheme.colorScheme.error) } }
    }
}
