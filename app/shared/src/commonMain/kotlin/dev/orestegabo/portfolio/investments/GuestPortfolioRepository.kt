@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Native implementations protect bytes at rest and replace the whole file atomically, or throw.
 * Missing is distinct from unreadable. Never return null on corruption, key loss or access denial. */
interface GuestSnapshotStorage {
    suspend fun read(): ByteArray?
    suspend fun write(bytes: ByteArray)
}
const val GUEST_STORE_MAX_BYTES = 4 * 1024 * 1024
private const val WORKSPACE = "guest-personal"
private const val SCHEMA = 3

/** Full history, not the server's compact overview. Copies cross the boundary, not mutable cached records. */
data class GuestPortfolio(val portfolio: PortfolioSnapshot, val receipts: List<JsonRecord>, val metadata: JsonRecord, val revision: Int)
interface GuestDocumentStorage {
    suspend fun read(id: String): ByteArray?
    suspend fun write(id: String, bytes: ByteArray)
    suspend fun delete(id: String)
}
internal class GuestDataDeletionException : IllegalStateException("Guest data deletion was interrupted. Retry local storage to finish removing it.")

interface GuestPortfolioRepository {
    suspend fun deleteAll(): GuestPortfolio
    suspend fun setMonthlyTarget(amount: Long): GuestPortfolio
    suspend fun importPdf(bytes: ByteArray, fileName: String, kind: String, body: JsonRecord, targetId: String? = null): GuestPortfolio
    suspend fun documentBytes(id: String): ByteArray
    suspend fun deleteDocument(id: String): GuestPortfolio
    suspend fun load(): GuestPortfolio
    suspend fun saveBond(record: JsonRecord, id: String? = null): GuestPortfolio
    suspend fun saveShare(record: JsonRecord, id: String? = null): GuestPortfolio
    suspend fun deleteBond(id: String): GuestPortfolio
    suspend fun deleteShare(id: String): GuestPortfolio
    suspend fun setReceipt(purchaseId: String, dueDate: String, received: Boolean): GuestPortfolio
    /** Metadata-only compatibility API; new imports use the atomic file + financial-record path. */
    suspend fun saveDocumentReference(record: JsonRecord, id: String? = null): GuestPortfolio
}
@Composable expect fun rememberGuestPortfolioRepository(): GuestPortfolioRepository

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
        root.put("metadata", JsonRecord(root.getJSONObject("metadata").toString()).put("deletionRequested", true))
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
    private fun id(kind: String) = "guest:$kind:${newId()}".also { require(validId(it, kind)) }
    private fun validId(id: String, kind: String) = id.startsWith("guest:$kind:") &&
        id.removePrefix("guest:$kind:").matches(Regex("[A-Za-z0-9-]{1,80}"))
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
            require(version in listOf(1.0, 2.0, SCHEMA.toDouble())) { "Unsupported local storage version. Update the app; data was not changed." }
            if (version == 1.0) {
                // v1 contained purchases/holdings/metadata only; no financial fields are rewritten.
                require(!root.has("receipts") && !root.has("documents"))
                root.put("schemaVersion", SCHEMA).put("receipts", JsonList()).put("documents", JsonList())
                if (!root.has("revision")) root.put("revision", 0)
            }
            if (version < 3) root.put("schemaVersion", SCHEMA).put("pendingFiles", JsonList())
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
    private fun view(root: JsonRecord) = GuestPortfolio(PortfolioSnapshot(root.rows("purchases"), root.rows("holdings"), root.rows("documents")),
        root.rows("receipts"), root.getJSONObject("metadata"), root.getInt("revision"))
    private fun upsert(root: JsonRecord, key: String, kind: String, body: JsonRecord, existingId: String?) {
        val rows = root.rows(key)
        val old = existingId?.let { target -> require(validId(target, kind)); rows.singleOrNull { it.text("id") == target } ?: error("Local record not found.") }
        val record = JsonRecord(body.toString()).put("id", existingId ?: id(kind))
            .put("createdAt", old?.text("createdAt") ?: now()).put("updatedAt", now())
        // Editors do not round-trip evidence fields. Keep references when editing financial terms.
        if (!body.has("documentIds") && old?.has("documentIds") == true) record.put("documentIds", old.getJSONArray("documentIds"))
        root.put(key, JsonList(rows.filterNot { it.text("id") == existingId } + record))
    }
    private fun remove(root: JsonRecord, key: String, kind: String, id: String) {
        require(validId(id, kind)); val rows = root.rows(key)
        require(rows.any { it.text("id") == id }) { "Local record not found." }
        root.put(key, JsonList(rows.filterNot { it.text("id") == id }))
    }
    private fun validate(root: JsonRecord) {
        require(root.getDouble("revision") in 0.0..2_000_000_000.0 && root.getDouble("revision") % 1 == 0.0)
        val metadata = root.getJSONObject("metadata")
        listOf("createdAt", "updatedAt").forEach { require(kotlin.time.Instant.parse(metadata.text(it)).toString().isNotEmpty()) }
        val bonds = root.rows("purchases").associateBy { it.text("id") }
        for ((key, kind) in listOf("purchases" to "bond", "holdings" to "share", "documents" to "document", "receipts" to "receipt")) {
            val array = root.getJSONArray(key); val rows = array.objects()
            require(rows.size == array.length() && rows.size <= 10_000) { "Invalid local records." }
            require(rows.map { it.text("id") }.distinct().size == rows.size) { "Duplicate local identifiers." }
            rows.forEach { row ->
                require(validId(row.text("id"), kind)) { "Account and sample records cannot enter guest storage." }
                listOf("createdAt", "updatedAt").forEach { kotlin.time.Instant.parse(row.text(it)) }
                when (key) {
                    "purchases" -> normalizeBond(row)
                    "holdings" -> normalizeShare(row)
                    "documents" -> validateDocument(row)
                    "receipts" -> {
                        val bond = bonds[row.text("purchaseId")]
                        require(bond != null && calendarDate(row.text("dueDate")) != null && row.text("currency") == bond.text("currency"))
                        require(row.text("method") in listOf("manual", "automatic"))
                        require(row.text("received") in listOf("true", "false") && row.getDouble("amount") >= 0)
                    }
                }
            }
        }
        require(root.rows("receipts").map { it.text("purchaseId") to it.text("dueDate") }.distinct().size == root.rows("receipts").size)
        val pending = root.getJSONArray("pendingFiles")
        require(pending.length() <= 100)
        for (i in 0 until pending.length()) {
            val file = pending.getString(i)
            require(file.matches(Regex("[A-Za-z0-9-]{1,80}")) && root.rows("documents").none { it.text("localFileId") == file })
        }
        val docs = root.rows("documents").map { it.text("id") }.toSet()
        (root.rows("purchases") + root.rows("holdings")).forEach { row -> if (row.has("documentIds")) row.getJSONArray("documentIds").let { refs ->
            for (i in 0 until refs.length()) require(refs.getString(i) in docs) { "Document reference not found in this workspace." }
        } }
    }
    private fun validateDocument(ref: JsonRecord) {
        require(ref.text("fileName").isNotBlank() && ref.text("fileName").length <= 240 && ref.text("mimeType") == "application/pdf")
        require(ref.text("sha256").matches(Regex("[a-f0-9]{64}")))
        require(ref.text("localFileId").matches(Regex("[A-Za-z0-9-]{1,80}"))) // Not a URL or path.
    }
}

