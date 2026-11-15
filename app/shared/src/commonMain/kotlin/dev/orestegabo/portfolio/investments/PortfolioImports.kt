package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/** Account-facing effects needed by imports; session/job ownership stays in PortfolioModel. */
internal interface PortfolioImportHost {
    val api: PortfolioDataApi
    var portfolio: PortfolioSnapshot
    val documents: List<JsonRecord>
    val shareOrders: List<JsonRecord>
    val statementItems: List<JsonRecord>
    val canPublishPublicData: Boolean
    fun action(block: suspend () -> Unit)
    suspend fun refreshAfterWrite(success: String)
}

/** Owns a single account's import journal, selected PDF, review and resumable upload. */
internal class PortfolioImports(
    settings: ServerSettings,
    private val importWorkDispatcher: CoroutineDispatcher,
    private val host: PortfolioImportHost,
) {
    private val api get() = host.api
    private val canPublishPublicData get() = host.canPublishPublicData
    private val shareOrders get() = host.shareOrders
    private val statementItems get() = host.statementItems
    private val documents get() = host.documents
    private var portfolio: PortfolioSnapshot
        get() = host.portfolio
        set(value) { host.portfolio = value }

    private val importJournal = ImportJournal(settings)
    internal val importHistory get() = importJournal.entries
    internal val importHistoryStorageFailed get() = importJournal.persistenceFailed
    internal var activeImportId by mutableStateOf<String?>(null)
        private set
    // Selection events, rather than receipt IDs, route every upload source into chat.
    // A dismissed picker emits nothing; retrying an existing receipt emits a new event.
    internal var importSelectionRevision by mutableStateOf(0)
        private set
    private var retryPdf: ByteArray? = null
    private val savedPdfDigests = mutableMapOf<String, String>()
    private var retryBondOnly = false
    private var retryStatement: JsonRecord? = null
    internal fun activeImportReceipt() = importJournal.find(activeImportId)
    private fun updateImport(change: (ImportReceipt) -> ImportReceipt) { activeImportId?.let { importJournal.update(it, change) } }
    private fun startSavingImport() { updateImport { it.copy(state = ImportState.SAVING, error = "") } }
    private fun finishImport(outcome: ImportOutcome, purchaseId: String = "", recorded: Int = 0, missing: Int = 0) {
        updateImport { it.copy(state = ImportState.SAVED, error = "", outcome = outcome,
            documentId = uploadedDocument ?: it.documentId, purchaseId = purchaseId, recorded = recorded, missing = missing) }
    }
    fun reconcileImportHistory() {
        importHistory.filter { it.state !in listOf(ImportState.SAVED, ImportState.EXISTS) && it.documentId.isNotBlank() }.forEach { entry ->
            val purchase = portfolio.purchases.firstOrNull {
                it.text("status") != "submitted" && (entry.kind == "bond" && it.text("confirmationDocumentId") == entry.documentId ||
                    entry.kind == "bond-issuance" && entry.targetId.isNotBlank() && it.text("id") == entry.targetId &&
                    it.text("issuanceDocumentId") == entry.documentId && it.text("confirmationMethod") == "manual")
            }
            if (purchase != null) importJournal.update(entry.id) { it.copy(state = ImportState.SAVED, outcome = ImportOutcome.PURCHASE, error = "", purchaseId = purchase.text("id")) }
            else if (entry.kind == "bond-issuance" && entry.targetId.isBlank() && portfolio.documents.any { it.text("id") == entry.documentId })
                importJournal.update(entry.id) { it.copy(state = ImportState.SAVED, outcome = ImportOutcome.ISSUANCE, error = "") }
        }
    }
    private var importedPdf: ByteArray? = null
    private var importedReview: JsonRecord? = null
    internal fun currentImportReview(): JsonRecord? = importedReview
    private var pdfUploadId = ""
    private var uploadedDocument: String? = null
    var pdfImportProgress by mutableStateOf<PdfImportProgress?>(null)
        private set
    fun importPurchasePdf(actions: NativeActions, engine: InvestmentEngine, bondOnly: Boolean = false,
        expectedStatement: JsonRecord? = null, targetPurchaseId: String? = null, retryEntryId: String? = null,
        completed: (JsonRecord) -> Unit) = host.action {
        require(importedReview == null || retryEntryId == activeImportId) { "Finish or cancel the current import first." }
        val previous = retryEntryId?.let { importJournal.find(it) }
        if (retryEntryId != activeImportId) retryPdf = null
        require(previous?.state !in listOf(ImportState.SAVED, ImportState.EXISTS)) { "This import is already complete." }
        importedPdf = null; importedReview = null; uploadedDocument = null
        retryBondOnly = bondOnly; retryStatement = expectedStatement
        var selected = false
        try {
            readPurchasePdf(actions, engine, bondOnly, expectedStatement,
                openedPdf = { bytes ->
                    val digest = withContext(importWorkDispatcher) { importPdfDigest(bytes) }
                    require(previous == null || previous.digest.isBlank() || previous.digest == digest) {
                        "This is a different PDF. Start a new import for this file."
                    }
                    retryPdf = bytes
                    updateImport { it.copy(fileName = actions.pickedPdfName?.let(::importDisplayName) ?: it.fileName, digest = digest) }
                }, selectedPdf = { importedPdf = it }, progress = { progress ->
                    if (!selected && progress?.stage == PdfImportStage.OPENING) {
                        selected = true
                        val entry = previous ?: importJournal.begin(actions.pickedPdfName ?: "Investment PDF", targetPurchaseId.orEmpty(), expectedStatement?.text("id").orEmpty())
                        activeImportId = entry.id
                        importSelectionRevision++
                        pdfUploadId = entry.id
                        uploadedDocument = entry.documentId.takeIf(String::isNotBlank)
                        updateImport { it.copy(state = ImportState.READING, error = "") }
                    }
                    pdfImportProgress = progress
                })?.let { review ->
                require(review.text("kind") != "bond-issuance" || canPublishPublicData) { PUBLIC_DATA_PERMISSION_ERROR }
                if (review.text("kind") == "statement") {
                    retryBondOnly = false; retryStatement = null
                    updateImport { it.copy(targetId = "", statementId = "") }
                }
                if (targetPurchaseId?.startsWith("share-order:") == true) {
                    require(review.text("kind") == "equity") { "Choose the share purchase contract for this order." }
                    val order = shareOrders.singleOrNull { it.text("id") == targetPurchaseId.removePrefix("share-order:") }
                        ?: error("This order is no longer pending. Refresh your portfolio.")
                    pendingShareContractInput(review, order) // Validate before offering confirmation.
                    review.put("pendingOrderId", order.text("id")).put("pendingOrderVersion", order.optInt("version"))
                }
                updateImport { it.copy(kind = review.text("kind")) }
                pdfImportProgress = PdfImportProgress(PdfImportStage.CHECKING)
                val existing = findExistingImportPdf(review)
                val importTarget = targetPurchaseId.takeUnless { review.text("kind") == "statement" }
                val alreadySaved = !review.has("pendingOrderId") && existing != null && showExistingImport(existing, review,
                    requireComplete = true, targetId = importTarget)
                if (!alreadySaved) {
                    if (existing != null) {
                        uploadedDocument = existing.text("id")
                        updateImport { it.copy(documentId = existing.text("id")) }
                    }
                    importedReview = review
                    updateImport { it.copy(state = ImportState.REVIEW, error = "") }
                    completed(review)
                }
            }
        } finally { pdfImportProgress = null }
    }

    private suspend fun findExistingImportPdf(review: JsonRecord): JsonRecord? {
        val bytes = importedPdf ?: retryPdf ?: return null
        val digest = activeImportReceipt()?.digest?.takeIf(String::isNotBlank)
            ?: withContext(importWorkDispatcher) { importPdfDigest(bytes) }
        // A fresh account-authorized list prevents a stale local receipt from reviving a deleted file.
        val current = api.request("/api/bonds/documents").rows("documents")
        portfolio = portfolio.copy(documents = current)
        sharedIssuanceDocument(review, current)?.let { return it }
        val knownIds = importHistory.filter { it.digest == digest && it.documentId.isNotBlank() }.map { it.documentId }.toSet()
        current.firstOrNull { it.text("id") in knownIds }?.let { return it }
        val candidates = current.filter { duplicatePdfCandidate(it, review, activeImportReceipt()?.fileName.orEmpty()) }
        for (document in candidates) {
            val id = document.text("id")
            require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid document URL." }
            val existingDigest = savedPdfDigests[id] ?: run {
                val data = try { api.bytes("/api/bonds/documents/$id") }
                catch (failure: ApiException) { if (failure.status == 404) continue else throw failure }
                require(data.size in 1..MAX_PDF_BYTES && data.take(5).toByteArray().decodeToString() == "%PDF-") { "The server did not return a PDF." }
                withContext(importWorkDispatcher) { importPdfDigest(data) }.also { savedPdfDigests[id] = it }
            }
            if (existingDigest == digest) return document
        }
        return null
    }

    private suspend fun showExistingImport(document: JsonRecord, review: JsonRecord, requireComplete: Boolean = false, targetId: String? = null): Boolean {
        val id = document.text("id")
        val kind = review.text("kind")
        val prior = importHistory.firstOrNull { it.documentId == id && it.kind == kind && it.state == ImportState.SAVED }
        val records = try {
            when (kind) {
                "bond", "bond-issuance" -> api.request("/api/bonds/purchases").rows("purchases").also {
                    portfolio = portfolio.copy(purchases = it)
                }
                "equity" -> api.request("/api/bonds/equities").rows("holdings").also {
                    portfolio = portfolio.copy(holdings = it)
                }
                else -> emptyList()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (requireComplete || failure is ApiException && failure.status == 401) throw failure
            emptyList()
        }
        val purchase = completedImportPurchase(document, review, records, targetId)
        val statementRows = if (kind == "statement") try {
            api.request("/api/bonds/statement-items?documentId=$id").rows("items").filter { row -> row.rows("sources").any { it.text("documentId") == id } }
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: ApiException) {
            if (failure.status in listOf(404, 405)) emptyList() else throw failure
        } else emptyList()
        val outcome = when {
            purchase != null -> ImportOutcome.PURCHASE
            kind == "bond-issuance" && targetId.isNullOrBlank() && (document.has("issuance") || prior?.outcome == ImportOutcome.ISSUANCE) -> ImportOutcome.ISSUANCE
            kind == "statement" && (statementRows.isNotEmpty() || prior?.outcome == ImportOutcome.STATEMENT) -> ImportOutcome.STATEMENT
            else -> null
        }
        if ((requireComplete || kind == "statement") && outcome == null) return false
        updateImport { it.copy(state = ImportState.EXISTS, kind = kind, error = "",
            documentId = id, purchaseId = purchase?.text("id").orEmpty(), outcome = outcome,
            recorded = if (outcome == ImportOutcome.STATEMENT) if (statementRows.isEmpty()) prior?.recorded ?: 0 else statementRows.count { it.text("state") == "recorded" } else 0,
            missing = if (outcome == ImportOutcome.STATEMENT) if (statementRows.isEmpty()) prior?.missing ?: 0 else statementRows.count { it.text("state") in listOf("missing", "review-required") } else 0) }
        importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
        return true
    }

    /** A concurrent import may have saved this file since the review was opened. */
    private suspend fun checkExistingBeforeSave(review: JsonRecord, completed: () -> Unit, targetId: String? = null): Boolean {
        val requestedTarget = targetId ?: activeImportReceipt()?.targetId?.takeIf(String::isNotBlank)
        if (uploadedDocument != null) {
            val existing = documents.firstOrNull { it.text("id") == uploadedDocument }
            if (existing != null && showExistingImport(existing, review, requireComplete = true,
                    targetId = requestedTarget)) {
                completed(); return true
            }
            return false
        }
        pdfImportProgress = PdfImportProgress(PdfImportStage.CHECKING)
        try {
            val existing = findExistingImportPdf(review) ?: return false
            if (showExistingImport(existing, review, requireComplete = true, targetId = requestedTarget)) {
                completed(); return true
            }
            // The file can outlive its investment. Reuse the bytes, but still save the missing record.
            uploadedDocument = existing.text("id")
            updateImport { it.copy(documentId = existing.text("id")) }
            return false
        } finally { pdfImportProgress = null }
    }

    internal fun retryImport(id: String, actions: NativeActions, engine: InvestmentEngine, completed: (JsonRecord) -> Unit) {
        val entry = importJournal.find(id) ?: return
        val same = id == activeImportId && retryPdf != null
        val target = entry.targetId.takeIf(String::isNotBlank)
        val statement = if (same) retryStatement else statementItems.firstOrNull { it.text("id") == entry.statementId }
        val picker = if (same) ReceivedPdfActions(actions, retryPdf!!, entry.fileName) else actions
        importPurchasePdf(picker, engine, bondOnly = if (same) retryBondOnly else target != null && !target.startsWith("share-order:"),
            expectedStatement = statement, targetPurchaseId = target, retryEntryId = id, completed = completed)
    }

    internal fun cancelImportReview(review: JsonRecord) { discardImportedPdf(review) }

    fun discardImportedPdf(review: JsonRecord) {
        if (importedReview === review) {
            if (activeImportReceipt()?.state !in listOf(ImportState.SAVED, ImportState.EXISTS)) updateImport { it.copy(state = ImportState.CANCELLED, error = "") }
            importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
        }
    }

    fun saveImportedPurchase(review: JsonRecord, purchase: JsonRecord, id: String?, completed: () -> Unit) = host.action {
        require(importedReview === review && importedPdf != null) { "Choose the PDF again before confirming." }
        startSavingImport()
        val pendingOrderId = review.text("pendingOrderId")
        if (pendingOrderId.isNotBlank()) {
            val order = shareOrders.singleOrNull { it.text("id") == pendingOrderId }
                ?: error("This order is no longer pending. Refresh your portfolio.")
            pendingShareContractInput(review, order)
        }
        if (pendingOrderId.isBlank() && checkExistingBeforeSave(review, completed, targetId = id)) return@action
        val bond = review.text("kind") == "bond"
        val reusingDocument = uploadedDocument != null
        try {
            if (uploadedDocument == null) {
                val pdf = importedPdf!!
                val chunkSize = 512 * 1024
                val count = (pdf.size + chunkSize - 1) / chunkSize
                for (index in 0 until count) {
                    pdfImportProgress = PdfImportProgress(PdfImportStage.UPLOADING, "${index + 1} / $count")
                    val metadata = JsonRecord().put("label", if (bond) "Bond confirmation" else "Share purchase contract")
                        .put("instrumentName", purchase.text(if (bond) "bondName" else "securityName"))
                        .put("issuer", purchase.text(if (bond) "issuer" else "broker"))
                        .put("documentDate", purchase.text(if (bond) "purchaseDate" else "tradeDate"))
                        .put("category", if (bond) "Bond confirmation" else "Share contract")
                    val result = api.request("/api/bonds/documents", "POST", JsonRecord()
                        .put("uploadId", pdfUploadId).put("index", index).put("count", count)
                        .put("content", Base64.encode(pdf.copyOfRange(index * chunkSize, minOf(pdf.size, (index + 1) * chunkSize))))
                        .put("metadata", metadata))
                    if (index == count - 1) {
                        uploadedDocument = result.text("documentId").takeIf { it.isNotBlank() }
                        updateImport { it.copy(documentId = uploadedDocument.orEmpty()) }
                    }
                }
                require(uploadedDocument != null) { "The PDF could not be saved. Try again." }
            }
            pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
            require(bond || !reusingDocument || purchase.text("contractNote").isNotBlank()) {
                "This import may already be saved. Check your portfolio before trying again."
            }
            val storedDocument = uploadedDocument.orEmpty()
            // Resolve a completed write before retrying a new-record POST after a lost response.
            val existingMatches = if (bond && id == null) api.request("/api/bonds/purchases").rows("purchases")
                .filter { it.text("confirmationDocumentId") == storedDocument && it.text("status") != "submitted" }
                else if (!bond && purchase.text("contractNote").isNotBlank()) api.request("/api/bonds/equities").rows("holdings")
                    .filter { it.text("contractNote") == purchase.text("contractNote") && it.text("ticker") == purchase.text("ticker") && it.text("tradeDate") == purchase.text("tradeDate") }
                else emptyList()
            require(existingMatches.size <= 1) { "This import may already be saved. Check your portfolio before trying again." }
            val existing = existingMatches.singleOrNull()
            require(bond || existing == null || existing.text("broker").equals(purchase.text("broker"), true) &&
                listOf("shares", "pricePerShare", "netAmountPayable").all { existing.optDouble(it) == purchase.optDouble(it) }) {
                "The saved purchase differs from this PDF. Review your portfolio before importing again."
            }
            val response = if (pendingOrderId.isNotBlank()) {
                api.request("/api/bonds/share-orders/$pendingOrderId/confirm", "POST", JsonRecord()
                    .put("confirmed", true).put("version", review.optInt("pendingOrderVersion"))
                    .put("purchase", JsonRecord(purchase.toString()).put("contractDocumentId", uploadedDocument)))
            } else if (existing != null && existing.text("contractDocumentId").isNotBlank()) JsonRecord().put("id", existing.text("id")) else api.request(if (bond) "/api/bonds/purchases" + (id?.let { "/$it" } ?: "") else "/api/bonds/equities",
                if (bond && id != null) "PUT" else "POST", JsonRecord(purchase.toString()).apply { if(bond)put("confirmationDocumentId",uploadedDocument) else put("contractDocumentId",uploadedDocument) })
            val purchaseId = id ?: response.optJSONObject(if (bond) "purchase" else "holding")?.text("id")
                ?: response.text("id")
            finishImport(ImportOutcome.PURCHASE, purchaseId)
            importedPdf = null; importedReview = null; uploadedDocument = null; retryPdf = null
            completed()
            host.refreshAfterWrite("Purchase and PDF saved.")
        } finally { pdfImportProgress = null }
    }

    internal fun saveImportedStatement(review: JsonRecord, completed: () -> Unit) = host.action {
        require(importedReview === review && importedPdf != null && review.text("kind") == "statement") { "Choose the PDF again before saving." }
        val rows = review.rows("rows").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        val movements = review.rows("movements").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        require(rows.size + movements.size in 1..500) { "No records were found in this statement." }
        startSavingImport()
        if (checkExistingBeforeSave(review, completed)) return@action
        try {
            if (uploadedDocument == null) {
                val pdf = importedPdf!!
                val chunkSize = 512 * 1024
                val count = (pdf.size + chunkSize - 1) / chunkSize
                for (index in 0 until count) {
                    pdfImportProgress = PdfImportProgress(PdfImportStage.UPLOADING, "${index + 1} / $count")
                    val result = api.request("/api/bonds/documents", "POST", JsonRecord().put("uploadId", pdfUploadId)
                        .put("index", index).put("count", count)
                        .put("content", Base64.encode(pdf.copyOfRange(index * chunkSize, minOf(pdf.size, (index + 1) * chunkSize))))
                        .put("metadata", JsonRecord().put("label", "BK Capital statement").put("category", "Statement")
                            .put("instrumentName", "Investment account statement").put("issuer", "BK Capital").put("documentDate", review.text("toDate"))))
                    if (index == count - 1) {
                        uploadedDocument = result.text("documentId").takeIf(String::isNotBlank)
                        updateImport { it.copy(documentId = uploadedDocument.orEmpty()) }
                    }
                }
                require(uploadedDocument != null) { "The PDF could not be saved. Try again." }
            }
            pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
            val result = api.request("/api/bonds/statement-items", "POST", JsonRecord().put("items", JsonList(rows)).put("movements", JsonList(movements)).put("documentId", uploadedDocument))
            val checked = result.rows("rows")
            require(checked.isNotEmpty()) { "Statement results could not be verified. Try again." }
            finishImport(ImportOutcome.STATEMENT, recorded = checked.count { it.text("state") == "recorded" }, missing = checked.count { it.text("state") in listOf("missing", "review-required") })
            discardImportedPdf(review)
            completed()
            host.refreshAfterWrite("Statement PDF saved and records checked.")
        } finally { pdfImportProgress = null }
    }

    fun saveImportedIssuance(review: JsonRecord, confirmationTarget: JsonRecord? = null, sharingConfirmed: Boolean = false, completed: () -> Unit) = host.action {
        require(canPublishPublicData) { PUBLIC_DATA_PERMISSION_ERROR }
        require(importedReview === review && importedPdf != null) { "Choose the PDF again before saving." }
        val issuance = importedIssuanceInput(review)
        val expectedConfirmation = confirmationTarget?.let { pendingIssuanceConfirmation(review, it) }
        startSavingImport()
        if (checkExistingBeforeSave(review, completed, targetId = confirmationTarget?.text("id"))) return@action
        try {
            require(uploadedDocument != null || sharingConfirmed) { "Confirm that this report is public before sharing it." }
            val pdf = importedPdf!!
            val chunkSize = 512 * 1024
            val count = (pdf.size + chunkSize - 1) / chunkSize
            val metadata = JsonRecord().put("visibility","shared").put("label", "Bond issuance results").put("category", "Bond issuance")
                .put("instrumentName", issuance.text("bondName")).put("issuer", issuance.text("issuer"))
                .put("documentDate", issuance.text("issuanceDate")).put("issuance", issuance)
            var duplicate = false
            if (uploadedDocument == null) for (index in 0 until count) {
                pdfImportProgress = PdfImportProgress(PdfImportStage.UPLOADING, "${index + 1} / $count")
                val response = api.request("/api/bonds/documents", "POST", JsonRecord()
                    .put("uploadId", pdfUploadId).put("index", index).put("count", count)
                    .put("content", Base64.encode(pdf.copyOfRange(index * chunkSize, minOf(pdf.size, (index + 1) * chunkSize))))
                    .put("metadata", metadata))
                if (response.text("documentId").isNotBlank()) {
                    uploadedDocument = response.text("documentId").takeIf { it.isNotBlank() }
                    require(uploadedDocument != null) { "The issuance report could not be saved. Try again." }
                    updateImport { it.copy(documentId = uploadedDocument.orEmpty()) }
                    duplicate = response.optBoolean("duplicate")
                    break // A globally saved report can be reused after the first chunk.
                }
            }
            if (confirmationTarget != null && expectedConfirmation != null) {
                pdfImportProgress = PdfImportProgress(PdfImportStage.SAVING)
                val current = api.request("/api/bonds/purchases").rows("purchases")
                    .singleOrNull { it.text("id") == confirmationTarget.text("id") }
                    ?: error("This pending purchase is no longer available. Refresh your portfolio.")
                require(current.text("issuanceDocumentId") == uploadedDocument) { "This PDF does not match the pending bond or applied amount." }
                // A retry after a lost success response must not apply the purchase twice.
                if (current.text("status") == "active") {
                    require(current.text("confirmationMethod") == "manual" &&
                        listOf("faceValue", "feesPaid", "pricePercent", "couponRate").all {
                            current.optDouble(it, Double.NaN) == expectedConfirmation.optDouble(it, Double.NaN)
                        }) { "The purchase changed. Review its latest details before confirming." }
                } else {
                    val latest = pendingIssuanceConfirmation(review, current)
                    require(current.optBoolean("canConfirmWithoutPdf")) { "Use your personal PDF to confirm the final allocation and price." }
                    require(listOf("faceValue", "feesPaid", "accruedInterestPaid", "amountInvested").all {
                            latest.optDouble(it) == expectedConfirmation.optDouble(it)
                        }) { "The purchase changed. Review its latest details before confirming." }
                    api.request("/api/bonds/purchases/${current.text("id")}/confirm", "POST",
                        JsonRecord().put("confirmed", true).put("issuanceDocumentId", uploadedDocument))
                }
            }
            finishImport(if (confirmationTarget != null) ImportOutcome.PURCHASE else ImportOutcome.ISSUANCE, confirmationTarget?.text("id").orEmpty())
            if (duplicate && confirmationTarget == null) updateImport { it.copy(state = ImportState.EXISTS) }
            discardImportedPdf(review)
            completed()
            host.refreshAfterWrite(if (confirmationTarget != null) "PDF saved and purchase confirmed." else if (duplicate) "This issuance is already saved. No extra copy was stored." else "Bond issuance and PDF saved.")
        } finally { pdfImportProgress = null }
    }

    fun attach(account: String?) = importJournal.attach(account)

    fun recordCancellation() {
        if (activeImportReceipt()?.state in listOf(ImportState.READING, ImportState.SAVING))
            updateImport { it.copy(state = ImportState.CANCELLED, error = "") }
    }

    fun recordFailure(message: String) {
        if (activeImportReceipt()?.state in listOf(ImportState.READING, ImportState.SAVING))
            updateImport { it.copy(state = ImportState.FAILED, error = message) }
    }

    fun release() { importedPdf = null; importedReview = null; retryPdf = null }

    fun clear() {
        release()
        uploadedDocument = null
        activeImportId = null
        importJournal.detach()
        savedPdfDigests.clear()
        pdfImportProgress = null
    }
}
