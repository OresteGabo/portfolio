package dev.orestegabo.portfolio.investments

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

internal data class InvestmentEvidence(val label: String, val document: JsonRecord)

/** Follow explicit persisted provenance only; a similar ticker/date is not proof of a link. */
internal fun investmentEvidence(record: JsonRecord, documents: List<JsonRecord>): List<InvestmentEvidence> =
    (documents.filter { it.text("id").startsWith("guest:document:") && record.hasDocument(it.text("id")) }.map { InvestmentEvidence(if (it.text("kind") == "bond") "View allocation confirmation" else "View share contract", it) } +
    listOf("confirmationDocumentId" to "View allocation confirmation", "contractDocumentId" to "View share contract",
        "applicationFormDocumentId" to "Open application form", "issuanceDocumentId" to "View issuance results", "statementDocumentId" to "View source statement").mapNotNull { (key, label) ->
        val id = record.text(key).takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) } ?: return@mapNotNull null
        val metadata = documents.firstOrNull { it.text("id") == id }
        val document = JsonRecord(metadata?.toString() ?: "{}").put("id", id)
            .put("downloadUrl", "/api/bonds/documents/$id")
        if (document.text("label").isBlank()) document.put("label", when (key) {
            "applicationFormDocumentId" -> "Bond application form"
            "confirmationDocumentId" -> "Bond confirmation"
            "contractDocumentId" -> "Share contract"
            "statementDocumentId" -> "BK Capital statement"
            else -> "Bond issuance results"
        })
        InvestmentEvidence(label, document)
    }).distinctBy { it.document.text("id") }

@Composable
internal fun InvestmentEvidenceActions(model: PortfolioModel, record: JsonRecord) {
    investmentEvidence(record, model.documents).forEach { evidence ->
        TextButton(enabled = !model.busy, onClick = { model.openDocumentPreview(evidence.document) }) {
            AppText(evidence.label)
        }
    }
}

/** Same authenticated download and native share/save behavior as the document library. */
internal suspend fun shareInvestmentDocument(api: PortfolioApi, actions: NativeActions, document: JsonRecord): Boolean {
    val path = document.text("downloadUrl")
    require(path.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+"))) { "Invalid document URL." }
    val data = api.bytes(path)
    require(data.take(5).toByteArray().decodeToString() == "%PDF-") { "The server did not return a PDF." }
    return actions.sharePdf(document.text("originalFileName").ifBlank { "investment.pdf" }, data)
}

internal suspend fun shareInvestmentDocument(model: PortfolioModel, actions: NativeActions, document: JsonRecord): Boolean {
    if (!model.isGuest) return shareInvestmentDocument(model.api, actions, document)
    val bytes = model.documentBytes(document)
    return try { actions.sharePdf(document.text("originalFileName").ifBlank { "investment.pdf" }, bytes) }
    finally { bytes.fill(0) }
}
