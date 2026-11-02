package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Re-created fictional examples, never model.portfolio, persisted holdings or a backend account. */
internal fun guestSamplePortfolio() = PortfolioSnapshot(
    purchases = listOf(JsonRecord().put("id", "sample:bond").put("bondName", "Example bond — fictional")
        .put("currency", "RWF").put("status", "active").put("purchaseDate", "2020-01-01")
        .put("faceValue", 1000000).put("pricePercent", 100).put("feesPaid", 0).put("accruedInterestPaid", 0)),
    holdings = listOf(JsonRecord().put("id", "sample:shares").put("securityName", "Example company — fictional")
        .put("ticker", "DEMO").put("currency", "RWF").put("status", "active").put("tradeDate", "2020-01-01")
        .put("shares", 100).put("netAmountPayable", 50000)))

@Composable
internal fun GuestEntryDialog(close: () -> Unit, choose: (GuestPath) -> Unit) {
    AlertDialog(onDismissRequest = close, title = { AppText("Continue as Guest") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppText("Choose how to begin. No account or upload is required.")
            OutlinedButton(onClick = { choose(GuestPath.SAMPLE) }, modifier = Modifier.fillMaxWidth()) { AppText("Explore fictional sample") }
            DetailNote("Illustrative examples only. Sample records are separate from your personal portfolio.")
            Button(onClick = { choose(GuestPath.PERSONAL) }, modifier = Modifier.fillMaxWidth()) { AppText("Start personal portfolio") }
            DetailNote("Start with an empty workspace. Saving investments, PDF import and live markets are not available in Guest Mode yet.")
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = close) { AppText("Cancel") } })
}

@Composable
internal fun GuestContent(model: PortfolioModel, tab: String, choosePath: () -> Unit, signIn: () -> Unit) {
    val sample = model.guestPath == GuestPath.SAMPLE
    val snapshot = remember { guestSamplePortfolio() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(if (sample) "Guest · Fictional sample" else "Guest · Personal workspace", fontWeight = FontWeight.SemiBold)
                    AppText(if (sample) "These examples are fictional. They are not your investments or live market values."
                        else "Your choice is remembered on this device. Investment entry is not available in Guest Mode yet.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = signIn) { AppText("Sign in to your account") }
                }
            }
        }
        when (tab) {
            "Portfolio" -> {
                if (sample) {
                    item { DetailHeading("Sample portfolio", "Fictional examples · acquisition amounts only") }
                    item {
                        DetailAmount("Sample bond principal", snapshot.summary(Today).single().bondFaceValue, "RWF")
                    }
                    item {
                        DetailHeading("Example bond — fictional")
                        DetailValueRow("Principal", money(1000000.0, "RWF"))
                        HorizontalDivider()
                    }
                    item {
                        DetailHeading("Example company — fictional")
                        DetailValueRow("Shares", "100")
                        DetailValueRow("Acquisition cost", money(50000.0, "RWF"))
                        DetailNote("No live valuation or verified income is shown for sample records.")
                    }
                } else item {
                    EmptyState("Your personal portfolio starts here", EmptyArtwork.PORTFOLIO, compact = true,
                        description = "Investment entry is not available in Guest Mode yet. Sign in to manage saved investments today.")
                }
                item { OutlinedButton(onClick = choosePath, modifier = Modifier.fillMaxWidth()) { AppText("Change guest experience") } }
            }
            "Settings" -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailHeading("Guest settings")
                    DetailNote("Changing guest experience never transfers sample records or uploads a personal portfolio.")
                    OutlinedButton(onClick = choosePath, modifier = Modifier.fillMaxWidth()) { AppText("Change guest experience") }
                    TextButton(onClick = signIn) { AppText("Sign in to your account") }
                }
            }
            else -> item {
                EmptyState(when(tab) { "Documents" -> "Guest PDF import is not available yet"; "Calendar" -> "Guest payment calendar is not available yet"; else -> "Guest live markets are not available yet" },
                    EmptyArtwork.PORTFOLIO, compact = true,
                    description = "This feature is not available in Guest Mode yet. Sign in to use account features.")
            }
        }
    }
}

internal class GuestUnavailableEngine : InvestmentEngine {
    override var onProgress: (String) -> Unit = {}
    override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("Guest imports are not available yet.")
    override fun cancel() {}
    override fun destroy() {}
}
