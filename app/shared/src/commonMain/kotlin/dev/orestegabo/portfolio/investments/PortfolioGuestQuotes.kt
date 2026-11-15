package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Optional guest market reads; no account API or writable guest repository dependency. */
internal class PortfolioGuestQuotes(
    private val scope: CoroutineScope,
    private val guestMarketData: GuestMarketData?,
    private val currentGeneration: () -> Int,
    private val personalMode: () -> Boolean,
) {
    private val generation get() = currentGeneration()
    private val isPersonal get() = personalMode()
    var guestQuotes by mutableStateOf<GuestQuoteSnapshot?>(null); private set
    var guestQuotesRefreshing by mutableStateOf(false); private set
    var guestQuotesFailed by mutableStateOf(false); private set
    val guestQuotesEnabled get() = guestMarketData?.enabled == true
    private var guestQuoteJob: Job? = null
    fun refresh() {
        if (!isPersonal || !guestQuotesEnabled || guestQuoteJob?.isActive == true) return
        val token = generation
        guestQuotesRefreshing = true
        guestQuoteJob = scope.launch {
            fun current() = token == generation && isPersonal
            try {
                val saved = guestMarketData?.load()
                if (current()) guestQuotes = saved
                val latest = guestMarketData?.refresh()
                if (current()) { guestQuotes = latest; guestQuotesFailed = false }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current()) guestQuotesFailed = true }
            finally { if (current()) { guestQuotesRefreshing = false; guestQuoteJob = null } }
        }
    }
    fun stop() {
        guestQuoteJob?.cancel(); guestQuoteJob = null; guestQuotes = null
        guestQuotesRefreshing = false; guestQuotesFailed = false
    }
}
