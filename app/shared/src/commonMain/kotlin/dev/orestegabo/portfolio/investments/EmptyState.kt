package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Optional actions keep navigation and feature logic at the call site. */
data class EmptyStateAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

/** Shared artwork presets; callers may also supply any ImageVector directly. */
enum class EmptyArtwork(val icon: ImageVector) {
    PORTFOLIO(Icons.Outlined.AccountBalanceWallet),
    DOCUMENTS(Icons.Outlined.Description),
    SEARCH(Icons.Outlined.Search),
    CALENDAR(Icons.Outlined.Event),
    CHARTS(Icons.Outlined.InsertChartOutlined),
    BONDS(Icons.Outlined.AccountBalance),
    SHARES(Icons.Outlined.PieChartOutline),
    ACCOUNTS(Icons.Outlined.PeopleOutline),
    PROJECTION(Icons.Outlined.AutoGraph),
}

/** One visual language for empty results, first use and recoverable unavailable states.
 * Compact changes spacing and illustration size only. Framing is optional inside an existing card.
 * Strings are localized by AppText; financial values belong in FinancialText, not descriptions.
 */
@Composable
fun EmptyState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: EmptyStateAction? = null,
    secondaryAction: EmptyStateAction? = null,
    compact: Boolean = false,
    framed: Boolean = true,
    badgeIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val corner = if (compact) 24.dp else 28.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        border = if (framed) BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .5f)) else null,
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.background(Brush.verticalGradient(listOf(
                colors.primaryContainer.copy(alpha = .35f), colors.surfaceContainerLow,
            ))).padding(horizontal = if (compact) 20.dp else 24.dp, vertical = if (compact) 20.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            Box(Modifier.size(if (compact) 108.dp else 196.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val middle = Offset(size.width / 2, size.height / 2)
                    drawCircle(colors.primary.copy(alpha = .08f), size.width * .47f, middle, style = Stroke(1.dp.toPx()))
                    drawCircle(colors.primary.copy(alpha = .045f), size.width * .36f, middle)
                    drawCircle(colors.primary.copy(alpha = .25f), (if (compact) 3.dp else 4.dp).toPx(), Offset(size.width * .13f, size.height * .28f))
                    drawCircle(colors.primary.copy(alpha = .18f), 3.dp.toPx(), Offset(size.width * .82f, size.height * .79f))
                }
                Surface(
                    modifier = Modifier.size(if (compact) 64.dp else 108.dp),
                    shape = RoundedCornerShape(if (compact) 20.dp else 30.dp),
                    color = colors.surface, shadowElevation = 3.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(icon, null, Modifier.size(if (compact) 32.dp else 52.dp), tint = colors.primary)
                    }
                }
                if (badgeIcon != null) Surface(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .offset(x = if (compact) (-10).dp else (-25).dp, y = if (compact) (-10).dp else (-25).dp)
                        .size(if (compact) 28.dp else 38.dp),
                    shape = CircleShape, color = colors.primaryContainer,
                    border = BorderStroke(3.dp, colors.surfaceContainerLow),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(badgeIcon, null, Modifier.size(if (compact) 16.dp else 20.dp), tint = colors.onPrimaryContainer)
                    }
                }
            }
            AppText(title, modifier = Modifier.fillMaxWidth().semantics { heading() },
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (!description.isNullOrBlank()) AppText(description, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            action?.let { primary ->
                Button(onClick = primary.onClick, enabled = primary.enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
                    primary.icon?.let { AppIcon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                    AppText(primary.label, textAlign = TextAlign.Center)
                }
            }
            secondaryAction?.let { secondary ->
                TextButton(onClick = secondary.onClick, enabled = secondary.enabled) {
                    secondary.icon?.let { AppIcon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                    AppText(secondary.label, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Preset convenience overload. Both forms render through the same component. */
@Composable
fun EmptyState(
    title: String,
    artwork: EmptyArtwork,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    framed: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    description: String? = null,
) = EmptyState(title = title, icon = artwork.icon, modifier = modifier, compact = compact, framed = framed,
    description = description, action = if (actionLabel != null && onAction != null) EmptyStateAction(actionLabel, onAction) else null)

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxWidth().height(136.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
    }
}
