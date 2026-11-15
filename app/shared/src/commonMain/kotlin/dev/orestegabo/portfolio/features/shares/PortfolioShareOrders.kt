package dev.orestegabo.portfolio.features.shares

import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException

/** Pending account orders and their projection; never converts an order into an executed holding. */
internal class PortfolioShareOrders(
    private val currentApi: () -> PortfolioDataApi,
    private val publishOrders: (List<JsonRecord>) -> Unit,
) {
    private val api get() = currentApi()
    var orders by mutableStateOf<List<JsonRecord>>(emptyList()); private set
    var available by mutableStateOf(false); private set

    suspend fun reload() {
        try {
            orders = api.request("/api/bonds/share-orders").rows("orders")
            publishOrders(orders)
            available = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ApiException) {
            if (failure.status == 401) throw failure
            available = false
            if (failure.status in listOf(404, 405)) { orders = emptyList(); publishOrders(orders) }
        } catch (_: Exception) { available = false }
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

    fun clear() { orders = emptyList(); publishOrders(orders); available = false }
}
