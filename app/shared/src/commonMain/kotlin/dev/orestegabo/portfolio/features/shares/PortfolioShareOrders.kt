package dev.orestegabo.portfolio.features.shares

import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Pending account orders and their projection; never converts an order into an executed holding. */
internal class PortfolioShareOrders(
    private val currentApi: () -> PortfolioDataApi,
    private val publishOrders: (List<JsonRecord>) -> Unit,
) {
    private val api get() = currentApi()
    var orders by mutableStateOf<List<JsonRecord>>(emptyList()); private set
    var available by mutableStateOf(false); private set

    private var refreshGeneration = 0L

    suspend fun reload() {
        val client = api
        val generation = ++refreshGeneration
        fun ownsRefresh() = generation == refreshGeneration && client === api
        try {
            val response = client.request("/api/bonds/share-orders")
            currentCoroutineContext().ensureActive()
            if (!ownsRefresh()) return
            orders = JsonRecord.readOnlyList(response.rows("orders"))
            publishOrders(orders)
            available = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) {
            if (!ownsRefresh()) return
            if (failure.status == 401) throw failure
            available = false
            if (failure.status in listOf(404, 405)) { orders = emptyList(); publishOrders(orders) }
        } catch (_: Exception) { if (ownsRefresh()) available = false }
    }

    suspend fun save(id: String, order: JsonRecord, completed: () -> Unit) {
        api.request("/api/bonds/share-orders/$id", "PUT", order)
        completed()
        reload()
    }

    suspend fun cancel(order: JsonRecord, completed: () -> Unit) {
        api.request("/api/bonds/share-orders/${order.text("id")}/cancel", "POST", JsonRecord().put("version", order.optInt("version")))
        completed()
        reload()
    }

    internal fun restoreCached(rows: List<JsonRecord>) {
        orders = JsonRecord.readOnlyList(rows); available = true; publishOrders(orders)
    }

    fun clear() { ++refreshGeneration; orders = emptyList(); publishOrders(orders); available = false }
}
