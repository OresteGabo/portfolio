package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Decorative artwork is shared by iOS and Android and follows the active app theme. */
enum class EmptyArtwork(val icon: ImageVector) {
    PORTFOLIO(Icons.Outlined.AccountBalanceWallet),
    DOCUMENTS(Icons.Outlined.Description),
    SEARCH(Icons.Outlined.Search),
    CALENDAR(Icons.Outlined.Event),
    CHARTS(Icons.Outlined.InsertChartOutlined),
    BONDS(Icons.Outlined.AccountBalance),
    SHARES(Icons.Outlined.PieChartOutline),
    ACCOUNTS(Icons.Outlined.PeopleOutline),
    PROJECTION(Icons.Outlined.AutoGraph)
}

@Composable
fun EmptyState(
    title: String,
    artwork: EmptyArtwork,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    framed: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        color = if (framed) colors.primaryContainer.copy(alpha = .32f) else colors.surface
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 136.dp else 284.dp).clip(RoundedCornerShape(24.dp))) {
            // Quiet, oversized watermark: illustrative only, never a chart of invented data.
            Box(Modifier.align(Alignment.CenterEnd).offset(x = 26.dp).size(if (compact) 162.dp else 230.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width * .52f, size.height * .52f)
                    drawCircle(colors.primary.copy(alpha = .065f), size.minDimension * .46f, center,
                        style = Stroke(1.dp.toPx()))
                    drawCircle(colors.primary.copy(alpha = .035f), size.minDimension * .34f, center)
                    drawCircle(colors.primary.copy(alpha = .18f), 3.dp.toPx(), Offset(size.width * .17f, size.height * .22f))
                    drawCircle(colors.primary.copy(alpha = .12f), 5.dp.toPx(), Offset(size.width * .77f, size.height * .85f))
                }
                Icon(artwork.icon, contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(if (compact) 112.dp else 164.dp).rotate(-12f),
                    tint = colors.primary.copy(alpha = .09f))
            }
            Column(
                Modifier.padding(24.dp).align(if (compact) Alignment.CenterStart else Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!compact) Surface(shape = RoundedCornerShape(16.dp), color = colors.surface.copy(alpha = .85f)) {
                    Icon(artwork.icon, null, Modifier.padding(14.dp).size(28.dp), tint = colors.primary)
                }
                Text(title, modifier = Modifier.fillMaxWidth(if (compact) .74f else .86f),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                if (actionLabel != null && onAction != null) {
                    if (compact) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(actionLabel) }
                    else FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxWidth().height(136.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
    }
}
