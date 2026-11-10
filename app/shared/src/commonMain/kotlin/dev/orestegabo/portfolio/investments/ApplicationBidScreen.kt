@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid

@Composable
internal fun ApplicationBidScreen(model: PortfolioModel, form: JsonRecord, saved: () -> Unit) {
    var amount by rememberSaveable(form.text("id")) { mutableStateOf(ApplicationAmountStep.toString()) }
    val bidId = rememberSaveable(form.text("id")) { Uuid.random().toString() }
    val today = rememberBondCalendarToday()
    val issue = model.calendar.firstOrNull { it.text("id") == form.optJSONObject("applicationForm")?.text("calendarId") }
    val terms = form.optJSONObject("bidTerms")
    val allowed = issue != null && applicationBidAllowed(form,issue,today)
    val value = amount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 100_000 && it <= 10_000_000_000_000.0 && it % 100_000 == 0.0 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PendingBondHeader(terms?.text("bondName").orEmpty(), terms?.text("settlementDate").orEmpty(), terms?.text("maturityDate").orEmpty()) }
        item { PendingPurchaseNotice() }
        if (allowed && today.toString() == issue?.text("closingBookDate")) item { DetailNote("Confirm the closing deadline with your broker.") }
        if (!allowed) item { AppText("Bids are unavailable for this form or the subscription window has closed.", color = MaterialTheme.colorScheme.error) }
        item { ApplicationAmountSelector("Bid amount (RWF)", amount, { amount = it }, enabled = allowed && !model.busy) }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppText("From the application form", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Fact("Tenor (years)", terms?.text("tenorYears").orEmpty())
                    Fact("Issuance date", displayDate(terms?.text("issuanceDate").orEmpty()))
                }
            }
        }
        item { Button(enabled = allowed && value != null && !model.busy, modifier = Modifier.fillMaxWidth(), onClick = {
            value?.let { model.saveApplicationBid(form,it,bidId,saved) }
        }) { AppText("Save bid") } }
        item { PendingPurchaseDisclaimer() }
        model.error?.let { error -> item { AppText(error, color = MaterialTheme.colorScheme.error) } }
    }
}
