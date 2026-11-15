package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

internal interface PortfolioApplicationFormHost {
    val api: PortfolioDataApi
    val user: JsonRecord?
    val isGuest: Boolean
    val busy: Boolean
    val calendar: List<JsonRecord>
    var portfolio: PortfolioSnapshot
    var message: String?
    fun action(block: suspend () -> Unit)
    fun closeDocumentPreview()
    suspend fun loadPrivateData(force: Boolean)
}

/** Published application forms and subscription bids; all writes use the model's action guard. */
internal class PortfolioApplicationForms(private val host: PortfolioApplicationFormHost) {
    private val api get() = host.api
    private val uploader = PdfChunkUploader { api.request("/api/bonds/documents", "POST", it) }
    private val user get() = host.user
    private val isGuest get() = host.isGuest
    private val busy get() = host.busy
    private val calendar get() = host.calendar
    private var portfolio: PortfolioSnapshot
        get() = host.portfolio
        set(value) { host.portfolio = value }
    private var message: String?
        get() = host.message
        set(value) { host.message = value }
    private fun action(block: suspend () -> Unit) = host.action(block)
    private fun closeDocumentPreview() = host.closeDocumentPreview()
    private suspend fun loadPrivateData(force: Boolean) = host.loadPrivateData(force)

    internal var applicationBidForm by mutableStateOf<JsonRecord?>(null); private set
    internal fun startApplicationBid(form: JsonRecord) {
        val issue = calendar.firstOrNull { it.text("id") == form.optJSONObject("applicationForm")?.text("calendarId") } ?: return
        if (!isGuest && !busy && applicationBidAllowed(form,issue,bondSubscriptionDate(kotlin.time.Clock.System.now()))) {
            closeDocumentPreview(); applicationBidForm = form
        }
    }
    internal fun closeApplicationBid() { applicationBidForm = null }
    internal fun saveApplicationBid(form: JsonRecord, amount: Double, bidId: String, completed: () -> Unit) = action {
        val issue = calendar.firstOrNull { it.text("id") == form.optJSONObject("applicationForm")?.text("calendarId") }
        require(issue != null && applicationBidAllowed(form,issue,bondSubscriptionDate(kotlin.time.Clock.System.now()))) { "Bids are unavailable for this form or the subscription window has closed." }
        val response = api.request("/api/bonds/application-forms/${form.text("id")}/bids", "POST", JsonRecord().put("amount", amount).put("bidId",bidId))
        require(response.optJSONObject("purchase")?.text("id")?.isNotBlank() == true) { "The bid could not be verified. Refresh your portfolio before retrying." }
        loadPrivateData(force = true)
        applicationBidForm = null
        message = "Bid saved. Awaiting BNR results and allocation confirmation."
        completed()
    }
    var applicationFormsError by mutableStateOf<String?>(null)
        private set

    private suspend fun fetchBondApplicationForms() {
        val forms = api.request("/api/bonds/application-forms").rows("forms")
        portfolio = portfolio.copy(documents = portfolio.documents.filterNot { it.text("category") == BOND_APPLICATION_CATEGORY } + forms)
        applicationFormsError = null
    }

    fun refreshBondApplicationForms() = action {
        try { fetchBondApplicationForms() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            applicationFormsError = "Application forms could not be loaded. Try again."
        }
    }

    fun publishBondApplicationForm(issue: JsonRecord, bytes: ByteArray, completed: () -> Unit) = action {
        require(mayUploadBondApplication(user)) { "Only the owner can upload bond application forms." }
        validatePdfEnvelope(bytes)
        val metadata = JsonRecord().put("category", BOND_APPLICATION_CATEGORY).put("visibility", "shared")
            .put("calendarId", issue.text("id")).put("documentDate", issue.text("closingBookDate"))
            .put("label", "Bond application form").put("instrumentName", issue.text("title")).put("issuer", "BNR")
        val uploadId = Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val response = uploader.upload(bytes, uploadId, metadata = { metadata }, stopAtDocument = true)
        val saved = response.text("documentId").isNotBlank()
        require(saved) { "The application form could not be saved. Try again." }
        completed()
        message = "Shared application form saved."
        try { fetchBondApplicationForms() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure is ApiException && failure.status == 401) throw failure
            applicationFormsError = "The form was saved. Reload application forms to see it."
        }
    }

    fun clear() { applicationFormsError = null; applicationBidForm = null }
}
