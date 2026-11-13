@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
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
    Column(Modifier.fillMaxSize().imePadding()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!model.busy && companies.isNotEmpty()) expanded=it }) {
                Surface(modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(24.dp), color = colors.surface,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha=.18f))) {
                    Column(Modifier.padding(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppText("Security name",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            if (ticker.isNotBlank()) InvestmentLogo(ticker=ticker,modifier=Modifier.size(52.dp))
                            else Surface(shape=RoundedCornerShape(14.dp),color=colors.primary.copy(alpha=.07f)) { StructMark(Modifier.padding(10.dp).size(32.dp)) }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                AppText(name.ifBlank { localized("Select a company") },style=MaterialTheme.typography.titleMedium,
                                    fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                if (ticker.isNotBlank()) AppText(ticker,style=MaterialTheme.typography.labelMedium,color=colors.primary)
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        }
                        HorizontalDivider(color=colors.outlineVariant.copy(alpha=.45f))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            AppText("Market price",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            FinancialText(if (price>0) money(price) else "—",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,color=colors.primary)
                        }
                    }
                }
                ExposedDropdownMenu(expanded = expanded,onDismissRequest = { expanded=false }) {
                    companies.forEach { company -> DropdownMenuItem(text = { AppText("${company.text("name").ifBlank { company.text("ticker") }} (${company.text("ticker")})") },
                        leadingIcon = { InvestmentLogo(ticker=company.text("ticker"),modifier=Modifier.size(28.dp)) },
                        onClick = { ticker=company.text("ticker"); expanded=false }) }
                }
            }
        }
        item { ShareOrderQuantity(quantity,{quantity=it},enabled=!model.busy) }
        if (!model.busy && companies.isEmpty()) item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                AppText("Market prices unavailable.",Modifier.weight(1f),style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.loadMarket(refresh=true) }) { AppText("Retry") }
            }
        }
        item { ShareOrderTotal(estimate) }
        if (!model.shareOrdersAvailable && !model.busy) item { AppText("Pending orders unavailable. Pull down on Home to retry.",style = MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
        item { Button(enabled = !model.busy && model.shareOrdersAvailable && estimate!=null && name.isNotBlank(),modifier = Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp),onClick = {
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
    if (keyboardVisible) Surface(color=colors.surface) {
        Column {
            HorizontalDivider(color=colors.outlineVariant.copy(alpha=.5f))
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={focusManager.clearFocus(force=true);keyboard?.hide()}) { AppText("Done") }
            }
        }
    }
    }
}


@Composable
private fun ShareOrderQuantity(value: String, change: (String) -> Unit, enabled: Boolean) {
    val colors=MaterialTheme.colorScheme
    val hidden=LocalNumbersHidden.current
    val focus=LocalFocusManager.current
    val amount=value.toInputNumberOrNull()?.toLong() ?: 0L
    Surface(shape=RoundedCornerShape(24.dp),color=colors.surface,border=BorderStroke(1.dp,colors.outlineVariant.copy(alpha=.55f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            AppText("Number of shares",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
            CaptureRedactionRegion(Modifier.fillMaxWidth(),masked={
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { AppText("••••",style=MaterialTheme.typography.displaySmall,color=colors.onSurface) }
            }) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(onClick={change((amount-100).coerceAtLeast(100).toString())},enabled=enabled && amount>100,
                        modifier=Modifier.size(48.dp),shape=RoundedCornerShape(14.dp)) { AppIcon(Icons.Outlined.Remove,"Decrease shares") }
                    BasicTextField(value=value,onValueChange=change,enabled=enabled,singleLine=true,
                        modifier=Modifier.weight(1f).heightIn(min=60.dp).semantics { contentDescription=localized("Number of shares") },
                        textStyle=MaterialTheme.typography.displaySmall.copy(fontSize=if(value.length>7) 28.sp else 40.sp,fontWeight=FontWeight.SemiBold,
                            color=colors.onSurface,textAlign=TextAlign.Center,fontFeatureSettings="tnum"),
                        cursorBrush=SolidColor(colors.primary),
                        visualTransformation=if(hidden) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),
                        decorationBox={ input -> Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                            if(value.isEmpty()) AppText("0",style=MaterialTheme.typography.displaySmall,color=colors.onSurfaceVariant.copy(alpha=.35f))
                            input()
                        } })
                    FilledTonalIconButton(onClick={change((amount+100).coerceAtMost(1_000_000_000).toString())},enabled=enabled && amount<1_000_000_000,
                        modifier=Modifier.size(48.dp),shape=RoundedCornerShape(14.dp)) { AppIcon(Icons.Outlined.Add,"Increase shares") }
                }
            }
        }
    }
}

@Composable
private fun ShareOrderTotal(estimate: ShareOrderEstimate?) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(26.dp),color=colors.primary,contentColor=colors.onPrimary) {
        Box(Modifier.fillMaxWidth()) {
            StructMark(Modifier.align(Alignment.TopEnd).offset(x=20.dp,y=(-16).dp).size(140.dp).alpha(.075f).clearAndSetSemantics {},onDark=colors.primary.luminance()<.5f)
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppText("Estimated amount payable",style=MaterialTheme.typography.labelLarge,color=colors.onPrimary.copy(alpha=.8f))
                    FinancialText(estimate?.let {money(it.total)} ?: "—",style=MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings="tnum"),fontWeight=FontWeight.SemiBold,color=colors.onPrimary)
                }
                HorizontalDivider(color=colors.onPrimary.copy(alpha=.2f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        AppText("Gross consideration",style=MaterialTheme.typography.labelSmall,color=colors.onPrimary.copy(alpha=.75f))
                        FinancialText(estimate?.let {money(it.gross)} ?: "—",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,color=colors.onPrimary)
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        AppText("Market commission · 1.71%",style=MaterialTheme.typography.labelSmall,color=colors.onPrimary.copy(alpha=.75f))
                        FinancialText(estimate?.let {money(it.commission)} ?: "—",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,color=colors.onPrimary)
                    }
                }
            }
        }
    }
}
