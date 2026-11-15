@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.WORKSPACE
import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.SCHEMA
import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.validate
import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.validateDocument
import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.normalizeBond
import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.normalizeShare
import dev.orestegabo.portfolio.investments.GuestPortfolioSchema.validId

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Single application-scoped writer. No network, account identity, price source or sample dependency. */
class LocalGuestPortfolioRepository(
    private val storage: GuestSnapshotStorage,
    private val now: () -> String = { Clock.System.now().toString() },
    private val newId: () -> String = { Uuid.random().toString() },
    private val today: () -> kotlinx.datetime.LocalDate = { Today },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val documentStorage: GuestDocumentStorage? = null,
) : GuestPortfolioRepository {
    private val mutex = Mutex()
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(workDispatcher) { mutex.withLock { block() } }
    override suspend fun load(): GuestPortfolio = locked {
        val (root, migrated) = read()
        if (migrated) persist(root)
        cleanPending(root)
        view(root)
    }
    override suspend fun setMonthlyTarget(amount: Long): GuestPortfolio = update { root ->
        require(validInvestmentTarget(amount))
        root.put("metadata", JsonRecord(root.getJSONObject("metadata").toString()).put("monthlyTarget", amount))
    }
    override suspend fun deleteAll(): GuestPortfolio = locked {
        try {
            val (root, _) = read(); cleanPending(root)
            erase(root); view(root)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { throw GuestDataDeletionException() }
    }
    /** A durable deletion intent resumes only after a valid manifest read. Batches retain v3 bounds. */
    private suspend fun erase(root: JsonRecord) {
        root.put("metadata", JsonRecord(root.getJSONObject("metadata").toString()).put("cashLedger", JsonList()).put("deletionRequested", true))
        for (key in listOf("purchases", "holdings", "receipts")) root.put(key, JsonList())
        commit(root)
        while (root.rows("documents").isNotEmpty()) {
            val docs = root.rows("documents")
            val remaining = docs.drop(100)
            root.put("documents", JsonList(remaining))
            root.put("pendingFiles", JsonList(docs.take(100).map { it.text("localFileId") }.distinct()
                .filter { file -> remaining.none { it.text("localFileId") == file } }))
            commit(root); cleanPending(root)
        }
        root.put("metadata", JsonRecord().put("createdAt", now()).put("updatedAt", now()))
        commit(root)
    }
    override suspend fun documentBytes(id: String): ByteArray = locked {
        val (root, _) = read()
        val doc = root.rows("documents").singleOrNull { it.text("id") == id } ?: error("Local PDF not found.")
        val bytes = requireNotNull(requireNotNull(documentStorage).read(doc.text("localFileId"))) { "Original PDF is unavailable." }
        try { validatePdfEnvelope(bytes); require(importPdfDigest(bytes) == doc.text("sha256")) { "Local PDF integrity check failed." }; bytes }
        catch (failure: Throwable) { bytes.fill(0); throw failure }
    }
    override suspend fun deleteDocument(id: String): GuestPortfolio = locked {
        val (root, _) = read(); cleanPending(root)
        val doc = root.rows("documents").singleOrNull { it.text("id") == id } ?: error("Local PDF not found.")
        root.put("documents", JsonList(root.rows("documents").filterNot { it.text("id") == id }))
        for (key in listOf("purchases", "holdings")) root.put(key, JsonList(root.rows(key).map { row ->
            row.optJSONArray("documentIds")?.let { refs -> row.put("documentIds", JsonList((0 until refs.length()).map { refs.getString(it) }.filterNot { it == id })) }
            row
        }))
        val stillReferenced = root.rows("documents").any { it.text("localFileId") == doc.text("localFileId") }
        root.put("pendingFiles", JsonList(if (stillReferenced) emptyList() else listOf(doc.text("localFileId"))))
        commit(root); cleanPending(root); view(root)
    }
    override suspend fun importPdf(bytes: ByteArray, fileName: String, kind: String, body: JsonRecord, targetId: String?): GuestPortfolio = locked {
        require(kind in listOf("bond", "equity")) { "Unsupported guest PDF type." }
        validatePdfEnvelope(bytes)
        val blobs = requireNotNull(documentStorage)
        val digest = importPdfDigest(bytes)
        val (root, _) = read(); cleanPending(root)
        val key = if (kind == "bond") "purchases" else "holdings"
        val normalized = if (kind == "bond") normalizeBond(body) else normalizeShare(body)
        if (normalized.text("isin").isBlank()) throw GuestPdfReviewException("Enter the ISIN before saving an imported investment.")
        val priorCopies = root.rows("documents").filter { it.text("sha256") == digest }
        require(priorCopies.size <= 1) { "Multiple stored copies require review before importing this PDF." }
        val prior = priorCopies.singleOrNull()
        val priorId = prior?.text("id")
        if (priorId != null && (root.rows("purchases") + root.rows("holdings")).any { it.hasDocument(priorId) }) {
            val retained = blobs.read(prior.text("localFileId"))
            if (retained == null) blobs.write(prior.text("localFileId"), bytes) // Explicit reviewed repair of a missing original only.
            else try { require(importPdfDigest(retained) == digest) { "Local PDF integrity check failed." } } finally { retained.fill(0) }
            return@locked view(root) // Never duplicate or overwrite the linked financial event.
        }
        val candidates = guestPdfCandidates(normalized, kind, root.rows(key))
        if (candidates.isNotEmpty() && targetId == null) throw GuestPdfReviewException("Choose the existing purchase before attaching this PDF.")
        if (targetId != null) {
            if (candidates.none { it.text("id") == targetId }) throw GuestPdfReviewException("The selected purchase does not match this PDF.")
            val existing = root.rows(key).single { it.text("id") == targetId }
            val moneyKeys = if (kind == "bond") listOf("faceValue", "pricePercent", "feesPaid", "accruedInterestPaid", "couponRate", "withholdingTaxRate") else listOf("shares", "pricePerShare", "grossConsideration", "totalCharges", "netAmountPayable")
            val dateKeys = if (kind == "bond") listOf("maturityDate", "firstCouponDate") else listOf("ticker")
            val changedTerms = dateKeys.any { existing.text(it).isNotBlank() && normalized.text(it).isNotBlank() && existing.text(it) != normalized.text(it) } ||
                kind == "bond" && existing.optDouble("couponFrequency") != normalized.optDouble("couponFrequency")
            if (changedTerms || moneyKeys.any { existing.optDouble(it) != normalized.optDouble(it) }) throw GuestPdfReviewException("The existing figures differ. Correct the purchase before linking this PDF.")
        }
        val doc = prior ?: JsonRecord().put("id", id("document")).put("localFileId", newId())
            .put("fileName", importDisplayName(fileName).take(180)).put("mimeType", "application/pdf").put("sha256", digest)
            .put("kind", kind).put("createdAt", now()).put("updatedAt", now())
        validateDocument(doc)
        val fileId = doc.text("localFileId")
        // Persist intent before bytes. Interrupted, uncommitted files are deleted on the next valid load.
        if (prior == null) {
            root.put("pendingFiles", JsonList(listOf(fileId))); persist(root)
            blobs.write(fileId, bytes)
        } else {
            val retained = blobs.read(fileId)
            if (retained == null) blobs.write(fileId, bytes)
            else try { require(importPdfDigest(retained) == digest) { "Local PDF integrity check failed." } } finally { retained.fill(0) }
        }
        if (prior == null) root.put("documents", JsonList(root.rows("documents") + doc))
        val old = root.rows(key).firstOrNull { it.text("id") == targetId }
        val refs = old?.optJSONArray("documentIds")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        // Attaching evidence must not replace existing notes, tax choices or other saved metadata.
        val record = old?.let { JsonRecord(it.toString()) } ?: normalized
        if (kind == "bond" && old?.text("status") == "submitted" && normalized.text("status") == "active") record.put("status", "active")
        record.put("documentIds", JsonList((refs + doc.text("id")).distinct()))
        upsert(root, key, if (kind == "bond") "bond" else "share", record, targetId)
        root.put("pendingFiles", JsonList())
        commit(root)
        view(root)
    }
    private suspend fun commit(root: JsonRecord) {
        root.put("revision", root.getInt("revision") + 1)
        root.put("metadata", JsonRecord(root.getJSONObject("metadata").toString()).put("updatedAt", now()))
        validate(root); persist(root)
    }
    private suspend fun cleanPending(root: JsonRecord) {
        val pending = root.getJSONArray("pendingFiles")
        if (pending.length() == 0) return
        val blobs = requireNotNull(documentStorage) { "Local PDF storage is unavailable." }
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            for (i in 0 until pending.length()) blobs.delete(pending.getString(i))
            root.put("pendingFiles", JsonList()); persist(root)
        }
    }
    override suspend fun saveBond(record: JsonRecord, id: String?) = update { root ->
        require(!record.has("id") || record.text("id") == id) { "Only local records can be edited here." }
        upsert(root, "purchases", "bond", normalizeBond(record), id)
    }
    override suspend fun saveShare(record: JsonRecord, id: String?) = update { root ->
        require(!record.has("id") || record.text("id") == id) { "Only local records can be edited here." }
        upsert(root, "holdings", "share", normalizeShare(record), id)
    }
    override suspend fun deleteBond(id: String) = update { root ->
        remove(root, "purchases", "bond", id)
        root.put("receipts", JsonList(root.rows("receipts").filterNot { it.text("purchaseId") == id }))
    }
    override suspend fun deleteShare(id: String) = update { remove(it, "holdings", "share", id) }
    override suspend fun saveDocumentReference(record: JsonRecord, id: String?) = update { root ->
        val ref = JsonRecord()
        for (key in listOf("fileName", "mimeType", "sha256", "localFileId")) ref.put(key, record.text(key))
        validateDocument(ref)
        upsert(root, "documents", "document", ref, id)
    }
    override suspend fun setReceipt(purchaseId: String, dueDate: String, received: Boolean) = update { root ->
        val bond = root.rows("purchases").singleOrNull { it.text("id") == purchaseId } ?: error("Bond not found.")
        val date = requireNotNull(calendarDate(dueDate)) { "Invalid coupon date." }
        require(bond.text("status") in listOf("active", "matured") && date <= today() && date in couponSchedule(requireNotNull(bond.terms()))) {
            "Only a due coupon for an active bond can be recorded."
        }
        val rows = root.rows("receipts")
        val existing = rows.singleOrNull { it.text("purchaseId") == purchaseId && it.text("dueDate") == dueDate }
        val receipt = JsonRecord().put("id", existing?.text("id") ?: id("receipt"))
            .put("purchaseId", purchaseId).put("dueDate", dueDate).put("received", received).put("method", "manual")
            .put("amount", existing?.optDouble("amount") ?: requireNotNull(bond.terms()).netCouponPayment())
            .put("currency", bond.text("currency")).put("createdAt", existing?.text("createdAt") ?: now()).put("updatedAt", now())
        root.put("receipts", JsonList(rows.filterNot { it.text("purchaseId") == purchaseId && it.text("dueDate") == dueDate } + receipt))
    }
    override suspend fun saveLedgerEntry(entry: JsonRecord) = update { root ->
        val prior = root.getJSONObject("metadata").rows("cashLedger")
        val id = entry.text("id")
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid transaction identity." }
        val normalized = validateLedgerEntry(entry, root.rows("holdings"), prior.filterNot { it.text("id") == id }, today())
            .put("id", id).put("createdAt", now())
        val existing = prior.singleOrNull { it.text("id") == id }
        if (existing != null) {
            require(listOf("kind", "date", "currency", "holdingId", "shares", "price", "fees", "tax", "amount", "note").all {
                !entry.has(it) || normalized.text(it) == existing.text(it)
            }) { "This transaction identity is already in use." }
        } else {
            require(prior.size < 5000) { "Local transaction storage is full." }
            root.getJSONObject("metadata").let { root.put("metadata", JsonRecord(it.toString()).put("cashLedger", JsonList(prior + normalized))) }
        }
    }
    override suspend fun deleteLedgerEntry(id: String) = update { root ->
        val metadata = root.getJSONObject("metadata")
        require(metadata.rows("cashLedger").any { it.text("id") == id }) { "Transaction not found." }
        root.put("metadata", JsonRecord(metadata.toString()).put("cashLedger", JsonList(metadata.rows("cashLedger").filterNot { it.text("id") == id })))
    }
    override suspend fun exportBackup(): ByteArray = locked {
        val (root, _) = read(); cleanPending(root)
        val docs = root.rows("documents")
        require(docs.size <= 100) { "A backup supports up to 100 PDFs." }
        var total = root.toString().encodeToByteArray().size
        val files = docs.distinctBy { it.text("localFileId") }.map { doc ->
            val bytes = requireNotNull(documentStorage?.read(doc.text("localFileId"))) { "A saved PDF is missing. Repair it before creating a backup." }
            try {
                validatePdfEnvelope(bytes); require(importPdfDigest(bytes) == doc.text("sha256"))
                total += ((bytes.size + 2) / 3) * 4
                require(total < BACKUP_MAX_BYTES - 4096) { "The backup exceeds the 48 MB limit." }
                JsonRecord().put("fileId", doc.text("localFileId")).put("content", kotlin.io.encoding.Base64.encode(bytes))
            } finally { bytes.fill(0) }
        }
        JsonRecord().put("format", "StructGuestBackup").put("version", 1).put("workspace", root).put("files", JsonList(files)).toString().encodeToByteArray()
    }
    override suspend fun restoreBackup(bytes: ByteArray): GuestPortfolio = locked {
        require(bytes.size in 1..BACKUP_MAX_BYTES)
        val archive = JsonRecord(bytes.decodeToString(throwOnInvalidSequence = true))
        require(archive.text("format") == "StructGuestBackup" && archive.optInt("version") == 1) { "Unsupported backup version." }
        val restored = archive.getJSONObject("workspace")
        require(restored.text("workspace") == WORKSPACE && restored.optInt("schemaVersion") == SCHEMA && restored.rows("pendingFiles").isEmpty()) { "Unsupported backup workspace." }
        require(restored.getJSONArray("pendingFiles").length() == 0 && !restored.getJSONObject("metadata").optBoolean("deletionRequested"))
        require(restored.toString().encodeToByteArray().size <= GUEST_STORE_MAX_BYTES) { "Backup workspace is too large." }
        validate(restored)
        val ledger = restored.getJSONObject("metadata").rows("cashLedger")
        val checkedLedger = mutableListOf<JsonRecord>()
        ledger.forEach { entry ->
            checkedLedger += validateLedgerEntry(entry, restored.rows("holdings"), checkedLedger, today())
                .put("id", entry.text("id")).put("createdAt", entry.text("createdAt"))
        }
        restored.put("metadata", JsonRecord(restored.getJSONObject("metadata").toString()).put("cashLedger", JsonList(checkedLedger)))
        val docs = restored.rows("documents"); val files = archive.rows("files")
        require(docs.size <= 100 && files.size <= 100 && files.size == archive.getJSONArray("files").length())
        require(files.map { it.text("fileId") }.distinct().size == files.size && docs.map { it.text("localFileId") }.toSet() == files.map { it.text("fileId") }.toSet())
        val decoded = mutableMapOf<String, ByteArray>()
        try {
            files.forEach { file ->
                val content = kotlin.io.encoding.Base64.decode(file.text("content"))
                decoded[file.text("fileId")] = content
                validatePdfEnvelope(content)
                require(docs.filter { it.text("localFileId") == file.text("fileId") }.all { it.text("sha256") == importPdfDigest(content) }) { "Backup PDF integrity check failed." }
            }
            val (current, _) = read(); cleanPending(current)
            val oldFiles = current.rows("documents").map { it.text("localFileId") }.distinct()
            require(oldFiles.size <= 100) { "Restore this backup into an empty guest workspace." }
            val staged = files.associate { it.text("fileId") to Uuid.random().toString() }
            current.put("pendingFiles", JsonList(staged.values.toList())); persist(current)
            try {
                staged.forEach { (original, target) -> requireNotNull(documentStorage).write(target, decoded.getValue(original)) }
                restored.put("documents", JsonList(docs.map { JsonRecord(it.toString()).put("localFileId", staged.getValue(it.text("localFileId"))) }))
                restored.put("pendingFiles", JsonList(oldFiles)).put("revision", current.getInt("revision") + 1)
                validate(restored); persist(restored)
            } catch (failure: Throwable) { runCatching { cleanPending(current) }; throw failure }
            runCatching { cleanPending(restored) } // A successful restore stays successful if old-file cleanup needs a later retry.
            view(restored)
        } finally { decoded.values.forEach { it.fill(0) } }
    }
    private fun id(kind: String) = "guest:$kind:${newId()}".also { require(validId(it, kind)) }
    private suspend fun update(change: (JsonRecord) -> Unit): GuestPortfolio = locked {
        val (root, _) = read(); cleanPending(root) // Read fresh. A failed load never permits an overwrite.
        change(root)
        root.put("revision", root.getInt("revision") + 1)
        root.getJSONObject("metadata").let { root.put("metadata", JsonRecord(it.toString()).put("updatedAt", now())) }
        validate(root)
        persist(root) // Publish only after successful durable commit.
        view(root)
    }
    private fun empty() = JsonRecord().put("schemaVersion", SCHEMA).put("workspace", WORKSPACE).put("revision", 0)
        .put("metadata", JsonRecord().put("createdAt", now()).put("updatedAt", now()))
        .put("purchases", JsonList()).put("holdings", JsonList()).put("receipts", JsonList()).put("documents", JsonList()).put("pendingFiles", JsonList())
    private suspend fun read(): Pair<JsonRecord, Boolean> {
        val bytes = storage.read() ?: return empty() to false
        try {
            require(bytes.size in 1..GUEST_STORE_MAX_BYTES) { "Invalid guest storage size." }
            val root = JsonRecord(bytes.decodeToString(throwOnInvalidSequence = true))
            require(root.text("workspace") == WORKSPACE) { "Wrong local workspace." }
            val version = root.getDouble("schemaVersion")
            require(version in listOf(1.0, 2.0, 3.0, SCHEMA.toDouble())) { "Unsupported local storage version. Update the app; data was not changed." }
            if (version == 1.0) {
                // v1 contained purchases/holdings/metadata only; no financial fields are rewritten.
                require(!root.has("receipts") && !root.has("documents"))
                root.put("schemaVersion", SCHEMA).put("receipts", JsonList()).put("documents", JsonList())
                if (!root.has("revision")) root.put("revision", 0)
            }
            if (version < 3) root.put("schemaVersion", SCHEMA).put("pendingFiles", JsonList())
            root.put("schemaVersion", SCHEMA)
            validate(root)
            if (root.getJSONObject("metadata").optBoolean("deletionRequested")) {
                try { cleanPending(root); erase(root) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { throw GuestDataDeletionException() }
            }
            return root to (version != SCHEMA.toDouble())
        } finally { bytes.fill(0) }
    }
    private suspend fun persist(root: JsonRecord) {
        val bytes = root.toString().encodeToByteArray()
        try { require(bytes.size <= GUEST_STORE_MAX_BYTES) { "Local portfolio storage is full." }; storage.write(bytes) }
        finally { bytes.fill(0) }
    }
    private fun view(root: JsonRecord) = GuestPortfolio(PortfolioSnapshot(root.rows("purchases"), shareBalances(root.rows("holdings"), root.getJSONObject("metadata").rows("cashLedger")), root.rows("documents")),
        root.rows("receipts"), root.getJSONObject("metadata"), root.getInt("revision"))
    private fun upsert(root: JsonRecord, key: String, kind: String, body: JsonRecord, existingId: String?) {
        val rows = root.rows(key)
        require(existingId == null || root.getJSONObject("metadata").rows("cashLedger").none { it.text("holdingId") == existingId }) { "Remove linked transactions before editing this purchase." }
        val old = existingId?.let { target -> require(validId(target, kind)); rows.singleOrNull { it.text("id") == target } ?: error("Local record not found.") }
        val record = JsonRecord(body.toString()).put("id", existingId ?: id(kind))
            .put("createdAt", old?.text("createdAt") ?: now()).put("updatedAt", now())
        // Editors do not round-trip evidence fields. Keep references when editing financial terms.
        if (!body.has("documentIds") && old?.has("documentIds") == true) record.put("documentIds", old.getJSONArray("documentIds"))
        root.put(key, JsonList(rows.filterNot { it.text("id") == existingId } + record))
    }
    private fun remove(root: JsonRecord, key: String, kind: String, id: String) {
        require(validId(id, kind));
        require(root.getJSONObject("metadata").rows("cashLedger").none { it.text("holdingId") == id }) { "Remove linked transactions before deleting this purchase." }
        val rows = root.rows(key)
        require(rows.any { it.text("id") == id }) { "Local record not found." }
        root.put(key, JsonList(rows.filterNot { it.text("id") == id }))
    }

}
