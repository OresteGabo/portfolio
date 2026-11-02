package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

internal data class MigrationAccount(val origin: String,val id: String,val label: String)
internal data class MigrationRow(val id: String,val kind: String,val title: String,val record: JsonRecord,val parent: String? = null)
internal data class MigrationOutcome(val status: String,val target: String = "",val message: String = "")
internal interface GuestMigrationRemote {
    suspend fun status(account: MigrationAccount): JsonRecord
    suspend fun document(account: MigrationAccount,row: MigrationRow,date: String,bytes: ByteArray,progress: (Int,Int)->Unit): JsonRecord
    suspend fun item(account: MigrationAccount,row: MigrationRow,record: JsonRecord,documents: List<String>): JsonRecord
}
internal class ApiGuestMigrationRemote(private val api: PortfolioApi): GuestMigrationRemote {
    private fun checkAccount(account: MigrationAccount) { check(api.origin == account.origin) }
    override suspend fun status(account: MigrationAccount): JsonRecord {
        checkAccount(account)
        return api.request("/api/bonds/guest-migration/status?accountId=${account.id.encodeURLParameter()}")
    }
    override suspend fun document(account: MigrationAccount,row: MigrationRow,date: String,bytes: ByteArray,progress: (Int,Int)->Unit): JsonRecord {
        checkAccount(account)
        val category=if(row.record.text("kind")=="bond") "Bond confirmation" else "Share contract"
        val digest=importPdfDigest(bytes)
        check(digest==row.record.text("sha256"))
        val upload=importPdfDigest("${account.origin}|${account.id}|${row.id}|$date|$digest".encodeToByteArray()).take(32)
        val size=512*1024;val count=(bytes.size+size-1)/size
        for(index in 0 until count) {
            progress(index+1,count)
            val result=api.request("/api/bonds/guest-migration/documents","POST",JsonRecord().put("accountId",account.id)
                .put("confirmed",true).put("sourceId",row.id).put("sha256",digest).put("uploadId",upload).put("index",index).put("count",count)
                .put("content",Base64.encode(bytes.copyOfRange(index*size,minOf(bytes.size,(index+1)*size))))
                .put("metadata",JsonRecord().put("category",category).put("label",category).put("instrumentName","").put("issuer","").put("documentDate",date)))
            if(result.text("documentId").isNotBlank()) return result
        }
        error("Missing document acknowledgment")
    }
    override suspend fun item(account: MigrationAccount,row: MigrationRow,record: JsonRecord,documents: List<String>): JsonRecord {
        checkAccount(account)
        return api.request("/api/bonds/guest-migration/items","POST",JsonRecord().put("accountId",account.id).put("confirmed",true)
            .put("sourceId",row.id).put("kind",row.kind).put("record",record).put("documentIds",JsonList(documents)))
    }
}

