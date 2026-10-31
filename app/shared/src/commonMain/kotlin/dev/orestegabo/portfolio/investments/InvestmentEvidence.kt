package dev.orestegabo.portfolio.investments

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

internal data class InvestmentEvidence(val label: String, val document: JsonRecord)

/** Follow explicit persisted provenance only; a similar ticker/date is not proof of a link. */
internal fun investmentEvidence(record: JsonRecord, documents: List<JsonRecord>): List<InvestmentEvidence> =
    listOf("confirmationDocumentId" to "View allocation confirmation", "contractDocumentId" to "View share contract",
        "issuanceDocumentId" to "View issuance results", "statementDocumentId" to "View source statement").mapNotNull { (key, label) ->
        val id = record.text(key).takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) } ?: return@mapNotNull null
        val metadata = documents.firstOrNull { it.text("id") == id }
        val document = JsonRecord(metadata?.toString() ?: "{}").put("id", id)
            .put("downloadUrl", "/api/bonds/documents/$id")
        if (document.text("label").isBlank()) document.put("label", when (key) {
            "confirmationDocumentId" -> "Bond confirmation"
            "contractDocumentId" -> "Share contract"
            "statementDocumentId" -> "BK Capital statement"
            else -> "Bond issuance results"
        })
        InvestmentEvidence(label, document)
    }.distinctBy { it.document.text("id") }

@Composable
internal fun InvestmentEvidenceActions(model: PortfolioModel, record: JsonRecord) {
    investmentEvidence(record, model.documents).forEach { evidence ->
        TextButton(enabled = !model.busy, onClick = { model.openDocumentPreview(evidence.document) }) {
            AppText(evidence.label)
        }
    }
}
