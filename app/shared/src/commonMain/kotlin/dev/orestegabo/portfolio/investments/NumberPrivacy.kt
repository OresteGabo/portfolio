package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

val LocalNumbersHidden = staticCompositionLocalOf { false }
private val privacyNumbers = Regex("""\b\d{4}-\d{2}-\d{2}\b|[+−-]?\d+(?:[,.]\d+)*(?:[\s\u00a0\u202f]\d{3})*(?:%|[kKmMbB])?""")
private val financialValueOnly = Regex("""^(?:[A-Z]{3} )?[+−-]?[\d\s\u00a0\u202f,.%]+$""")
private val privacyDate = Regex("""\d{4}-\d{2}-\d{2}""")

/** Fixed masks conceal amount length. Navigation dates remain usable. Never changes stored data. */
fun maskFinancialNumbers(text: String): String = privacyNumbers.replace(text) {
    if (privacyDate.matches(it.value)) it.value else "••••"
}

@Composable
fun privateFinancialValue(value: String) = if (LocalNumbersHidden.current) maskFinancialNumbers(value) else value

@Composable
fun FinancialText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
    autoSize: TextAutoSize? = null,
) {
    val hidden = LocalNumbersHidden.current
    val translated = localized(text)
    val displayed = if (hidden) maskFinancialNumbers(translated) else translated
    val privacyModifier = if (hidden && displayed != translated) Modifier.clearAndSetSemantics { contentDescription = localized("Financial numbers hidden") } else Modifier
    val softMask = if (hidden && displayed != translated && financialValueOnly.matches(translated)) Modifier.blur(1.5.dp) else Modifier
    val captured = maskFinancialNumbers(translated)
    if (!hidden && captured != translated && LocalCaptureBackdropRegistry.current != null) {
        val valueOnly = financialValueOnly.matches(translated)
        val measurer = rememberTextMeasurer()
        val maskStyle = style.merge(TextStyle(fontSize = fontSize, fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Unspecified))
        val minimumWidth = with(LocalDensity.current) {
            measurer.measure(captured, maskStyle, softWrap = false, maxLines = 1).size.width.toDp() + 2.dp
        }
        CaptureRedactionRegion(modifier = modifier.then(if (valueOnly) Modifier.widthIn(min = minimumWidth) else Modifier), masked = {
            AppText(captured, Modifier.fillMaxWidth().blur(if (valueOnly) 1.5.dp else 0.dp),
                color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign,
                overflow = if (valueOnly) TextOverflow.Visible else overflow,
                softWrap = if (valueOnly) false else softWrap,
                maxLines = if (valueOnly) maxOf(1, minLines) else maxLines,
                minLines = minLines, style = style, autoSize = autoSize)
        }) {
            AppText(displayed, color = color, fontSize = fontSize, fontWeight = fontWeight,
                textAlign = textAlign, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style, autoSize = autoSize)
        }
    } else AppText(displayed, privacyModifier.then(modifier).then(softMask), color = color, fontSize = fontSize, fontWeight = fontWeight,
        textAlign = textAlign, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style, autoSize = autoSize)
}

@Composable
fun HiddenFinancialChart(modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f)) {
        Column(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = localized("Financial chart hidden") },
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(Icons.Outlined.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            AppText("••••", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
