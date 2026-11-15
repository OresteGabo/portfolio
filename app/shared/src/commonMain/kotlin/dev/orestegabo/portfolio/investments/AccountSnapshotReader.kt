package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal data class AccountSnapshotRead(val snapshot: PortfolioSnapshot, val historyComplete: Boolean)

/** Reads a complete compatible account projection; the caller owns locks, cache age and publication. */
internal class AccountSnapshotReader(
    private val currentApi: () -> PortfolioDataApi,
    private val currentPortfolio: () -> PortfolioSnapshot,
) {
    private val api get() = currentApi()
    private val portfolio get() = currentPortfolio()
    private var compactOverviewSupported: Boolean? = null

    suspend fun purchase(id: String): JsonRecord = api.request("/api/bonds/purchases/$id")
    suspend fun statementItem(id: String): JsonRecord? =
        api.request("/api/bonds/statement-items").rows("items").firstOrNull { it.text("id") == id }

    fun resetCompatibility() { compactOverviewSupported = null }

    suspend fun read(deferSupplementary: Boolean): AccountSnapshotRead {
        val overview = if (compactOverviewSupported == false) null else try {
            val response = api.request("/api/bonds/mobile-overview")
            if (response.optInt("version") == 1 && response.has("recentPurchaseIds") && response.has("recentHoldingIds")) {
                compactOverviewSupported = true
                response
            } else { compactOverviewSupported = false; null }
        } catch (failure: ApiException) {
            if (failure.status == 404 || failure.status == 405) { compactOverviewSupported = false; null } else throw failure
        }
        val (purchases, holdings) = if (overview != null) overview.rows("purchases") to overview.rows("holdings")
        else coroutineScope {
            val bonds = async { api.request("/api/bonds/purchases").rows("purchases") }
            val shares = async { api.request("/api/bonds/equities").rows("holdings") }
            bonds.await() to shares.await()
        }
        val allBondsForPending = if (overview != null) try { api.request("/api/bonds/purchases").rows("purchases") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure; null }
            else purchases
        val completePendingBonds = (allBondsForPending ?: portfolio.purchases).filter { it.text("status") == "submitted" }
        val documents = if (deferSupplementary) portfolio.documents else api.request("/api/bonds/documents").rows("documents")
        val monthlyTotals = overview?.optJSONArray("monthlyInvestmentTotals")?.objects()
            ?: if (overview != null) try {
                // Old compact responses omit contribution totals. Fetch their source records once
                // during this sync, derive the rolling summary, then discard those extra records.
                monthlyGoalTotalsFromHistory(requireNotNull(allBondsForPending),
                    api.request("/api/bonds/equities").rows("holdings"), Today)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (failure is ApiException && failure.status == 401) throw failure
                null // An unavailable secondary summary must not hide the rest of the portfolio.
            } else null
        fun ids(key: String): Set<String>? = overview?.optJSONArray(key)?.let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() }
        val snapshot = PortfolioSnapshot((purchases + completePendingBonds).distinctBy { it.text("id") }, holdings, if (deferSupplementary) portfolio.documents else documents, ids("recentPurchaseIds"), ids("recentHoldingIds"),
            monthlyTotals)
        return AccountSnapshotRead(snapshot, overview == null)
    }
}
