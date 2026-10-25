package dev.orestegabo.portfolio.investments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Shared text entry point keeps translations reactive, including stored errors and progress. */
@Composable
fun AppText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null, overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) = Text(localized(text), modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
    textAlign = textAlign, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style)

@Composable
fun AppIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) = Icon(imageVector, contentDescription?.let { localized(it) }, modifier, tint)

@Composable
internal fun LanguageOptions() {
    ListItem(headlineContent = { AppText("Language") },
        supportingContent = { AppText(if (AppLanguage.code == "fr") "Français" else "English") },
        leadingContent = { AppIcon(Icons.Outlined.Language, null) },
        trailingContent = { LanguageFlagSwitch() })
}

/** Two explicit choices; until chosen, the selection follows the supported system language. */
@Composable
private fun LanguageFlagSwitch() {
    val french = AppLanguage.code == "fr"
    val position by animateDpAsState(if (french) 0.dp else 76.dp, label = "Language selection")
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.width(160.dp).height(52.dp).selectableGroup()) {
            Surface(Modifier.padding(4.dp).offset(x = position).width(76.dp).fillMaxHeight(),
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {}
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                listOf("fr" to "Français", "en" to "English").forEach { (code, name) ->
                    val selected = (code == "fr") == french
                    Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { AppLanguage.select(code) })
                        .semantics(mergeDescendants = true) { contentDescription = name },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        LanguageFlag(code)
                        Text(code.uppercase(), Modifier.clearAndSetSemantics {},
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Drawn flags render consistently on Android and iOS, including England's St George cross. */
@Composable
private fun LanguageFlag(code: String) {
    Surface(shape = RoundedCornerShape(3.dp), border = BorderStroke(0.5.dp, Color.Black.copy(alpha = .12f))) {
        Canvas(Modifier.size(width = 24.dp, height = 18.dp).clearAndSetSemantics {}) {
            drawRect(Color.White)
            if (code == "fr") {
                drawRect(Color(0xFF002395), size = Size(size.width / 3f, size.height))
                drawRect(Color(0xFFED2939), topLeft = Offset(size.width * 2f / 3f, 0f), size = Size(size.width / 3f, size.height))
            } else {
                val red = Color(0xFFCE1124)
                drawRect(red, Offset(size.width * .4f, 0f), Size(size.width * .2f, size.height))
                drawRect(red, Offset(0f, size.height * .38f), Size(size.width, size.height * .24f))
            }
        }
    }
}
