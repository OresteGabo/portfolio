package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class BondStatus(val value: String, val label: String) {
    ALL("all", "All"),
    ACTIVE("active", "Active"),
    SUBMITTED("submitted", "Submitted"),
    SOLD("sold", "Sold"),
    MATURED("matured", "Matured");

    override fun toString(): String = label
}

enum class InvestmentKind(val label: String) {
    ALL("all"),
    SHARES("shares"),
    BONDS("bonds");

    override fun toString(): String = label.replaceFirstChar { it.uppercase() }
}
@Composable
fun PortfolioScreen(
    model: PortfolioModel,
    uploadPending: (JsonRecord) -> Unit,
    openMissing: (JsonRecord) -> Unit,
    allMissing: () -> Unit,
    seeAll: () -> Unit,
    openCalendar: (LocalDate) -> Unit,
    detail: (JsonRecord) -> Unit,
    openNotifications: () -> Unit = {},
    unreadNotifications: Int = 0,
    openShare: (JsonRecord) -> Unit,
    addBond: () -> Unit = {},
    addShare: () -> Unit = {},
    editShareOrder: (JsonRecord) -> Unit = {},
    addTemporaryBond: () -> Unit = {}
) {
    if (!model.portfolioReady) {
        if (model.portfolioLoading) PortfolioLoadingSkeleton(openNotifications, unreadNotifications)
        else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText("Your portfolio", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (!model.isGuest) NotificationBell(unreadNotifications, openNotifications)
                }
                AppText((if (model.isGuest) model.guestStorageError else model.portfolioSyncError) ?: "Investments could not be loaded. Check your connection and try again.")
                Button(onClick = model::retryPortfolioSync) { AppText("Try again") }
            }
        }
        return
    }

    var kind by rememberSaveable { mutableStateOf(InvestmentKind.ALL) }
    var investmentMenuOpen by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(BondStatus.ALL) }

    val today = Today;
    val recentBonds = remember(model.portfolio, today) {
        model.portfolio.recentBonds(today)
    }

    val recentShares = remember(model.portfolio, today) {
        model.portfolio.recentShareCompanies(today)
    }

    val summary = remember(model.portfolio, today) {
        model.portfolio.summary(today)
    }

    val valuation = remember(model.portfolio, model.quotes, today) {
        model.portfolio.valuation(today, model.quotes)
    }
    val insights = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val payments = remember(model.portfolio) { investmentCalendarEvents(emptyList(), model.portfolio) }

    PortfolioPullRefresh(model) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                PortfolioHero(
                    !model.isGuest,
                    if (model.sampleGuest) "Fictional sample · Not your investments" else if (model.isGuest) "Guest · On this device" else "Your way forward",
                    valuation,
                    topInset,
                    openNotifications,
                    unreadNotifications,
                    model.quoteRefreshFailed || model.quotes?.optJSONObject("source")
                        ?.optBoolean("stale") == true,
                    model.quotes?.optJSONObject("source")?.text("publishedAt").orEmpty().take(10)
                ) {
                    Column(
                        Modifier.fillMaxWidth().smoothContentSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLoadingLine(model.busy && model.refreshPhase == PortfolioRefreshPhase.IDLE)
                        model.error?.let { Notice(it, true) { model.error = null } }
                        model.message?.let { Notice(it, false) { model.message = null } }
                    }
                }
            }
            if (model.isGuest) item { GuestWorkspaceStatus(model, openNotifications) }
            if (model.isGuest && !model.sampleGuest) item {
                Inset {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !model.busy,
                            onClick = addBond
                        ) { AppText("Add bond purchase") }
                        OutlinedButton(
                            enabled = !model.busy,
                            onClick = addShare
                        ) { AppText("Add share purchase") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            "Asset allocation",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(enabled = insights.canScrollBackward, onClick = {
                            scope.launch {
                                val index = (insights.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                if (reducedMotion) insights.scrollToItem(index) else insights.animateScrollToItem(
                                    index
                                )
                            }
                        }) { AppIcon(Icons.Outlined.ChevronLeft, "Previous insight") }
                        IconButton(enabled = insights.canScrollForward, onClick = {
                            scope.launch {
                                val index =
                                    (insights.firstVisibleItemIndex + 1).coerceAtMost(summary.size - 1)
                                if (reducedMotion) insights.scrollToItem(index) else insights.animateScrollToItem(
                                    index
                                )
                            }
                        }) { AppIcon(Icons.Outlined.ChevronRight, "Next insight") }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = (maxWidth - 56.dp).coerceAtMost(340.dp)
                        LazyRow(
                            state = insights,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            summary.forEach { total ->
                                item(key = "allocation:${total.currency}") {
                                    AllocationCard(
                                        total,
                                        Modifier.width(cardWidth)
                                    )
                                }
                                item(key = "income:${total.currency}") {
                                    val coupons = payments.filter {
                                        it.category == CalendarCategory.COUPON && it.date >= Today && it.record.text(
                                            "currency"
                                        ) == total.currency
                                    }
                                    CouponIncomeCard(
                                        total,
                                        model.portfolio,
                                        coupons,
                                        Modifier.width(cardWidth),
                                        openCalendar
                                    )
                                }
                            }
                            item(key = "monthly-target") {
                                MonthlyTargetCard(
                                    model,
                                    Modifier.width(cardWidth)
                                )
                            }
                        }
                    }
                }
            }
            if (!model.isGuest && model.statementItems.isNotEmpty()) {
                item {
                    Inset {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText(
                                "Missing documents",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (model.statementItems.size > 3) TextButton(onClick = allMissing) {
                                AppText(
                                    "See all"
                                )
                            }
                        }
                    }
                }
                items(model.statementItems.take(3), key = { "missing:${it.text("id")}" }) { row ->
                    AppMovingItem {
                        Inset { MissingStatementTile(row) { openMissing(row) } }
                    }
                }
            }
            item {
                Column(
                    Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AppText(
                                "Your investments",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            AppText(
                                "Past 12 months",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = seeAll) { AppText("See all") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ChoiceRow(InvestmentKind.entries, kind) { kind = it } }
                        if (model.user != null) Box {
                            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface,
                                border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))) {
                                IconButton(onClick = { investmentMenuOpen = true }) {
                                    AppIcon(Icons.Outlined.MoreVert, "Investment actions",tint=MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(expanded = investmentMenuOpen, onDismissRequest = { investmentMenuOpen = false },
                                modifier=Modifier.widthIn(min=280.dp,max=300.dp),shape=RoundedCornerShape(22.dp),
                                containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=8.dp,
                                border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.12f))) {
                                Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),
                                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    AppText("Add pending investment",modifier=Modifier.semantics { heading() },
                                        style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    AppText("Confirm later with your broker’s PDF.",style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                                DropdownMenuItem(text = { AppText("Bond",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,maxLines=2) },
                                    leadingIcon = { InvestmentActionIcon(Icons.Outlined.AccountBalance) },
                                    trailingIcon = { AppIcon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant) },
                                    modifier=Modifier.heightIn(min=68.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp),enabled = !model.busy,
                                    onClick = { investmentMenuOpen = false; addTemporaryBond() })
                                HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                                DropdownMenuItem(text = { AppText("Shares",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,maxLines=2) },
                                    leadingIcon = { InvestmentActionIcon(Icons.Outlined.BarChart) },
                                    trailingIcon = { AppIcon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant) },
                                    modifier=Modifier.heightIn(min=68.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp),enabled = !model.busy,
                                    onClick = { investmentMenuOpen = false; editShareOrder(JsonRecord()) })
                            }
                        }
                    }
                }
            }
            if (model.busy && model.refreshPhase == PortfolioRefreshPhase.IDLE && recentBonds.isEmpty() && recentShares.isEmpty() && model.shareOrders.isEmpty()) {
                item { LoadingState() }
            } else {
                if (kind != InvestmentKind.SHARES) {
                    if (kind == InvestmentKind.ALL) item { SectionLabel("Bonds") }
                    if (recentBonds.isEmpty()) item {
                        Inset {
                            EmptyState(
                                "No bonds in the past year",
                                EmptyArtwork.BONDS,
                                compact = true
                            )
                        }
                    }

                    if (kind == InvestmentKind.BONDS && recentBonds.isNotEmpty()) {
                        item {
                            Inset {
                                ChoiceRow(BondStatus.entries, status) { status = it }
                            }
                        }
                    }

                    val bonds = recentBonds.filter {
                        kind != InvestmentKind.BONDS || status == BondStatus.ALL || it.text("status") == status.value
                    }

                    if (bonds.isEmpty() && recentBonds.isNotEmpty()) {
                        item {
                            Inset {
                                EmptyState(
                                    "No ${displayRecordValue(status.value)} bonds",
                                    EmptyArtwork.SEARCH,
                                    compact = true,
                                    actionLabel = "Show all",
                                    onAction = { status = BondStatus.ALL }
                                )
                            }
                        }
                    }

                    items(bonds, key = { "bond:${it.text("id")}" }) { purchase ->
                        AppMovingItem {
                            Inset {
                                BondPurchaseTile(model, purchase) {
                                    detail(purchase)
                                }
                            }
                        }
                    }
                }

                if (kind != InvestmentKind.BONDS) {
                    if (kind == InvestmentKind.ALL) item { SectionLabel("Shares") }
                    val pendingShares = if (model.user != null) model.shareOrders else emptyList()
                    items(pendingShares, key = { "share-order:${it.text("id")}" }) { order ->
                        AppMovingItem {
                            Inset { SharePurchaseTile(order) { editShareOrder(order) } }
                        }
                    }
                    if (recentShares.isEmpty() && pendingShares.isEmpty()) item {
                        Inset {
                            EmptyState(
                                "No shares in the past year",
                                EmptyArtwork.SHARES,
                                compact = true
                            )
                        }
                    }
                    items(recentShares, key = { "share:${it.text("id")}" }) { holding ->
                        AppMovingItem {
                            Inset {
                                SharePurchaseTile(holding) { openShare(holding) }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun InvestmentActionIcon(icon: ImageVector) {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.08f)) {
        Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
            AppIcon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun Inset(content: @Composable () -> Unit) { Box(Modifier.padding(horizontal = 20.dp)) { content() } }
@Composable private fun SectionLabel(text: String) {
    AppText(text, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Modifier.portfolioStatusBarFade(enabled: Boolean): Modifier {
    if (!enabled) return this
    val inset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val background = MaterialTheme.colorScheme.background
    return drawWithContent {
        drawContent()
        if (inset > 0.dp) {
            val height = (inset + 12.dp).toPx()
            drawRect(Brush.verticalGradient(
                0f to background.copy(alpha = .8f),
                .75f to background.copy(alpha = .6f),
                1f to Color.Transparent, endY = height), size = Size(size.width, height))
        }
    }
}
