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
private const val SCHEMA = 2

/** Full history, not the server's compact overview. Copies cross the boundary, not mutable cached records. */
data class GuestPortfolio(val portfolio: PortfolioSnapshot, val receipts: List<JsonRecord>, val metadata: JsonRecord, val revision: Int)
interface GuestPortfolioRepository {
    suspend fun load(): GuestPortfolio
    suspend fun saveBond(record: JsonRecord, id: String? = null): GuestPortfolio
    suspend fun saveShare(record: JsonRecord, id: String? = null): GuestPortfolio
    suspend fun deleteBond(id: String): GuestPortfolio
    suspend fun deleteShare(id: String): GuestPortfolio
    suspend fun setReceipt(purchaseId: String, dueDate: String, received: Boolean): GuestPortfolio
    /** Metadata only. Original PDF ownership/bytes are a later import phase. */
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
) : GuestPortfolioRepository {
    private val mutex = Mutex()
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(workDispatcher) { mutex.withLock { block() } }
    override suspend fun load(): GuestPortfolio = locked {
        val (root, migrated) = read()
        if (migrated) persist(root)
        view(root)
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
        val (root, _) = read() // Read fresh. A failed load never permits an overwrite.
        change(root)
        root.put("revision", root.getInt("revision") + 1)
        root.getJSONObject("metadata").let { root.put("metadata", JsonRecord(it.toString()).put("updatedAt", now())) }
        validate(root)
        persist(root) // Publish only after successful durable commit.
        view(root)
    }
    private fun empty() = JsonRecord().put("schemaVersion", SCHEMA).put("workspace", WORKSPACE).put("revision", 0)
        .put("metadata", JsonRecord().put("createdAt", now()).put("updatedAt", now()))
        .put("purchases", JsonList()).put("holdings", JsonList()).put("receipts", JsonList()).put("documents", JsonList())
    private suspend fun read(): Pair<JsonRecord, Boolean> {
        val bytes = storage.read() ?: return empty() to false
        try {
            require(bytes.size in 1..GUEST_STORE_MAX_BYTES) { "Invalid guest storage size." }
            val root = JsonRecord(bytes.decodeToString(throwOnInvalidSequence = true))
            require(root.text("workspace") == WORKSPACE) { "Wrong local workspace." }
            val version = root.getDouble("schemaVersion")
            require(version == 1.0 || version == SCHEMA.toDouble()) { "Unsupported local storage version. Update the app; data was not changed." }
            if (version == 1.0) {
                // v1 contained purchases/holdings/metadata only; no financial fields are rewritten.
                require(!root.has("receipts") && !root.has("documents"))
                root.put("schemaVersion", SCHEMA).put("receipts", JsonList()).put("documents", JsonList())
                if (!root.has("revision")) root.put("revision", 0)
            }
            validate(root)
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
