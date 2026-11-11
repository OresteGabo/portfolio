package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

internal const val BOND_APPLICATION_CATEGORY = "Bond application"

internal fun mayUploadBondApplication(user: JsonRecord?): Boolean = user?.optBoolean("isOwner") == true

/** Availability follows the real market month, independently of the selected day or bid window. */
internal fun bondApplicationAvailable(issue: JsonRecord, today: LocalDate): Boolean {
    val issuance = calendarDate(issue.text("closingBookDate")) ?: return false
    return today >= LocalDate(issuance.year, issuance.month, 1)
}

/** Owner upload controls follow the real auction month and stop after settlement. */
internal fun bondApplicationUploadAllowed(issue: JsonRecord, today: LocalDate): Boolean {
    val issuance = calendarDate(issue.text("closingBookDate")) ?: return false
    val settlement = calendarDate(issue.text("settlementDate")) ?: return false
    val monthsAhead = (issuance.year - today.year) * 12 + issuance.month.ordinal - today.month.ordinal
    return monthsAhead in 0..1 && settlement >= today
}

internal fun bondApplicationFor(issue: JsonRecord, documents: List<JsonRecord>): JsonRecord? {
    if (calendarDate(issue.text("settlementDate")) == null || calendarDate(issue.text("maturityDate")) == null || issue.optInt("tenorYears") <= 0) return null
    return documents.singleOrNull { doc ->
        val form = doc.optJSONObject("applicationForm")
        doc.text("category") == BOND_APPLICATION_CATEGORY && form != null &&
            form.text("settlementDate") == issue.text("settlementDate") &&
            form.text("maturityDate") == issue.text("maturityDate") &&
            form.optInt("tenorYears") == issue.optInt("tenorYears")
    }
}

@Composable
internal fun BondApplicationFormCard(model: PortfolioModel, issue: JsonRecord, today: LocalDate, addTemporaryPurchase: (JsonRecord) -> Unit) {
    val actions = LocalNativeActions.current
    val form = bondApplicationFor(issue, model.documents)
    val available = bondApplicationAvailable(issue, today)
    val uploadAllowed = bondApplicationUploadAllowed(issue, today)
    var draft by remember(issue.text("id")) { mutableStateOf<ByteArray?>(null) }
    var draftName by remember(issue.text("id")) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (form != null) {
            Button(enabled = available && !model.busy, onClick = { model.openDocumentPreview(form) }, modifier = Modifier.fillMaxWidth()) {
                AppIcon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                AppText("Open application form")
            }
            if (!available) AppText("Available from the issuance month", style = MaterialTheme.typography.bodySmall)
        } else if (model.applicationFormsError == null) AppText(if (model.busy) "Loading…" else "Application form not published yet", style = MaterialTheme.typography.bodySmall)
        if (calendarTemporaryPurchaseAllowed(issue, today)) {
            OutlinedButton(enabled = !model.busy, onClick = {
                if (form != null && applicationBidAllowed(form, issue, today)) model.startApplicationBid(form)
                else addTemporaryPurchase(issue)
            }, modifier = Modifier.fillMaxWidth()) { AppText("Add temporary purchase") }
        }
        model.applicationFormsError?.let {
            AppText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(enabled = !model.busy, onClick = model::refreshBondApplicationForms) { AppText("Try again") }
        }
        if (mayUploadBondApplication(model.user) && form == null && uploadAllowed) {
            OutlinedButton(enabled = !model.busy, onClick = {
                model.action {
                    require(mayUploadBondApplication(model.user)) { "Only the owner can upload bond application forms." }
                    val bytes = actions.pickPdf() ?: return@action
                    validatePdfEnvelope(bytes)
                    draftName = actions.pickedPdfName ?: "application.pdf"
                    draft = bytes
                }
            }, modifier = Modifier.fillMaxWidth()) {
                AppIcon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                AppText("Upload application form")
            }
        }
    }
    draft?.let { bytes ->
        AlertDialog(onDismissRequest = { if (!model.busy) draft = null },
            title = { AppText("Publish application form?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppText(issue.text("title"), fontWeight = FontWeight.SemiBold)
                AppText("${localized("Settlement")} · ${displayDate(issue.text("settlementDate"))}")
                AppText(draftName)
                AppText("Confirm this is the blank form for this issuance, with no personal details. One shared copy will be available to all users.")
            } },
            confirmButton = { Button(enabled = !model.busy && uploadAllowed, onClick = {
                model.publishBondApplicationForm(issue, bytes) { draft = null }
            }) { AppText("Publish shared form") } },
            dismissButton = { TextButton(enabled = !model.busy, onClick = { draft = null }) { AppText("Cancel") } })
    }
}

/** Uses the real Rwanda date, not whichever historical calendar day is selected. Early-month bids are allowed. */
internal fun applicationBidAllowed(form: JsonRecord, issue: JsonRecord, today: LocalDate): Boolean {
    val terms = form.optJSONObject("bidTerms") ?: return false
    val close = calendarDate(issue.text("closingBookDate")) ?: return false
    return bondApplicationAvailable(issue,today) && today <= close &&
        terms.text("issuanceDate") == issue.text("closingBookDate") && terms.text("settlementDate") == issue.text("settlementDate") && terms.text("maturityDate") == issue.text("maturityDate") &&
        terms.optInt("tenorYears") == issue.optInt("tenorYears") && terms.text("bondName").isNotBlank()
}

/** Early applications are allowed during the issuance month, through the published closing date. */
internal fun calendarTemporaryPurchaseAllowed(issue: JsonRecord, today: LocalDate): Boolean {
    val close = calendarDate(issue.text("closingBookDate")) ?: return false
    return today.year == close.year && today.month == close.month && today <= close &&
        calendarDate(issue.text("settlementDate")) != null && calendarDate(issue.text("maturityDate")) != null &&
        issue.text("title").isNotBlank() && issue.optInt("tenorYears") > 0
}
