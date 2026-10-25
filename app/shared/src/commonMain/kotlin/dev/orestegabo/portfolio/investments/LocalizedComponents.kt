package dev.orestegabo.portfolio.investments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
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
    autoSize: TextAutoSize? = null,
 ) {
    val registry = LocalCaptureBackdropRegistry.current
    val position = remember(registry) { arrayOfNulls<Rect>(1) }
    SideEffect { registry?.invalidate() }
    val tracked = if (registry == null) modifier else modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (position[0] != bounds) { position[0] = bounds; registry.invalidate() }
    }
    Text(localized(text), tracked, color = color, fontSize = fontSize, fontWeight = fontWeight,
        textAlign = textAlign, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style, autoSize = autoSize)
}

@Composable
fun AppIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) = Icon(imageVector, contentDescription?.let { localized(it) }, modifier, tint)

@Composable
fun LanguageOptions() {
    ListItem(headlineContent = { AppText("Language") },
        supportingContent = { AppText(if (AppLanguage.code == "fr") "Français" else "English") },
        leadingContent = { AppIcon(Icons.Outlined.Language, null) },
        trailingContent = { LanguageFlagSwitch() })
}

/** Two explicit choices; until chosen, the selection follows the supported system language. */
@Composable
private fun LanguageFlagSwitch() {
    val reducedMotion = LocalReducedMotion.current
    val selectionFeedback = LocalSelectionFeedback.current
    val french = AppLanguage.code == "fr"
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val track = if (dark) Color(0xFF181A1D) else Color(0xFFF0F1F3)
    val selection = if (dark) Color(0xFF35383D) else Color.White
    val outline = if (dark) Color(0xFF484C52) else Color(0xFFD8DBE0)
    val selectedText = if (dark) Color(0xFFF5F6F7) else Color(0xFF20242A)
    val inactiveText = if (dark) Color(0xFFADB2BA) else Color(0xFF666D77)
    val position by animateDpAsState(if (french) 0.dp else 76.dp, animationSpec = if (reducedMotion) snap() else spring(), label = "Language selection")
    Surface(shape = RoundedCornerShape(12.dp), color = track,
        border = BorderStroke(1.dp, outline.copy(alpha = .65f))) {
        Box(Modifier.width(160.dp).height(52.dp).selectableGroup()) {
            Surface(Modifier.padding(4.dp).offset(x = position).width(76.dp).fillMaxHeight(),
                shape = RoundedCornerShape(8.dp), color = selection,
                border = BorderStroke(1.dp, outline),
                shadowElevation = 2.dp) {}
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                listOf("fr" to "Français", "en" to "English").forEach { (code, name) ->
                    val selected = (code == "fr") == french
                    Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) { AppLanguage.select(code); selectionFeedback() } })
                        .semantics(mergeDescendants = true) { contentDescription = name },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        LanguageFlag(code, selected)
                        Text(code.uppercase(), Modifier.clearAndSetSemantics {},
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) selectedText else inactiveText)
                    }
                }
            }
        }
    }
}

/** Drawn France and UK flags render consistently on Android and iOS. */
@Composable
private fun LanguageFlag(code: String, selected: Boolean) {
    Surface(modifier = Modifier.alpha(if (selected) 1f else .6f).drawWithCache {
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        onDrawWithContent {
            if (selected) drawContent() else drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                drawContent()
                canvas.restore()
            }
        }
    }, shape = RoundedCornerShape(3.dp), border = BorderStroke(0.5.dp, Color.Black.copy(alpha = .12f))) {
        Canvas(Modifier.size(width = 24.dp, height = 18.dp).clearAndSetSemantics {}) {
            drawRect(Color.White)
            if (code == "fr") {
                drawRect(Color(0xFF002395), size = Size(size.width / 3f, size.height))
                drawRect(Color(0xFFED2939), topLeft = Offset(size.width * 2f / 3f, 0f), size = Size(size.width / 3f, size.height))
            } else {
                val red = Color(0xFFC8102E)
                val width = size.width
                val height = size.height
                drawRect(Color(0xFF012169))
                drawLine(Color.White, Offset.Zero, Offset(width, height), height / 5f)
                drawLine(Color.White, Offset(width, 0f), Offset(0f, height), height / 5f)
                // Counterchanged red diagonals preserve the Union Jack's asymmetric saltires.
                val diagonalHalves = Path().apply {
                    moveTo(width / 2f, height / 2f); lineTo(width, height / 2f); lineTo(width, height); close()
                    moveTo(width / 2f, height / 2f); lineTo(width / 2f, height); lineTo(0f, height); close()
                    moveTo(width / 2f, height / 2f); lineTo(0f, height / 2f); lineTo(0f, 0f); close()
                    moveTo(width / 2f, height / 2f); lineTo(width / 2f, 0f); lineTo(width, 0f); close()
                }
                clipPath(diagonalHalves) {
                    drawLine(red, Offset.Zero, Offset(width, height), height * 2f / 15f)
                    drawLine(red, Offset(width, 0f), Offset(0f, height), height * 2f / 15f)
                }
                drawRect(Color.White, Offset(width * 5f / 12f, 0f), Size(width / 6f, height))
                drawRect(Color.White, Offset(0f, height / 3f), Size(width, height / 3f))
                drawRect(red, Offset(width * .45f, 0f), Size(width / 10f, height))
                drawRect(red, Offset(0f, height * .4f), Size(width, height / 5f))
            }
        }
    }
}
