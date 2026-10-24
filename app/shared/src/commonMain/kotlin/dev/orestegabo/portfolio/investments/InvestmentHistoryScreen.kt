package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InvestmentHistoryScreen(model: PortfolioModel, uploadPending: (JsonRecord) -> Unit, detail: (JsonRecord) -> Unit) {
    var kind by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("all") }
    LaunchedEffect(model.historyComplete, model.busy) {
        if (!model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
    }
    val years = (model.purchases.map { it.text("purchaseDate").take(4) } + model.holdings.map { it.text("tradeDate").take(4) })
        .filter { it.toIntOrNull() != null }.distinct().sortedDescending()
    fun matches(row: JsonRecord, dateKey: String, nameKey: String) =
        (year == "all" || row.text(dateKey).startsWith(year)) &&
            listOf(nameKey, "ticker", "isin", "broker").any { row.text(it).contains(search, true) }
    val bonds = if (kind == "shares") emptyList() else model.purchases.filter { matches(it, "purchaseDate", "bondName") }
        .sortedByDescending { it.text("purchaseDate") }
    val shares = if (kind == "bonds") emptyList() else model.holdings.filter { matches(it, "tradeDate", "securityName") }
        .sortedByDescending { it.text("tradeDate") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChoiceRow(listOf("all", "shares", "bonds"), kind) { kind = it } }
            IconButton(enabled = !model.busy, onClick = { model.loadInvestmentHistory(force = true) }) { Icon(Icons.Outlined.Refresh, "Refresh investment history") }
        } }
        if (!model.historyComplete) item {
            if (model.busy) LoadingState()
            else EmptyState("History unavailable", EmptyArtwork.PORTFOLIO, compact = true,
                actionLabel = "Try again", onAction = { model.error = null; model.loadInvestmentHistory() })
        } else {
            item { Field("Search investments", search, { search = it }) }
            if (years.isNotEmpty()) item { ChoiceRow(listOf("all") + years, year) { year = it } }
            if (bonds.isEmpty() && shares.isEmpty()) item {
                EmptyState("No investments found", EmptyArtwork.SEARCH, compact = true,
                    actionLabel = if (search.isNotBlank() || year != "all") "Clear filters" else null,
                    onAction = if (search.isNotBlank() || year != "all") ({ search = ""; year = "all"; kind = "all" }) else null)
            }
            if (bonds.isNotEmpty()) item { Text("Bonds", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(bonds, key = { "bond:${it.text("id")}" }) { BondPurchaseTile(it, uploadConfirmation = { uploadPending(it) }) { detail(it) } }
            if (shares.isNotEmpty()) item { Text("Shares", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(shares, key = { "share:${it.text("id")}" }) { SharePurchaseTile(it) }
        }
    }
}
