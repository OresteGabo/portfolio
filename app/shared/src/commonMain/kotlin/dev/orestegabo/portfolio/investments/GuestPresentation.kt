package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Pure view selection. Account caches and guest/sample records are never copied into one another. */
internal fun guestViewPortfolio(path: GuestPath?, personal: GuestPortfolio?, sample: PortfolioSnapshot): PortfolioSnapshot =
    if (path == GuestPath.SAMPLE) sample else personal?.portfolio ?: PortfolioSnapshot()

internal fun guestSamplePortfolio(today: LocalDate = Today): PortfolioSnapshot {
    val purchase = today.minus(DatePeriod(months = 9))
    return PortfolioSnapshot(
        purchases = listOf(JsonRecord().put("id", "sample:bond").put("bondName", "Example bond — fictional")
            .put("issuer", "Example issuer — fictional").put("isin", "EXAMPLE-ONLY").put("instrumentType", "treasury")
            .put("currency", "RWF").put("status", "active").put("purchaseDate", purchase.toString())
            .put("settlementDate", purchase.toString()).put("maturityDate", purchase.plus(DatePeriod(years = 7)).toString())
            .put("firstCouponDate", purchase.plus(DatePeriod(months = 6)).toString()).put("couponRate", .12)
            .put("couponFrequency", 2).put("withholdingTaxRate", .05).put("scheduleConfidence", "estimated")
            .put("faceValue", 1000000).put("pricePercent", 100).put("feesPaid", 0).put("accruedInterestPaid", 0)),
        holdings = listOf(JsonRecord().put("id", "sample:shares").put("securityName", "Example company — fictional")
            .put("ticker", "DEMO").put("isin", "EXAMPLE-SHARES").put("currency", "RWF").put("status", "active")
            .put("tradeDate", purchase.toString()).put("settlementDate", purchase.toString()).put("shares", 100)
            .put("pricePerShare",495).put("grossConsideration",49500).put("totalCharges",500).put("netAmountPayable",50000)))
}

internal fun guestViewDocuments(portfolio: PortfolioSnapshot) = portfolio.documents.map { doc ->
    val record = (portfolio.purchases + portfolio.holdings).firstOrNull { it.hasDocument(doc.text("id")) }
    JsonRecord(doc.toString()).put("label", if (doc.text("kind") == "bond") "Bond confirmation" else "Share contract")
        .put("category", if (doc.text("kind") == "bond") "Bond confirmation" else "Share contract")
        .put("originalFileName",doc.text("fileName"))
        .put("instrumentName", record?.text("bondName")?.ifBlank { record.text("securityName") }.orEmpty())
        .put("issuer", record?.text("issuer").orEmpty()).put("visibility", "private")
}

@Composable internal fun GuestWorkspaceStatus(model: PortfolioModel, settings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                AppText(if (model.sampleGuest) "Fictional sample · Read only" else "Guest · On this device", style = MaterialTheme.typography.labelMedium)
                if (model.sampleGuest) AppText("These are not your investments.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = settings) { AppText("Local storage") }
        }
    }
}

@Composable internal fun GuestStorageSettings(model: PortfolioModel, choose: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    if (deleting) AlertDialog(onDismissRequest = { if (!model.busy) deleting = false },
        title = { AppText("Delete Guest Data?") },
        text = { AppText("Delete all personal guest investments, coupon receipts and saved PDFs from this device? There is no cloud backup. Account data and files you exported are not deleted. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { model.deleteGuestData(); deleting = false }) { AppText("Delete Guest Data", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !model.busy, onClick = { deleting = false }) { AppText("Keep my data") } })
    Panel {
        AppText("Local storage", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        AppText(if (model.sampleGuest) "Fictional examples are separate from your personal workspace. Resetting the sample never changes personal investments."
            else "Your guest investments and original PDFs stay in protected app storage on this device. They are not synced or backed up.")
        AppText("Losing this device or uninstalling the app can permanently lose guest data. Signing in does not automatically upload it.", style = MaterialTheme.typography.bodySmall)
        if (model.sampleGuest) TextButton(onClick = model::resetSample) { AppText("Reset sample") }
        else TextButton(enabled = !model.busy, onClick = { deleting = true }) { AppText("Delete Guest Data", color = MaterialTheme.colorScheme.error) }
        OutlinedButton(enabled = !model.busy, onClick = choose) { AppText("Change guest experience") }
        TextButton(enabled = !model.busy, onClick = model::beginAccountSignIn) { AppText("Sign in to your account") }
        DetailNote("Cloud sync is not enabled for guest data.")
    }
}

@Composable internal fun DeleteLocalDocument(model: PortfolioModel, document: JsonRecord) {
    var confirm by remember(document.text("id")) { mutableStateOf(false) }
    TextButton(enabled = !model.busy, onClick = { confirm = true }) { AppText("Delete local PDF", color = MaterialTheme.colorScheme.error) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { AppText("Delete local PDF?") },
        text = { AppText("Delete the original PDF from this device? Investment records and coupon receipts will remain. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = { model.deleteGuestDocument(document.text("id")); confirm = false }) { AppText("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { AppText("Cancel") } })
}
