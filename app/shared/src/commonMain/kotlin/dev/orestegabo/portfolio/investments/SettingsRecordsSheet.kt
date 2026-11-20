package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsRecordsSheet(
    model: PortfolioModel,
    ownerSection: String?,
    administration: PortfolioAdministration,
    dismiss: () -> Unit,
    changePublisher: (JsonRecord) -> Unit,
    openBond: (JsonRecord) -> Unit,
) {
    val actions = LocalNativeActions.current
    val owner = model.user?.optBoolean("isOwner") == true
    val accounts = administration.accounts
    val registration = administration.registration
    var bondSearch by rememberSaveable { mutableStateOf("") }
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ownerSection, model.historyComplete, model.busy) {
        if (ownerSection == "bonds" && !model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
    }
    if (ownerSection != null && (owner || ownerSection == "bonds")) ModalBottomSheet(onDismissRequest = { dismiss() }) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(when (ownerSection) { "accounts" -> "Accounts"; "bonds" -> "Bond purchases"; else -> "Invitations" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(enabled = !model.busy, onClick = { if (ownerSection == "bonds") model.loadInvestmentHistory(force = true) else administration.load(ownerSection!!) }) { AppIcon(Icons.Outlined.Refresh, "Refresh ${ownerSection}") }
                IconButton(onClick = { dismiss() }) { AppIcon(Icons.Outlined.Close, "Close") }
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
                        modifier = Modifier.clickable { dismiss(); openBond(purchase) })
                } }
            } else if (ownerSection == "accounts") {
                val rows = accounts?.rows("accounts")
                if (rows != null && rows.isEmpty() && !model.busy) item { EmptyState("No accounts yet", EmptyArtwork.ACCOUNTS, compact = true) }
                rows?.forEach { account -> item(key = account.text("id")) {
                    OwnerAccountRow(account, model.busy, changePublisher)
                } }
            } else registration?.let { settings ->
                item { AppText(if (settings.optBoolean("available")) "Invitations are available" else "Invitations are unavailable", style = MaterialTheme.typography.bodyMedium) }
                settings.optJSONObject("invitation")?.let { invitation -> item { Panel {
                    Fact("Active invitation", invitation.text("email")); Fact("Expires", invitation.text("expiresAt"))
                } } }
                if (settings.optBoolean("available")) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field("Invitation email (optional)", inviteEmail, { inviteEmail = it }, enabled = !model.busy)
                        Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = { administration.createInvitation(inviteEmail, model.origin, actions::shareText) }) { AppText("Create & share invitation") }
                        OutlinedButton(enabled = !model.busy, modifier = Modifier.fillMaxWidth(), onClick = administration::closeInvitations) { AppText("Close invitations") }
                    }
                }
            }
        }
    }
}
