package dev.orestegabo.portfolio.features.transactions

import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException

/** Account cash activity; session guards and local guest writes remain with the model. */
internal class PortfolioCashLedger(
    private val currentApi: () -> PortfolioDataApi,
    private val refreshPortfolio: suspend () -> Unit,
) {
    private val api get() = currentApi()
    var entries by mutableStateOf<List<JsonRecord>>(emptyList()); private set
    var available by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    suspend fun reload() {
        try {
            val response = api.request("/api/bonds/cash-ledger")
            error = null
            available = response.has("entries")
            entries = response.rows("entries")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) {
            if (failure.status == 401) throw failure
            available = false
            if (failure.status in listOf(404, 405)) {
                entries = emptyList()
                error = "Cash activity is currently unavailable. Your investments remain available."
            } else error = "Cash activity could not be refreshed. Try again."
        } catch (_: Exception) {
            available = false
            error = "Cash activity could not be refreshed. Try again."
        }
    }

    suspend fun save(entry: JsonRecord) {
        api.request("/api/bonds/cash-ledger", "POST", entry)
        reload()
        refreshPortfolio()
    }

    suspend fun delete(id: String) {
        api.request("/api/bonds/cash-ledger/$id", "DELETE")
        reload()
        refreshPortfolio()
    }

    fun clear() { error = null; entries = emptyList(); available = false }
}