private val bondKeys = listOf("instrumentType", "issuer", "currency", "market", "purchaseDate", "settlementDate", "bondName", "isin", "tenorYears", "faceValue", "pricePercent", "accruedInterestPaid", "feesPaid", "couponRate", "withholdingTaxRate", "maturityDate", "firstCouponDate", "couponDates", "couponFrequency", "scheduleConfidence", "broker", "accountReference", "sourceUrl", "status", "notes", "documentIds")
private val shareKeys = listOf("instrumentType", "exchange", "currency", "status", "securityName", "ticker", "isin", "tradeDate", "settlementDate", "shares", "pricePerShare", "grossConsideration", "brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee", "totalCharges", "netAmountPayable", "broker", "contractNote", "cdsAccountNo", "cdsReference", "tradeReportId", "notes", "documentIds")
private fun localFields(record: JsonRecord, keys: List<String>): JsonRecord {
    // No remote document IDs, synthetic account ownership or sample IDs can be smuggled through a form.
    for (key in listOf("ownerId", "accountId", "confirmationDocumentId", "issuanceDocumentId", "contractDocumentId", "statementDocumentId")) require(!record.has(key))
    val body = kotlinx.serialization.json.Json.parseToJsonElement(record.toString()) as kotlinx.serialization.json.JsonObject
    require(body.values.all { it.toString().length <= 32_000 }) { "Local field is too large." }
    return JsonRecord(kotlinx.serialization.json.JsonObject(body.filterKeys { it in keys }).toString())
}
private fun normalizeBond(raw: JsonRecord): JsonRecord {
    val p = localFields(raw, bondKeys)
    require(p.text("bondName").isNotBlank() && p.text("currency").matches(Regex("[A-Z]{3}")))
    require(p.text("status") in listOf("submitted", "active", "sold", "matured"))
    require(p.getDouble("faceValue") > 0 && p.getDouble("pricePercent") > 0 && p.getDouble("pricePercent") <= 1000)
    require(p.getDouble("feesPaid") >= 0 && p.getDouble("accruedInterestPaid") >= 0)
    require(calendarDate(p.text("settlementDate")) != null)
    listOf("couponRate", "withholdingTaxRate", "couponFrequency").forEach { p.getDouble(it) }
    if (p.has("couponDates")) p.getJSONArray("couponDates")
    val terms = requireNotNull(p.terms()) { "Check bond dates, rates and coupon frequency." }
    p.optJSONArray("couponDates")?.let { dates -> require(dates.length() == terms.confirmedDates.size) }
    require(p.text("scheduleConfidence") in listOf("estimated", "confirmed"))
    if (p.text("scheduleConfidence") == "confirmed") require(terms.confirmedDates.isNotEmpty())
    return p
}
private fun normalizeShare(raw: JsonRecord): JsonRecord {
    val p = localFields(raw, shareKeys)
    require(p.text("securityName").isNotBlank() && p.text("ticker").isNotBlank() && p.text("currency").matches(Regex("[A-Z]{3}")))
    require(p.text("status") in listOf("active", "sold"))
    require(calendarDate(p.text("tradeDate")) != null && calendarDate(p.text("settlementDate")) != null)
    val shares = p.getDouble("shares"); val price = p.getDouble("pricePerShare")
    require(shares > 0 && shares % 1.0 == 0.0 && price > 0)
    val fees = listOf("brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee").map { p.getDouble(it).also { v -> require(v >= 0) } }
    val cost = shareAcquisitionCost(shares, price, fees)
    // Account validation permits small document rounding differences and retains stated amounts.
    // Keep the same semantics; derive a total only when the caller did not supply it.
    fun checked(key: String, expected: Double): Double {
        val value = if (p.has(key)) p.getDouble(key) else expected
        require(value >= 0 && kotlin.math.abs(value - expected) <= 0.02) { "Share costs do not match quantities and fees." }
        p.put(key, value)
        return value
    }
    val gross = checked("grossConsideration", cost.gross)
    val charges = checked("totalCharges", cost.fees)
    checked("netAmountPayable", sumMoney(listOf(gross, charges)))
    return p
}
