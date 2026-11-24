package dev.orestegabo.portfolio.features.transactions

import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One publication for ledger data and refresh status; records are detached read-only snapshots. */
internal data class CashLedgerUiState(
    val entries: List<JsonRecord> = emptyList(), val available: Boolean = false,
    val error: String? = null, val isRefreshing: Boolean = false,
)

/** Account cash activity; session guards and local guest writes remain with the model.
 * Owned by the ViewModel and called on its UI dispatcher, like its Compose state.
 */
internal class PortfolioCashLedger(
    private val currentApi: () -> PortfolioDataApi,
    private val refreshPortfolio: suspend () -> Unit,
) {
    private val api get() = currentApi()
    var state by mutableStateOf(CashLedgerUiState()); private set
    val entries get() = state.entries
    val available get() = state.available
    val error get() = state.error
    private var refreshGeneration = 0L

    suspend fun reload() {
        val client = api
        val generation = ++refreshGeneration
        fun ownsRefresh() = generation == refreshGeneration && client === api
        state = state.copy(isRefreshing = true)
        try {
            val response = client.request("/api/bonds/cash-ledger")
            currentCoroutineContext().ensureActive()
            if (!ownsRefresh()) return
            state = CashLedgerUiState(JsonRecord.readOnlyList(response.rows("entries")), response.has("entries"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) {
            if (!ownsRefresh()) return
            if (failure.status == 401) throw failure
            state = if (failure.status in listOf(404, 405)) {
                CashLedgerUiState(error = "Cash activity is currently unavailable. Your investments remain available.")
            } else state.copy(available = false, error = "Cash activity could not be refreshed. Try again.", isRefreshing = false)
        } catch (_: Exception) {
            if (!ownsRefresh()) return
            state = state.copy(available = false, error = "Cash activity could not be refreshed. Try again.", isRefreshing = false)
        } finally {
            // An older request must not stop the newer request's loading indicator.
            if (generation == refreshGeneration) state = state.copy(isRefreshing = false)
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

    internal fun restoreCached(entries: List<JsonRecord>, available: Boolean) {
        state = CashLedgerUiState(JsonRecord.readOnlyList(entries), available)
    }

    fun clear() {
        ++refreshGeneration
        state = CashLedgerUiState()
    }
}
