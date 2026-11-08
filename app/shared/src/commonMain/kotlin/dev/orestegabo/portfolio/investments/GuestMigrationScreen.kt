package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun GuestMigrationScreen(model: PortfolioModel,transfer: GuestMigration) {
    transfer.preview?.let { preview ->
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = transfer::closePreview) { AppText("Back to transfer review") }
            Box(Modifier.weight(1f)) { PdfViewerScreen(model,preview,retry=transfer::closePreview) }
        }
        return
    }
    var expanded by remember(transfer) { mutableStateOf<Set<String>>(emptySet()) }
    val chosen=transfer.rows.filter { it.id in transfer.selected }
    val ready=transfer.prepared && chosen.isNotEmpty() && chosen.all { row -> transfer.selectable(row) &&
        (row.kind!="document" || transfer.documentTypeSupported(row) && calendarDate(transfer.dates[row.id].orEmpty())!=null) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {
            PageTitle("Guest data","Review before transferring","Nothing is uploaded until you select items and confirm. Your original guest data stays on this device.")
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                DetailNote("Destination account")
                FinancialText(transfer.account.label)
                DetailNote("Server")
                FinancialText(transfer.account.origin,style=MaterialTheme.typography.bodyMedium)
            }
            DetailNote("Existing cloud investments and receipts are never silently replaced. Conflicts need your review.")
        }
        if(transfer.preparing) item { LinearProgressIndicator(Modifier.fillMaxWidth());AppText("Preparing transfer review…") }
        transfer.failure?.let { item { AppText(it,color=MaterialTheme.colorScheme.error)
            if(!transfer.prepared) TextButton(onClick=model::reviewGuestMigration) { AppText("Try again") }
        } }
        if(transfer.prepared && transfer.rows.isEmpty()) item { EmptyState("No personal guest data on this device",EmptyArtwork.PORTFOLIO,compact=true) }
        listOf("bond" to "Bonds","equity" to "Shares","receipt" to "Coupon receipts","cash" to "Cash transactions","document" to "Original PDFs").forEach { (kind,title) ->
            val group=transfer.rows.filter { it.kind==kind }
            if(group.isNotEmpty()) item { DetailHeading(title) }
            items(group,key={it.id}) { row ->
                val supported=kind!="document" || transfer.documentTypeSupported(row)
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(row.id in transfer.selected,
                    enabled=!transfer.running && transfer.selectable(row) && supported,role=Role.Checkbox,
                    onValueChange={transfer.choose(row.id,it)})) {
                    Checkbox(row.id in transfer.selected,onCheckedChange=null,enabled=!transfer.running && transfer.selectable(row) && supported)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                        FinancialText(if (kind == "receipt") row.title.substringBeforeLast(" · ") + " · " + displayDate(row.record.text("dueDate")) else row.title,style=MaterialTheme.typography.titleSmall)
                        if(row.id in transfer.previous) DetailNote("Transferred previously to this account")
                        val r=row.record
                        if(kind=="bond") FinancialText(money(r.optDouble("faceValue"),r.text("currency")))
                        if(kind=="equity") FinancialText("${r.text("shares")} · ${money(r.optDouble("netAmountPayable"),r.text("currency"))}")
                        if(kind=="receipt") {
                            FinancialText(money(r.optDouble("amount"),r.text("currency")))
                            AppText(if(r.optBoolean("received")) "Marked received" else "Marked not received",style=MaterialTheme.typography.bodySmall)
                            AppText(displayRecordValue(r.text("method")),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if(!transfer.selectable(row)) DetailNote("Select this receipt's bond first, or transfer that bond before retrying.")
                if(!supported) DetailNote("Reimport this PDF in Guest Mode to verify its document type.")
                if(kind=="document") {
                    TextButton(enabled=!transfer.running,onClick={model.previewGuestMigrationDocument(row)}) { AppText("View local PDF") }
                    if(row.id in transfer.selected) Field("Document date (from PDF)",transfer.dates[row.id].orEmpty(),{transfer.date(row.id,it)},helper="YYYY-MM-DD · Check the original PDF",enabled=!transfer.running)
                } else if(kind in listOf("bond","equity")) {
                    DetailDisclosure("Review financial details",row.id in expanded) { expanded=if(row.id in expanded)expanded-row.id else expanded+row.id }
                    if(row.id in expanded) {
                        if(kind=="bond") { if(row.record.text("status")=="submitted") PendingPurchaseFacts(row.record) else BondPurchaseFacts(row.record) }
                        else SharePurchaseFacts(row.record)
                    }
                }
                transfer.results[row.id]?.let { result ->
                    DetailNote(when(result.status) { "created"->"Saved to your account";"matched"->"Already in your account · matched";"already-transferred"->"Transfer already acknowledged";else->result.message })
                }
                HorizontalDivider()
            }
        }
        if(transfer.prepared && transfer.rows.isNotEmpty()) item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                FinancialText("${chosen.size} ${localized("selected items")}")
                DetailNote("Only selected originals are uploaded. A PDF is sent in full, including any other investments mentioned inside it. Unselected PDFs stay on this device.")
                DetailNote("Automatic receipt markings remain automatic; they do not verify a bank payment. Original guest records are not deleted after transfer.")
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(transfer.consent,enabled=ready && !transfer.running,
                    role=Role.Checkbox,onValueChange={transfer.consent=it})) {
                    Checkbox(transfer.consent,onCheckedChange=null,enabled=ready && !transfer.running)
                    AppText("I reviewed the selected data and consent to sending it to this account.",Modifier.weight(1f).padding(vertical=12.dp))
                }
                transfer.progress?.let { AppText(it);LinearProgressIndicator(Modifier.fillMaxWidth()) }
                Button(enabled=ready && transfer.consent && !transfer.running,onClick=model::transferGuestMigration,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { AppText("Transfer selected data") }
                TextButton(onClick=model::closeGuestMigration) { AppText(if(transfer.running) "Stop transfer" else "Not now") }
                if(transfer.running) DetailNote("Stopping does not undo items already saved. You can review and confirm a retry later.")
            }
        }
    }
}
