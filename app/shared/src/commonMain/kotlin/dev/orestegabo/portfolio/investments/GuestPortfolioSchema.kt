package dev.orestegabo.portfolio.investments

/** Pure validation and normalization for the on-device guest schema; no storage or network access. */
internal object GuestPortfolioSchema {
    const val WORKSPACE = "guest-personal"
    const val SCHEMA = 4

    fun validId(id: String, kind: String) = id.startsWith("guest:$kind:") &&
        id.removePrefix("guest:$kind:").matches(Regex("[A-Za-z0-9-]{1,80}"))
    fun validate(root: JsonRecord) {
        require(root.getDouble("revision") in 0.0..2_000_000_000.0 && root.getDouble("revision") % 1 == 0.0)
        val metadata = root.getJSONObject("metadata")
        listOf("createdAt", "updatedAt").forEach { require(kotlin.time.Instant.parse(metadata.text(it)).toString().isNotEmpty()) }
        val entries = metadata.rows("cashLedger")
        if (metadata.has("cashLedger")) require(metadata.getJSONArray("cashLedger").length() == entries.size) { "Invalid cash ledger." }
        require(entries.size <= 5000 && entries.map { it.text("id") }.distinct().size == entries.size)
        entries.forEach { entry ->
            require(entry.text("id").matches(Regex("[A-Za-z0-9-]{1,80}")) && calendarDate(entry.text("date")) != null && entry.getDouble("amount") >= 0)
            require(entry.text("kind") in listOf("deposit", "withdrawal", "dividend", "sale") && entry.text("currency").matches(Regex("[A-Z]{3}")))
            kotlin.time.Instant.parse(entry.text("createdAt"))
        }
        entries.filter { it.text("kind") in listOf("sale", "dividend") }.forEach { entry ->
            val lot = root.rows("holdings").singleOrNull { it.text("id") == entry.text("holdingId") }
            require(lot != null && lot.text("currency") == entry.text("currency"))
        }
        entries.filter { it.text("kind") == "sale" }.groupBy { it.text("holdingId") }.forEach { (id, sales) ->
            val original = root.rows("holdings").single { it.text("id") == id }
            require(sales.all { it.getDouble("shares") > 0 && it.getDouble("allocatedCost") >= 0 && it.getDouble("realizedGain").isFinite() })
            require(sales.sumOf { it.getDouble("shares") } <= original.getDouble("shares"))
            require(sumMoney(sales.map { it.getDouble("allocatedCost") }) <= original.getDouble("netAmountPayable"))
        }
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
    fun validateDocument(ref: JsonRecord) {
        require(ref.text("fileName").isNotBlank() && ref.text("fileName").length <= 240 && ref.text("mimeType") == "application/pdf")
        require(ref.text("sha256").matches(Regex("[a-f0-9]{64}")))
        require(ref.text("localFileId").matches(Regex("[A-Za-z0-9-]{1,80}"))) // Not a URL or path.
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
    fun normalizeBond(raw: JsonRecord): JsonRecord {
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
    fun normalizeShare(raw: JsonRecord): JsonRecord {
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
}
