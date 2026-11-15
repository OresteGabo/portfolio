package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException

/** Per-screen history cache and request status; transport and coroutine lifetime are supplied by callers. */
internal class EquityPriceHistory(private val read: suspend (String, String) -> JsonRecord) {
    private val historyValues = mutableStateMapOf<String, JsonRecord>()
    val values: Map<String, JsonRecord> get() = historyValues
    private val failedValues = mutableStateMapOf<String, Boolean>()
    val failed: Map<String, Boolean> get() = failedValues
    private val loaded = mutableStateMapOf<String, Int>()
    val loadedRevisions: Map<String, Int> get() = loaded
    private val loading = mutableStateMapOf<String, Int>()
    val loadingRevisions: Map<String, Int> get() = loading

    fun clearFailures() { failedValues.clear() }
    fun clearFailure(symbol: String, period: String) { failedValues.remove("$symbol/$period") }

    suspend fun load(symbol: String, selectedPeriod: String, revision: Int) {
        val key = "$symbol/$selectedPeriod"
        if (historyValues.containsKey(key) && loaded[key] == revision) return
        loading[key] = revision
        failedValues.remove(key)
        try {
            historyValues[key] = read(symbol, selectedPeriod)
            loaded[key] = revision
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failedValues[key] = true
        } finally {
            if (loading[key] == revision) loading.remove(key)
        }
    }
}
