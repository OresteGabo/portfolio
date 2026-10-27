package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class LegalText(val en: String, val fr: String) {
    fun display(language: String = AppLanguage.code) = if (language == "fr") fr else en
}
internal data class LegalSection(val title: LegalText, val body: LegalText)
internal data class LegalDocument(val id: String, val title: LegalText, val summary: LegalText, val sections: List<LegalSection>)
internal data class SoftwareNotice(val component: String, val path: String)
internal data class SoftwareNoticeGroup(val id: String, val title: LegalText, val summary: LegalText, val notices: List<SoftwareNotice>)
internal val legalNoticeGroups = listOf(
    SoftwareNoticeGroup("pdf", LegalText("PDF reading", "Lecture des PDF"),
        LegalText("Opening and displaying documents", "Ouverture et affichage des documents"), listOf(
            SoftwareNotice("PDF.js", "pdfjs-LICENSE"),
            SoftwareNotice("Character maps", "bond-import/cmaps/LICENSE"))),
    SoftwareNoticeGroup("text", LegalText("Text recognition", "Reconnaissance de texte"),
        LegalText("Reading text from scanned pages", "Lecture du texte des pages numérisées"), listOf(
            SoftwareNotice("Tesseract core", "tesseract-core-LICENSE"))),
    SoftwareNoticeGroup("fonts", LegalText("Document fonts", "Polices des documents"),
        LegalText("Displaying text inside PDFs", "Affichage du texte des PDF"), listOf(
            SoftwareNotice("Foxit fonts", "bond-import/standard_fonts/LICENSE_FOXIT"),
            SoftwareNotice("Liberation fonts", "bond-import/standard_fonts/LICENSE_LIBERATION"))),
    SoftwareNoticeGroup("images", LegalText("Document images", "Images des documents"),
        LegalText("Displaying scanned images and their colours", "Affichage des images numérisées et de leurs couleurs"), listOf(
            SoftwareNotice("PDF.js QCMS", "bond-import/wasm/LICENSE_PDFJS_QCMS"),
            SoftwareNotice("PDF.js OpenJPEG", "bond-import/wasm/LICENSE_PDFJS_OPENJPEG"),
            SoftwareNotice("OpenJPEG", "bond-import/wasm/LICENSE_OPENJPEG"),
            SoftwareNotice("PDF.js JBIG2", "bond-import/wasm/LICENSE_PDFJS_JBIG2"),
            SoftwareNotice("QCMS", "bond-import/wasm/LICENSE_QCMS"),
            SoftwareNotice("JBIG2", "bond-import/wasm/LICENSE_JBIG2")))
)

/** Remove source-file comment wrappers for reading; bundled originals stay untouched. */
internal fun readableLicence(source: String): String {
    var blockComment = false
    return source.lines().map { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("//") -> trimmed.removePrefix("//").removePrefix(" ")
            trimmed == "/*" -> { blockComment = true; "" }
            trimmed == "*/" -> { blockComment = false; "" }
            blockComment && trimmed.startsWith("*") -> trimmed.removePrefix("*").removePrefix(" ")
            else -> line
        }
    }.joinToString("\n").trim()
}

internal fun legalTitle(route: String): String = if (route == "about") localized("About Struct Capital") else legalDocuments.find { it.id == route }?.title?.display()
    ?: legalNoticeGroups.find { "notices:${it.id}" == route }?.title?.display()
    ?: if (route == "notices") localized("Open-source notices") else localized("Legal & privacy")

@Composable
internal fun LegalScreen(route: String, open: (String) -> Unit) {
    if (route == "about") {
        AboutScreen(open)
        return
    }
    val actions = LocalNativeActions.current
    val doc = legalDocuments.find { it.id == route }
    val group = legalNoticeGroups.find { "notices:${it.id}" == route }
    var notices by remember(route) { mutableStateOf<List<Pair<String, String>>?>(null) }
    var failed by remember(route) { mutableStateOf(false) }
    LaunchedEffect(route) {
        if (group != null) {
            runCatching {
                group.notices.map { it.component to readableLicence(readSharedAsset("legal/licenses/${it.path}").decodeToString()) }
            }.onSuccess { notices = it }.onFailure { failed = true }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        when {
            route == "notices" -> {
                item { AppText("These acknowledgements cover the software used to read your documents. Original licence wording is kept in its original language.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(legalNoticeGroups, key = { it.id }) { entry ->
                    LegalLink(entry.title.display(), entry.summary.display(), "software") { open("notices:${entry.id}") }
                }
            }
            group != null -> {
                item { AppText("Original licence text", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                when {
                    failed -> item { AppText("This notice could not be opened. Please try again.") }
                    notices == null -> item { CircularProgressIndicator() }
                    else -> items(notices.orEmpty()) { (component, text) ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppText(component, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            LegalParagraphs(text, small = true)
                        }
                    }
                }
            }
            doc != null -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(doc.summary.display(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppText("${localized("Updated")} · $LEGAL_VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(doc.sections) { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppText(section.title.display(), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        LegalParagraphs(section.body.display())
                    }
                }
                item {
                    val privacyRequest = doc.id == "privacy" || doc.id == "data"
                    val address = if (privacyRequest) BrandContacts.PRIVACY else BrandContacts.SUPPORT
                    LegalLink(localized(if (privacyRequest) "Privacy and data requests" else "App support"), address, "contact") {
                        actions.openEmail(address)
                    }
                }
                if (doc.id == "privacy") item {
                    TextButton(onClick = { actions.openUrl("https://dpo.gov.rw/") }) { AppText("Data Protection & Privacy Office") }
                }
                if (doc.id == "credits") item {
                    LegalLink(localized("Open-source notices"), localized("Software acknowledgements and licences"), "software") { open("notices") }
                }
            }
            else -> {
                item { AppText("Struct Capital · Rwanda", style = MaterialTheme.typography.titleMedium) }
                item { LegalLink(localized("About Struct Capital"), localized("The app, the brand and how to reach us"), "about") { open("about") } }
                items(legalDocuments, key = { it.id }) { document ->
                    LegalLink(document.title.display(), document.summary.display(), document.id) { open(document.id) }
                }
                item { AppText("Contact", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
                items(listOf(
                    "General enquiries" to BrandContacts.HELLO,
                    "App support" to BrandContacts.SUPPORT,
                    "Privacy and data requests" to BrandContacts.PRIVACY,
                    "Security and phishing reports" to BrandContacts.SECURITY,
                    "Business enquiries" to BrandContacts.INFO,
                )) { (label, address) ->
                    LegalLink(localized(label), address, "contact") { actions.openEmail(address) }
                }
            }
        }
    }
}

@Composable
private fun LegalParagraphs(text: String, small: Boolean = false) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            text.split("\n\n").forEach { paragraph ->
                AppText(paragraph, style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LegalLink(title: String, subtitle: String, kind: String, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        ListItem(
            headlineContent = { AppText(title, fontWeight = FontWeight.Medium) },
            supportingContent = { AppText(subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { AppIcon(when (kind) {
                "privacy" -> Icons.Outlined.PrivacyTip
                "data" -> Icons.Outlined.ManageAccounts
                "financial" -> Icons.Outlined.AccountBalance
                "credits" -> Icons.Outlined.Info
                "software" -> Icons.Outlined.Description
                "contact" -> Icons.Outlined.Email
                "about" -> Icons.Outlined.Info
                else -> Icons.Outlined.Gavel
            }, null) },
            trailingContent = { AppIcon(Icons.Filled.ChevronRight, null) },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
