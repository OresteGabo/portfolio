package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.design.StructColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSection(title: String, footnote: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(title, Modifier.padding(horizontal = 16.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
        footnote?.let { AppText(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary, toggleState: Boolean? = null, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier) {
    ListItem(headlineContent = { AppText(title, color = if (enabled) tint.takeIf { it == MaterialTheme.colorScheme.error } ?: MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant) }, supportingContent = subtitle?.let { { FinancialText(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = { AppIcon(icon, null, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = trailing ?: { AppIcon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().then(if (toggleState != null) Modifier.toggleable(toggleState, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)))
}

@Composable internal fun SettingsDivider() { HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) }

@Composable
internal fun AppearanceOptions(selected: String, choose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
            val active = selected == value
            Surface(Modifier.weight(1f).semantics { contentDescription = localized(label) }.selectable(active, role = Role.RadioButton, onClick = { choose(value) }),
                shape = RoundedCornerShape(16.dp),
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (active) 2.dp else 1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(width = 44.dp, height = 62.dp)) {
                        val cream = StructColors.AppearancePreview.cream
                        val ink = StructColors.AppearancePreview.ink
                        val accent = StructColors.AppearancePreview.accent
                        drawRoundRect(if (value == "dark") ink else cream, cornerRadius = CornerRadius(7.dp.toPx()))
                        if (value == "system") drawRoundRect(ink, topLeft = Offset(size.width / 2, 0f),
                            size = Size(size.width / 2, size.height), cornerRadius = CornerRadius(5.dp.toPx()))
                        drawRoundRect(accent, Offset(size.width * .15f, size.height * .2f), Size(size.width * .7f, size.height * .24f), CornerRadius(3.dp.toPx()))
                        repeat(3) { index ->
                            drawRoundRect(if (value == "dark") cream.copy(alpha = .3f) else ink.copy(alpha = .2f),
                                Offset(size.width * .15f, size.height * (.55f + index * .12f)),
                                Size(size.width * .7f, size.height * .045f), CornerRadius(2.dp.toPx()))
                        }
                    }
                    AppText(label, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
