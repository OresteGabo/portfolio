package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioApi
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent

/** Fixed past dates and synthetic records; no real account, clock-dependent coupons or HTTP server. */
internal class UiQualityPortfolioFixture(lotCount: Int = 1, purchaseRecords: List<JsonRecord>? = null,
    private val documentRecords: List<JsonRecord> = emptyList(), pendingRecords: List<JsonRecord> = emptyList(),
    initialSnapshotGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null, bondRecords: List<JsonRecord> = emptyList()) : AutoCloseable {
    val settings = object : ServerSettings {
        override fun readOrigin() = "https://ui-quality.example.test"
        override fun writeOrigin(origin: String) = Unit
        override fun readLanguage() = "en"
        override fun readScreenCaptureAllowed() = true
    }
    private var bonds = bondRecords
    val linkedBonds = java.util.concurrent.CopyOnWriteArrayList<String>()
    val createdBondPurchases = java.util.concurrent.atomic.AtomicInteger()
    private var orders = pendingRecords
    private var lots = purchaseRecords ?: (0 until lotCount).map { index -> JsonRecord()
        .put("id", "ui-lot-$index").put("ticker", "TEST").put("securityName", "Synthetic UI company")
        .put("currency", "RWF").put("status", "active").put("shares", 100).put("pricePerShare", 90)
        .put("grossConsideration", 9000).put("netAmountPayable", 9000).put("totalCharges", 0)
        .put("tradeDate", "2000-01-01").put("settlementDate", "2000-01-01") }
    val confirmedOrders = java.util.concurrent.CopyOnWriteArrayList<String>()
    val uploadedContracts = java.util.concurrent.atomic.AtomicInteger()
    val createdSharePurchases = java.util.concurrent.atomic.AtomicInteger()
    val requestedDocuments = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val engine = MockEngine { request ->
        if (request.url.encodedPath.endsWith("/mobile-overview")) initialSnapshotGate?.await()
        if (documentRecords.any { request.url.encodedPath == "/api/bonds/documents/${it.text("id")}" }) {
            requestedDocuments.add(request.url.encodedPath.substringAfterLast('/'))
            return@MockEngine respond(readerFixturePdf(pageCount = 1), headers = headersOf(HttpHeaders.ContentType, "application/pdf"))
        }
        val body = when {
            request.url.encodedPath.endsWith("/auth/login") -> """{"user":{"id":"ui-quality","email":"ui@example.test","isOwner":false}}"""
            request.url.encodedPath.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"ui-quality","email":"ui@example.test","isOwner":false}}"""
            request.url.encodedPath.endsWith("/mobile-overview") -> JsonRecord().put("version", 1).put("purchases", JsonList(bonds)).put("holdings", JsonList(lots)).toString()
            request.url.encodedPath.endsWith("/purchases") && request.method == HttpMethod.Get -> JsonRecord().put("purchases", JsonList(bonds)).toString()
            request.url.encodedPath.endsWith("/purchases") && request.method == HttpMethod.Post -> {
                createdBondPurchases.incrementAndGet(); error("No new bond should be created in this fixture")
            }
            request.url.encodedPath.endsWith("/link-imported") -> {
                val pendingId = request.url.encodedPath.substringAfter("/purchases/").substringBefore('/')
                val payload = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                check(payload.optBoolean("confirmed"))
                val pending = bonds.single { it.text("id") == pendingId && it.text("status") == "submitted" }
                val purchase = bonds.single { it.text("id") == payload.text("purchaseId") && it.text("status") == "active" }
                check(payload.getJSONObject("pending").toString() == pending.toString())
                check(payload.getJSONObject("purchase").toString() == purchase.toString())
                bonds = bonds.filterNot { it.text("id") == pendingId }
                linkedBonds.add(pendingId)
                JsonRecord().put("purchase", purchase).toString()
            }
            request.url.encodedPath.endsWith("/documents") && request.method == HttpMethod.Get -> JsonRecord().put("documents", JsonList(documentRecords)).toString()
            request.url.encodedPath.endsWith("/share-orders") -> JsonRecord().put("orders", JsonList(orders)).toString()
            request.url.encodedPath.endsWith("/confirm") && request.url.encodedPath.contains("/share-orders/") -> {
                val orderId = request.url.encodedPath.substringAfter("/share-orders/").substringBefore('/')
                val payload = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                check(payload.optBoolean("confirmed"))
                val order = orders.single { it.text("id") == orderId }
                check(payload.optInt("version") == order.optInt("version"))
                val incoming = payload.getJSONObject("purchase")
                check(incoming.text("contractDocumentId") == "import-doc" || documentRecords.any { it.text("id") == incoming.text("contractDocumentId") })
                val existing = lots.singleOrNull { it.text("id") == incoming.text("id") }
                val purchase = existing ?: incoming.put("id", "confirmed-$orderId")
                if (existing == null) publishExecutedOrder(orderId, purchase) else orders = orders.filterNot { it.text("id") == orderId }
                confirmedOrders.add(orderId)
                JsonRecord().put("holding", purchase).toString()
            }
            request.url.encodedPath.endsWith("/documents") && request.method == HttpMethod.Post -> {
                uploadedContracts.incrementAndGet()
                """{"documentId":"import-doc"}"""
            }
            request.url.encodedPath.endsWith("/equities") && request.method == HttpMethod.Post -> {
                createdSharePurchases.incrementAndGet()
                val purchase = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).put("id", "separate")
                lots = lots + purchase
                JsonRecord().put("holding", purchase).toString()
            }
            request.url.encodedPath.endsWith("/equities") -> JsonRecord().put("holdings", JsonList(lots)).toString()
            request.url.encodedPath.endsWith("/rse/quotes") -> """{"source":{"publishedAt":"2000-01-02","stale":true},"quotes":[{"ticker":"TEST","name":"Synthetic UI company","closing":100}]}"""
            else -> "{}"
        }
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val api = PortfolioApi("https://ui-quality.example.test", engine)
    val model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, apiFactory = { api }, deferAccountStartup = true)
    val selected = JsonRecord().put("ticker", "TEST").put("currency", "RWF")
    fun start() { AppLanguage.initialize(settings); model.login("ui@example.test", "synthetic-only-password") }
    fun ready(ui: UsabilityTestDriver, onMain: (() -> Unit) -> Unit) {
        ui.waitUntil("synthetic portfolio") { model.portfolioReady && !model.busy }
        onMain { model.loadInvestmentHistory(force = true) }
        ui.waitUntil("synthetic full history") { model.historyComplete && !model.busy }
        onMain { model.loadMarket(refresh = true) }
        ui.waitUntil("synthetic quotes") { model.quotes != null && !model.busy }
    }
    fun publishExecutedOrder(orderId: String, purchase: JsonRecord) {
        orders = orders.filterNot { it.text("id") == orderId }
        lots = lots + purchase
    }
    override fun close() { engine.close() }
}
