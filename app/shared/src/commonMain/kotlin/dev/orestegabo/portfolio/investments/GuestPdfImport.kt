package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

/** Private local workflow: no API/client/settings/telemetry/publication dependency. */
class GuestPdfImport(private val repository: GuestPortfolioRepository, private val workDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default) {
    var review by mutableStateOf<JsonRecord?>(null); private set
    var duplicate by mutableStateOf<JsonRecord?>(null); private set
    var failure by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf<String?>(null); private set
    var confirmed by mutableStateOf(false)
    var targetId by mutableStateOf<String?>(null)
    var previewBytes by mutableStateOf<ByteArray?>(null); private set
    var previewName by mutableStateOf(""); private set
    private var pdf: ByteArray? = null
    private var name = "investment.pdf"
    suspend fun select(actions: NativeActions, parser: InvestmentDocumentParser) {
        clear()
        var selected: ByteArray? = null
        try {
            val privateActions = object : NativeActions by actions {
                override suspend fun pickPdf(onSelected: () -> Unit) = actions.pickPrivatePdf(onSelected)
            }
            val loaded = readPurchasePdf(privateActions, parser = object : InvestmentDocumentParser {
                override var onProgress: (String) -> Unit
                    get() = parser.onProgress
                    set(value) { parser.onProgress = value }
                override suspend fun parse(pdf: ByteArray): JsonRecord {
                    withContext(workDispatcher) { validatePdfEnvelope(pdf) }
                    return parser.parse(pdf)
                }
                override fun cancel() = parser.cancel()
            }, openedPdf = { selected = it }, selectedPdf = { pdf = it.copyOf() }, progress = { progress = it?.stage?.label }) ?: return
            currentCoroutineContext().ensureActive()
            require(loaded.text("kind") in listOf("bond", "equity")) { "unsupported" }
            require(loaded.optJSONObject("fields") != null) { "missing review fields" }
            name = importDisplayName(actions.pickedPdfName ?: "investment.pdf").take(180)
            val bytes = requireNotNull(pdf)
            val digest = withContext(workDispatcher) { importPdfDigest(bytes) }
            val snapshot = repository.load().portfolio
            val doc = snapshot.documents.singleOrNull { it.text("sha256") == digest }
            val retained = if (doc == null) false else try {
                repository.documentBytes(doc.text("id")).also { it.fill(0) }; true
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            if (doc != null && retained && (snapshot.purchases + snapshot.holdings).any { it.hasDocument(doc.text("id")) }) {
                duplicate = doc; bytes.fill(0); pdf = null
            } else review = loaded
        } catch (_: TimeoutCancellationException) {
            clear(); failure = "Local PDF processing timed out. Try a clearer text-based PDF or enter the investment manually."
        } catch (cancelled: CancellationException) { clear(); throw cancelled }
        catch (_: Exception) {
            clear()
            failure = "Could not import this PDF. Use an unprotected BNR personal allocation notice or share contract, up to 10 MB and 10 pages. Statements and public issuance reports are not supported in Guest Mode yet."
        } finally { selected?.fill(0); progress = null }
    }
    fun fields(): JsonRecord? = review?.let { raw ->
        runCatching { importedPurchaseInput(raw) }.getOrElse {
            JsonRecord(requireNotNull(raw.optJSONObject("fields")).toString()).put("status", "active")
        }
    }
    suspend fun save(body: JsonRecord): GuestPortfolio {
        check(confirmed) { "Review the PDF and confirm its details before saving." }
        val bytes = requireNotNull(pdf).copyOf()
        try {
            val result = repository.importPdf(bytes, name, requireNotNull(review).text("kind"), body, targetId)
            clear()
            return result
        } finally { bytes.fill(0) }
    }
    suspend fun preview(id: String? = null, fileName: String = name) {
        closePreview()
        previewBytes = if (id == null) requireNotNull(pdf).copyOf() else repository.documentBytes(id)
        previewName = fileName
    }
    fun closePreview() { previewBytes?.fill(0); previewBytes = null; previewName = "" }
    fun clear() {
        closePreview(); pdf?.fill(0); pdf = null; review = null; duplicate = null
        confirmed = false; targetId = null; failure = null; progress = null
    }
}
