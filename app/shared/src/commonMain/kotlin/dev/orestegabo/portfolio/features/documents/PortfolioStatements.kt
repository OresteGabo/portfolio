package dev.orestegabo.portfolio.features.documents

import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*

/** Statement evidence/reconciliation state; authenticated action and portfolio refresh stay with the host. */
internal class PortfolioStatements(
    private val currentApi: () -> PortfolioDataApi,
    private val refreshAfterWrite: suspend (String) -> Unit,
) {
    private val api get() = currentApi()
    var items by mutableStateOf<List<JsonRecord>>(emptyList()); private set
    var reviewRows by mutableStateOf<List<JsonRecord>?>(null); private set

    fun closeReview() { reviewRows = null }
    fun showRecord(row: JsonRecord) { reviewRows = JsonRecord.readOnlyList(listOf(row)) }

    suspend fun showDocument(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid statement document." }
        reviewRows = JsonRecord.readOnlyList(api.request("/api/bonds/statement-items?documentId=$id").rows("items"))
    }

    suspend fun resolve(item: JsonRecord, purchaseId: String?, reason: String, completed: () -> Unit) {
        val id = item.text("id")
        require(id.matches(Regex("[A-Za-z0-9_-]{1,120}")) && reason.trim().length in 3..500) { "Explain the reconciliation correction." }
        val updated = api.request("/api/bonds/statement-items/$id/resolve", "POST", JsonRecord()
            .put("action", if (purchaseId == null) "clear" else "link").put("purchaseId", purchaseId.orEmpty())
            .put("reason", reason.trim()).put("expectedDecisionAt", item.rows("decisions").lastOrNull()?.text("at").orEmpty()))
            .rows("rows").single()
        reviewRows = reviewRows?.let { rows -> JsonRecord.readOnlyList(rows.map { if (it.text("id") == id) updated else it }) }
        items = JsonRecord.readOnlyList(api.request("/api/bonds/statement-items").rows("items"))
        completed()
    }

    suspend fun createBond(item: JsonRecord, instrument: JsonRecord, figures: JsonRecord, completed: () -> Unit) {
        val id = item.text("id")
        require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid statement record." }
        val body = JsonRecord(figures.toString()).put("instrumentId", instrument.text("id")).put("confirmed", true)
            .put("reason", "Verified statement purchase and selected public bond terms")
            .put("expectedDecisionAt", item.rows("decisions").lastOrNull()?.text("at").orEmpty())
        val updated = api.request("/api/bonds/statement-items/$id/bond", "POST", body).rows("rows").single()
        reviewRows = reviewRows?.let { rows -> JsonRecord.readOnlyList(rows.map { if (it.text("id") == id) updated else it }) }
        completed()
        refreshAfterWrite("Statement purchase saved using existing bond terms.")
    }

    suspend fun load() {
        items = try { JsonRecord.readOnlyList(api.request("/api/bonds/statement-items").rows("items")) }
        catch (failure: ApiException) {
            if (failure.status == 401) throw failure
            if (failure.status == 404 || failure.status == 405) emptyList() else items
        }
    }

    suspend fun check(review: JsonRecord, completed: (List<JsonRecord>) -> Unit) {
        val rows = review.rows("rows").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        val movements = review.rows("movements").map { JsonRecord(it.toString()).put("currency", "RWF").put("broker", "BK Capital") }
        require(rows.size + movements.size in 1..500) { "No records were found in this statement." }
        val response = api.request("/api/bonds/statement-items", "POST", JsonRecord().put("items", JsonList(rows)).put("movements", JsonList(movements)))
        items = JsonRecord.readOnlyList(api.request("/api/bonds/statement-items").rows("items"))
        completed(response.rows("rows"))
    }

    fun clear() { items = emptyList(); reviewRows = null }
}
