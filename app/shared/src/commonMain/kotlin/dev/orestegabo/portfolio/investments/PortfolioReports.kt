package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Fetches complete report inputs without expanding the dashboard's compact snapshot. */
internal class PortfolioReports(
    private val currentApi: () -> PortfolioDataApi,
    private val loadLedger: suspend () -> List<JsonRecord>,
    private val loadCouponTerms: suspend () -> List<JsonRecord>,
    private val reportWorkDispatcher: CoroutineDispatcher,
) {
    private val api get() = currentApi()
    var progress by mutableStateOf<String?>(null); private set
    var annualReportYear by mutableStateOf<Int?>(null); private set

    suspend fun portfolioPdf(email: String, target: Long, date: LocalDate, language: String, goal: InvestmentGoal = InvestmentGoal(monthlyAmount = target)): ByteArray {
        progress = "Loading all investment years…"
        // Export requires complete, fresh records. Do not inflate the dashboard's compact cache.
        val bonds = api.request("/api/bonds/purchases").rows("purchases")
        val shares = api.request("/api/bonds/equities").rows("holdings")
        val files = api.request("/api/bonds/documents").rows("documents")
        val missing = try { api.request("/api/bonds/statement-items").rows("items") }
        catch (failure: ApiException) {
            if (failure.status == 404 || failure.status == 405) emptyList() else throw failure
        }
        val latestQuotes = if (shares.isEmpty()) null else try { api.request("/api/bonds/rse/quotes?kind=equities") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure; null }
        val receipts=try { api.request("/api/bonds/notifications/coupons").let { if(it.has("receipts")) it.rows("receipts") else null } }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { null }
        val ledgerEntries = loadLedger()
        val apiOrders = api.request("/api/bonds/share-orders").rows("orders")
        val reportCouponTerms = loadCouponTerms()
        progress = "Building your report…"
        return withContext(Dispatchers.Default) {
            generatePortfolioReport(PortfolioReportData(PortfolioSnapshot(bonds, shares, files, shareOrders = apiOrders, couponReferences = reportCouponTerms), latestQuotes,
                missing, email, target, date, language, receipts, ledgerEntries, investmentGoal = goal))
        }
    }

    suspend fun annualPdf(year: Int, email: String, monthlyTarget: () -> Long, date: LocalDate, language: String, investmentGoal: () -> InvestmentGoal = { InvestmentGoal(monthlyAmount = monthlyTarget()) }): ByteArray {
        annualReportYear = year
        try {
            progress = "Loading annual investment records…"
            val bonds = api.request("/api/bonds/purchases").rows("purchases")
            val shares = api.request("/api/bonds/equities").rows("holdings")
            val ledgerEntries = loadLedger()
            val orders = api.request("/api/bonds/share-orders").rows("orders")
            val data = PortfolioReportData(PortfolioSnapshot(bonds, shares, shareOrders = orders), null, emptyList(), email,
                monthlyTarget(), date, language, ledgerEntries = ledgerEntries, investmentGoal = investmentGoal())
            val scope = annualReportScope(data, year)
            val histories = mutableMapOf<String, JsonRecord>()
            scope.closingShares.filter { it.text("currency") == "RWF" }.map { it.text("ticker") }.distinct().forEach { ticker ->
                require(ticker.matches(Regex("[A-Za-z0-9._-]{1,24}"))) { "Invalid security ticker." }
                try { histories[ticker] = api.request("/api/bonds/rse/equity-history?tickers=$ticker&period=all") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure }
            }
            val receipts = try { api.request("/api/bonds/notifications/coupons").let { if (it.has("receipts")) it.rows("receipts") else null } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (failure is ApiException && failure.status == 401) throw failure; null }
            progress = "Building your report…"
            return withContext(reportWorkDispatcher) { generateAnnualReport(data.copy(couponReceipts = receipts), year, histories) }
        } finally { progress = null; annualReportYear = null }
    }

    fun sharing() { progress = "Choose where to share the PDF" }
    fun clearProgress() { progress = null }
}
