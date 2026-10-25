package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

internal const val MIN_REFRESH_LOADING_MILLIS = 1_000L
internal const val REFRESH_SUCCESS_MILLIS = 650L
internal const val REFRESH_ERROR_MILLIS = 900L
enum class PortfolioRefreshPhase { IDLE, LOADING, SUCCESS, ERROR }

/** Material owns the gesture and settling motion; content follows its distance into the revealed gap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioPullRefresh(model: PortfolioModel, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    val active = model.refreshPhase != PortfolioRefreshPhase.IDLE
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val distance = (72.dp + topInset) * state.distanceFraction.coerceIn(0f, 1.5f)
    val label = when (model.refreshPhase) {
        PortfolioRefreshPhase.LOADING -> "Refreshing…"
        PortfolioRefreshPhase.SUCCESS -> "Updated"
        PortfolioRefreshPhase.ERROR -> "Couldn’t refresh"
        PortfolioRefreshPhase.IDLE -> if (state.distanceFraction >= 1f) "Release to refresh" else "Pull to refresh"
    }
    Box(Modifier.fillMaxSize().clipToBounds().pullToRefresh(
        isRefreshing = active, state = state, enabled = !model.busy || active,
        threshold = 80.dp, onRefresh = model::refreshPortfolio,
    ).semantics {
        customActions = listOf(CustomAccessibilityAction("Refresh portfolio") {
            if (model.busy) false else { model.refreshPortfolio(); true }
        })
    }) {
        Box(Modifier.fillMaxSize().offset { IntOffset(0, distance.roundToPx()) }) { content() }
        if (state.distanceFraction > 0f) {
            Box(Modifier.fillMaxWidth().height(distance).clipToBounds().padding(top = topInset), contentAlignment = Alignment.Center) {
                Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (model.refreshPhase) {
                        PortfolioRefreshPhase.LOADING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        PortfolioRefreshPhase.SUCCESS -> AppIcon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        PortfolioRefreshPhase.ERROR -> AppIcon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                        PortfolioRefreshPhase.IDLE -> AppIcon(Icons.Outlined.ArrowDownward, null,
                            Modifier.size(24.dp).rotate(if (state.distanceFraction >= 1f) 180f else 0f))
                    }
                    AppText(label, style = MaterialTheme.typography.labelLarge,
                        color = if (model.refreshPhase == PortfolioRefreshPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
