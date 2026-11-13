@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid

@Composable
fun ShareOrderScreen(model: PortfolioModel, existing: JsonRecord, back: () -> Unit, recordExecution: (JsonRecord) -> Unit) {
    val id = rememberSaveable(existing.text("id")) { existing.text("id").ifBlank { Uuid.random().toString() } }
    var ticker by rememberSaveable(id) { mutableStateOf(existing.text("ticker")) }
    var quantity by rememberSaveable(id) { mutableStateOf(existing.text("shares")) }
    var expanded by remember { mutableStateOf(false) }
    var cancelDialog by remember { mutableStateOf(false) }
    var executionDialog by remember { mutableStateOf(false) }
    val editing = existing.text("id").isNotBlank()
    var quoteAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(model.busy) {
        if (!quoteAttempted && !model.busy && model.quotes == null) { quoteAttempted = true; model.loadMarket() }
    }
    val companies = model.quotes?.rows("quotes").orEmpty()
        .filter { it.text("ticker").isNotBlank() && it.optDouble("closing") > 0 }
        .distinctBy { it.text("ticker") }.sortedBy { it.text("name").ifBlank { it.text("ticker") } }
    val quote = companies.firstOrNull { it.text("ticker") == ticker }
    val sameSecurity = editing && ticker == existing.text("ticker")
    val name = quote?.text("name")?.ifBlank { ticker } ?: if (sameSecurity) existing.text("securityName") else ""
    val price = quote?.optDouble("closing") ?: if (sameSecurity) existing.optDouble("pricePerShare") else 0.0
    val estimate = runCatching { shareOrderEstimate(quantity.toInputNumberOrNull() ?: 0.0, price) }.getOrNull()
    val changed = editing && (ticker != existing.text("ticker") || quantity.toInputNumberOrNull() != existing.optDouble("shares") || price != existing.optDouble("pricePerShare"))
    if (cancelDialog) AlertDialog(onDismissRequest = { cancelDialog=false }, title = { AppText("Cancel pending order?") },
        text = { AppText("Remove this pending record? Your broker’s order stays unchanged.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { cancelDialog=false; model.cancelShareOrder(existing,back) }) { AppText("Cancel pending order") } },
        dismissButton = { TextButton(onClick = { cancelDialog=false }) { AppText("Keep order") } })
    if (executionDialog) AlertDialog(onDismissRequest = { executionDialog=false }, title = { AppText("Record completed purchase?") },
        text = { AppText("Review the actual price and fees before saving.") },
        confirmButton = { TextButton(onClick = {
            executionDialog=false
            recordExecution(JsonRecord().put("securityName",existing.text("securityName")).put("ticker",existing.text("ticker"))
                .put("shares",existing.getDouble("shares")).put("pricePerShare",existing.getDouble("pricePerShare"))
                .put("broker",existing.text("broker")).put("pendingOrderId",id).put("pendingOrderVersion",existing.optInt("version")))
        }) { AppText("Review executed purchase") } }, dismissButton = { TextButton(onClick = { executionDialog=false }) { AppText("Not yet") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!model.busy && companies.isNotEmpty()) expanded=it }) {
                OutlinedTextField(value = if (name.isBlank()) "" else "$name ($ticker)", onValueChange = {}, readOnly = true,
                    label = { AppText("Security name") }, singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded = expanded,onDismissRequest = { expanded=false }) {
                    companies.forEach { company -> DropdownMenuItem(text = { AppText("${company.text("name").ifBlank { company.text("ticker") }} (${company.text("ticker")})") },
                        leadingIcon = { InvestmentLogo(ticker=company.text("ticker"),modifier=Modifier.size(28.dp)) },
                        onClick = { ticker=company.text("ticker"); expanded=false }) }
                }
            }
        }
        item { Field("Number of shares",quantity,{ quantity=it },KeyboardType.Number) }
        if (ticker.isNotBlank() && price > 0) item { Fact("Market price",money(price)) }
        if (!model.busy && companies.isEmpty()) item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                AppText("Market prices unavailable.",Modifier.weight(1f),style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.loadMarket(refresh=true) }) { AppText("Retry") }
            }
        }
        if (estimate != null) item { Panel {
            Fact("Gross consideration",money(estimate.gross))
            Fact("Market commission · 1.71%",money(estimate.commission))
            HorizontalDivider()
            Fact("Estimated amount payable",money(estimate.total))
        } }
        if (!model.shareOrdersAvailable && !model.busy) item { AppText("Pending orders unavailable. Pull down on Home to retry.",style = MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
        item { Button(enabled = !model.busy && model.shareOrdersAvailable && estimate!=null && name.isNotBlank(),modifier = Modifier.fillMaxWidth().heightIn(min=52.dp),onClick = {
            model.saveShareOrder(id,JsonRecord().put("version",existing.optInt("version")).put("ticker",ticker).put("securityName",name)
                .put("shares",quantity.toInputNumberOrNull()!!).put("pricePerShare",price)
                .put("requestDate",existing.text("requestDate").ifBlank { Today.toString() })
                .put("expectedExecutionDate",existing.text("expectedExecutionDate")).put("broker",existing.text("broker"))
                .put("brokerConfirmed",existing.optBoolean("brokerConfirmed")).put("notes",existing.text("notes")),back)
        }) { AppText(if(model.busy) "Saving…" else "Save pending order") } }
        if (editing) {
            if (changed) item { AppText("Save changes before recording execution.",style = MaterialTheme.typography.bodySmall) }
            item { OutlinedButton(enabled = !changed && !model.busy && model.shareOrdersAvailable,modifier = Modifier.fillMaxWidth(),onClick = { executionDialog=true }) { AppText("Record executed purchase") } }
            item { TextButton(enabled = !model.busy && model.shareOrdersAvailable,modifier = Modifier.fillMaxWidth(),onClick = {cancelDialog=true}) { AppText("Cancel pending order",color = MaterialTheme.colorScheme.error) } }
        }
    }
}