/** Only explicit prepare/apply calls; never called by auth restoration, login or account synchronization. */
internal class GuestMigration(
    private val repository: GuestPortfolioRepository,private val remote: GuestMigrationRemote,
    val account: MigrationAccount,private val active: () -> Boolean,
) {
    var rows by mutableStateOf<List<MigrationRow>>(emptyList()); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    var dates by mutableStateOf<Map<String,String>>(emptyMap()); private set
    var results by mutableStateOf<Map<String,MigrationOutcome>>(emptyMap()); private set
    var previous by mutableStateOf<Set<String>>(emptySet()); private set
    var consent by mutableStateOf(false)
    var preparing by mutableStateOf(false); private set
    var prepared by mutableStateOf(false); private set
    var running by mutableStateOf(false); private set
    var failure by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf<String?>(null); private set
    var preview by mutableStateOf<DocumentPreview?>(null); private set
    private var closed = false
    private var revision: Int? = null
    private var priorBonds = emptySet<String>()
    private fun authorized() { check(!closed && active()) { "Account changed or review closed" } }
    suspend fun prepare() {
        check(!running)
        preparing=true;failure=null;prepared=false
        try {
            authorized()
            val history=remote.status(account);authorized()
            check(history.text("accountId")==account.id)
            val snapshot=repository.load();authorized()
            val records=snapshot.portfolio
            rows=records.purchases.map { MigrationRow(it.text("id"),"bond",it.text("bondName"),it) } +
                records.holdings.map { MigrationRow(it.text("id"),"equity",it.text("securityName"),it) } +
                snapshot.receipts.map { receipt ->
                    val bondName=records.purchases.firstOrNull { it.text("id")==receipt.text("purchaseId") }?.text("bondName").orEmpty()
                    MigrationRow(receipt.text("id"),"receipt","$bondName · ${receipt.text("dueDate")}",receipt,receipt.text("purchaseId"))
                } +
                records.documents.map { MigrationRow(it.text("id"),"document",it.text("fileName"),it) }
            check(rows.size<=40000 && rows.all { it.id.matches(Regex("guest:(bond|share|receipt|document):[A-Za-z0-9-]{1,80}")) })
            val old=history.rows("items")
            previous=old.map { it.text("sourceId") }.toSet()
            priorBonds=old.filter { it.text("kind")=="bond" }.map { it.text("sourceId") }.toSet()
            dates=rows.filter { it.kind=="document" }.associate { row -> row.id to row.record.text("documentDate").ifBlank { old.firstOrNull { it.text("sourceId")==row.id }?.text("documentDate").orEmpty() } }
            revision=snapshot.revision;selected=emptySet();results=emptyMap();consent=false;prepared=true
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failed: Exception) { failure=if(failed is ApiException && failed.status==404) "The account service needs the migration update before data can be transferred." else "Could not prepare the transfer. Check your signed-in account and retry. No guest data was uploaded." }
        finally { preparing=false }
    }
    fun choose(id: String,value: Boolean) {
        if(running || !prepared) return
        require(rows.any { it.id==id })
        selected=if(value) selected+id else selected-id
        if(!value && id !in priorBonds) selected=selected-rows.filter { it.parent==id }.map { it.id }.toSet()
        consent=false
    }
    fun date(id: String,value: String) { if(!running) { dates=dates+(id to value.take(10));consent=false } }
    fun selectable(row: MigrationRow): Boolean = row.kind!="receipt" || row.parent in selected || row.parent in priorBonds
    fun documentTypeSupported(row: MigrationRow) = row.record.text("kind") in listOf("bond","equity")
    suspend fun showOriginal(row: MigrationRow) {
        authorized();closePreview()
        val bytes=repository.documentBytes(row.id)
        try { authorized();preview=DocumentPreview(JsonRecord(),"Local PDF",row.title,bytes) }
        catch(failed: Exception) { bytes.fill(0);throw failed }
    }
    fun closePreview() { preview?.bytes?.fill(0);preview=null }
    private fun outcome(row: MigrationRow,result: JsonRecord): MigrationOutcome {
        check(result.text("sourceId")==row.id && result.text("kind")==row.kind &&
            result.text("targetId").matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) &&
            result.text("status") in listOf("created","matched","already-transferred"))
        return MigrationOutcome(result.text("status"),result.text("targetId"))
    }
    suspend fun transfer() {
        if(running) return
        check(prepared && consent && selected.isNotEmpty()) { "Review and consent first" }
        authorized()
        val selection=selected.toSet();val documentDates=dates.toMap()
        check(rows.filter { it.id in selection && it.kind=="document" }.all { documentTypeSupported(it) && calendarDate(documentDates[it.id].orEmpty())!=null }) { "Verify selected document dates" }
        check(rows.filter { it.id in selection }.all(::selectable)) { "Select the parent bond first" }
        running=true;consent=false;failure=null
        try {
            check(repository.load().revision==revision) { "Local data changed; review again" }
            val ordered=rows.filter { it.id in selection }.sortedBy { when(it.kind) { "document"->0;"receipt"->2;else->1 } }
            for((index,row) in ordered.withIndex()) {
                authorized();progress="${index+1} / ${ordered.size}"
                try {
                    val result=if(row.kind=="document") {
                        val bytes=repository.documentBytes(row.id)
                        try { authorized();remote.document(account,row,requireNotNull(documentDates[row.id]),bytes) { part,count -> authorized();progress="${index+1} / ${ordered.size} · PDF $part / $count" } }
                        finally { bytes.fill(0) }
                    } else {
                        if(row.kind=="receipt" && row.parent !in priorBonds && results[row.parent]?.target.isNullOrBlank()) {
                            results=results+(row.id to MigrationOutcome("review",message="Transfer this receipt's bond first."));continue
                        }
                        val refs=row.record.optJSONArray("documentIds")?.let { a -> (0 until a.length()).map { a.getString(it) }.filter { it in selection } }.orEmpty()
                        if(refs.any { results[it]?.target.isNullOrBlank() }) {
                            results=results+(row.id to MigrationOutcome("review",message="A selected PDF has not finished transferring."));continue
                        }
                        authorized()
                        remote.item(account,row,migrationRecord(row),refs.map { requireNotNull(results[it]).target })
                    }
                    authorized();results=results+(row.id to outcome(row,result))
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(failed: Exception) {
                    authorized()
                    if(failed is ApiException && failed.status in listOf(400,409,413)) {
                        results=results+(row.id to MigrationOutcome("review",message="Review this item in Guest Mode and your account. No existing financial record was replaced."))
                    } else throw failed
                }
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { failure="Transfer stopped. Check the account and connection, then review and confirm a retry. Your original guest data is still on this device." }
        finally { running=false;progress=null }
    }
    fun clear() { closed=true;closePreview();rows=emptyList();selected=emptySet();results=emptyMap();dates=emptyMap();previous=emptySet();priorBonds=emptySet();revision=null;consent=false;prepared=false }
}

/** Explicit domain fields only: no local ID, file reference, revision or workspace metadata in financial DTOs. */
internal fun migrationRecord(row: MigrationRow): JsonRecord {
    if(row.kind=="receipt") return JsonRecord().put("parentSourceId",row.parent).put("dueDate",row.record.text("dueDate"))
        .put("amount",row.record.getDouble("amount")).put("received",row.record.optBoolean("received"))
        .put("method",row.record.text("method")).put("currency",row.record.text("currency")).put("recordedAt",row.record.text("updatedAt"))
    val keys=if(row.kind=="bond") setOf("instrumentType","issuer","currency","market","purchaseDate","settlementDate","bondName","isin","tenorYears","faceValue","pricePercent","accruedInterestPaid","feesPaid","couponRate","withholdingTaxRate","maturityDate","firstCouponDate","couponDates","couponFrequency","scheduleConfidence","broker","accountReference","sourceUrl","status","notes")
        else setOf("instrumentType","exchange","currency","status","securityName","ticker","isin","tradeDate","settlementDate","shares","pricePerShare","grossConsideration","brokerageCommission","rseTransactionFee","csdTransactionLevy","cmaTransactionFee","totalCharges","netAmountPayable","broker","contractNote","cdsAccountNo","cdsReference","tradeReportId","notes")
    return JsonRecord(kotlinx.serialization.json.JsonObject(Json.parseToJsonElement(row.record.toString()).jsonObject.filterKeys { it in keys }).toString())
}
