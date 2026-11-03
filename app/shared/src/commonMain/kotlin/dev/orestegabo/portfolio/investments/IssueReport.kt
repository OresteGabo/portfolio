package dev.orestegabo.portfolio.investments

import io.ktor.http.encodeURLParameter

internal enum class IssueCategory(val label: String) {
    BUG("Bug or broken feature"), DATA("Incorrect or missing data"),
    PERFORMANCE("Slow or unresponsive app"), OTHER("Something else")
}

/** Only explicitly allowlisted context is included; never accept a record, URL, log or account here. */
internal data class IssueReportDetails(val screen: String, val version: String, val platform: String, val language: String)

internal data class IssueReportDraft(
    val category: IssueCategory = IssueCategory.BUG,
    val description: String = "",
    val steps: String = "",
    val expected: String = "",
) {
    val valid get() = description.trim().length in 10..2000 && steps.length <= 1000 && expected.length <= 1000
    val empty get() = description.isBlank() && steps.isBlank() && expected.isBlank()
}

internal fun issueReportScreenName(title: String, pdfPreview: Boolean = false): String = when {
    pdfPreview -> "PDF viewer"
    title in setOf("Portfolio", "Home", "Calendar", "Stocks", "Documents", "Settings", "Share details", "Bond details",
        "Sign in", "Document details", "Coupons", "All investments", "Add bond", "Edit bond", "Add shares", "Edit shares",
        "Pending purchase", "Missing documents", "Investment outlook", "Simulator", "Learn", "Sign-in activity") -> title
    else -> "App"
}

internal fun issueReportBody(draft: IssueReportDraft, details: IssueReportDetails?, language: String): String {
    require(draft.valid)
    fun label(text: String) = localized(text, language)
    return buildString {
        appendLine("Struct Capital · ${label("Report a problem")}")
        appendLine("${label("Issue type")}: ${label(draft.category.label)}")
        appendLine()
        appendLine(label("What happened?"))
        appendLine(draft.description.trim())
        if (draft.steps.isNotBlank()) { appendLine(); appendLine(label("Steps to reproduce")); appendLine(draft.steps.trim()) }
        if (draft.expected.isNotBlank()) { appendLine(); appendLine(label("What should have happened?")); appendLine(draft.expected.trim()) }
        details?.let {
            appendLine(); appendLine(label("App details"))
            appendLine("${label("Screen")}: ${label(it.screen)}")
            appendLine("${label("App version")}: ${it.version}")
            appendLine("${label("Platform")}: ${it.platform}")
            appendLine("${label("Language")}: ${it.language}")
        }
    }.trimEnd()
}

internal fun supportEmailUri(address: String, subject: String, body: String): String {
    require(address in setOf(BrandContacts.SUPPORT, BrandContacts.SECURITY))
    require(subject.none { it == '\r' || it == '\n' })
    return "mailto:$address?subject=${subject.encodeURLParameter()}&body=${body.encodeURLParameter()}"
}

internal expect fun issueReportPlatform(): String
