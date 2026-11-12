package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared-file preparation only: parsing and saving remain behind the app's normal entry flow. */
@Composable
internal fun PdfImportPreparationScreen(pdf: ReceivedPdf, continueImport: () -> Unit, cancel: () -> Unit) {
    PlatformBackHandler(true, cancel)
    var prepared by rememberSaveable(pdf.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StructMark(Modifier.size(44.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        AppText("Struct Capital", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        AppText("DOCUMENT IMPORT", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppText(if (prepared) "Ready for your review" else "Your PDF.\nA clearer portfolio.", style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 38.sp), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    AppText(if (prepared) "Open Struct Capital to review your document and choose what to save." else "Prepare your document, then review its details in Struct Capital.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(22.dp), color = colors.surface, border = BorderStroke(1.dp, colors.primary.copy(alpha = .13f))) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        Surface(shape = RoundedCornerShape(15.dp), color = colors.primary.copy(alpha = .09f)) {
                            Icon(Icons.Outlined.PictureAsPdf, null, tint = colors.primary, modifier = Modifier.size(54.dp, 62.dp).padding(13.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppText(pdf.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("${localized("PDF document")} · ${(pdf.bytes.size + 1023) / 1024} KB", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    ImportStep(if (prepared) Icons.Outlined.CheckCircle else Icons.Outlined.Download, "Prepare", "Keep a local copy ready for import.", true)
                    ImportStep(Icons.Outlined.Search, "Review in the app", "Check the extracted investment details.", prepared)
                    ImportStep(Icons.Outlined.CheckCircle, "Save when ready", "You decide what enters your portfolio.", false)
                }
                Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = .07f)) {
                    Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (prepared) Icons.Outlined.CheckCircle else Icons.Outlined.PrivacyTip, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        AppText(if (prepared) "Your PDF is ready on this device. Nothing has been uploaded or saved to your portfolio." else "Preparing an import does not upload your PDF or save any investment records.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { if (prepared) continueImport() else prepared = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(50)) {
                    Icon(if (prepared) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.Download, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    AppText(if (prepared) "Open Struct Capital" else "Prepare import", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = cancel, modifier = Modifier.heightIn(min = 44.dp)) { AppText("Cancel", color = colors.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun ImportStep(icon: ImageVector, title: String, detail: String, active: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f), modifier = Modifier.size(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AppText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            AppText(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
