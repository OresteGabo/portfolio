package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.orestegabo.portfolio.design.StructColors
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.kigali_hero
import kotlin.math.roundToInt

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
        model.portfolio.recentShares(today)
    }

    val summary = remember(model.portfolio, today) {
        model.portfolio.summary(today)
    }

    val valuation = remember(model.portfolio, model.quotes, today) {
        model.portfolio.valuation(today, model.quotes)
    }
    //val recentBonds = model.portfolio.recentBonds(Today)
    //val recentShares = model.portfolio.recentShares(Today)
    //val summary = model.portfolio.summary(Today)
    //val valuation = model.portfolio.valuation(Today, model.quotes)
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
                    if (model.sampleGuest) "Fictional sample · Not your investments" else if (model.isGuest) "Guest · On this device" else "Rwanda investments",
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
                                    AppText("Pending investment",modifier=Modifier.semantics { heading() },
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
private fun PortfolioHero(showNotifications: Boolean, subtitle: String, totals: List<CurrencyValuation>, topInset: Dp, openNotifications: () -> Unit, unreadNotifications: Int, stale: Boolean, priceDate: String, notice: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(300.dp + topInset)) {
            Image(painterResource(Res.drawable.kigali_hero), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                alignment = Alignment.Center)
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        listOf(
                            StructColors.Dashboard.imageScrimStart,
                            StructColors.Dashboard.imageScrimEnd
                        )
                    )
                )
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, background), startY = 100f)))
            ImigongoMotif(
                Modifier.align(Alignment.CenterEnd).size(230.dp).offset(x = 52.dp, y = 24.dp),
                StructColors.Dashboard.gold.copy(alpha = .26f)
            )
        }
        Column(Modifier.fillMaxWidth().smoothContentSize().padding(start = 20.dp, end = 20.dp, top = 20.dp + topInset), verticalArrangement = Arrangement.spacedBy(38.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = StructColors.Dashboard.logoBacking
                    ) {
                        StructMark(Modifier.padding(7.dp).size(40.dp), onDark = false)
                    }
                    Column(Modifier.weight(1f)) {
                        AppText(
                            "Struct Capital",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = StructColors.Dashboard.onImage
                        )
                        AppText(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = StructColors.Dashboard.onImage.copy(alpha = .75f)
                        )
                    }
                    if (showNotifications) NotificationBell(
                        unreadNotifications,
                        openNotifications,
                        tint = StructColors.Dashboard.onImage
                    )
                }
                notice()
            }
            if (totals.isEmpty()) {
                Spacer(Modifier.height(46.dp))
            } else totals.forEach { total -> PortfolioMoneyCard(total, stale, priceDate) }
        }
    }
}

/** A geometric line motif inspired by Rwanda’s traditional imigongo patterns. */
@Composable
private fun ImigongoMotif(modifier: Modifier, color: Color) {
    Canvas(modifier.clipToBounds()) {
        val step = size.width / 3f
        for (row in -1..3) for (column in -1..3) {
            val cx = column * step + if (row % 2 == 0) 0f else step / 2
            val cy = row * step
            for (inset in 0..2) {
                val radius = step * (.46f - inset * .105f)
                val path = Path().apply {
                    moveTo(cx, cy - radius); lineTo(cx + radius, cy)
                    lineTo(cx, cy + radius); lineTo(cx - radius, cy); close()
                }
                drawPath(path, color, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PortfolioMoneyCard(valuation: CurrencyValuation, stale: Boolean, priceDate: String) {
    val total = valuation.cost
    val priceLabel = when {
        valuation.unpricedShares > 0 && valuation.pricedShares == 0 -> "Shares at purchase cost"
        valuation.unpricedShares > 0 -> "Some shares at purchase cost"
        valuation.pricedShares > 0 -> if (stale) "Cached closing prices" else "Latest closing prices"
        else -> "Bonds at principal value"
    }
    val priceBasis = localized(priceLabel) + if (valuation.pricedShares > 0 && calendarDate(priceDate) != null) " · $priceDate" else ""
    var showBasis by remember { mutableStateOf(false) }

    if (showBasis) {
        AlertDialog(
            onDismissRequest = { showBasis = false },
            title = { AppText("About this estimate") },
            text = {
                AppText("$priceBasis\n\nTracked investments, including pending requests at recorded amounts. Bonds use principal value; shares use the latest available RSE closing price. Unpriced shares use purchase cash cost. Cash balances, liabilities, dividends and selling fees are excluded. Annual coupons are shown separately.")
            },
            confirmButton = {
                TextButton(onClick = { showBasis = false }) { AppText("Done") }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        contentColor = StructColors.Dashboard.onImage,
        border = BorderStroke(1.dp, StructColors.Dashboard.gold.copy(alpha = .24f))
    ) {
        Box(Modifier.background(Brush.linearGradient(StructColors.Dashboard.worthGradient))) {
            ImigongoMotif(
                Modifier.align(Alignment.CenterEnd).size(200.dp).offset(x = 75.dp),
                StructColors.Dashboard.gold.copy(alpha = .075f)
            )
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp) // Slightly better breathing room
            ) {
                // Top section: Net Worth Header & Primary Value
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppText(
                            "Estimated net worth",
                            style = MaterialTheme.typography.labelLarge,
                            color = StructColors.Dashboard.worthLabel
                        )
                        // Improved touch target semantics and padding alignment
                        IconButton(
                            onClick = { showBasis = true },
                            modifier = Modifier.size(36.dp) // Compact yet touch-friendly
                        ) {
                            AppIcon(
                                Icons.Outlined.Info,
                                contentDescription = "Valuation basis details",
                                tint = StructColors.Dashboard.worthLabel,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    FinancialText(
                        money(valuation.estimatedNetWorth, total.currency),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 16.sp,
                            maxFontSize = 24.sp,
                            stepSize = .5.sp
                        )
                    )
                }

                HorizontalDivider(color = StructColors.Dashboard.gold.copy(alpha = .15f))

                // Bottom metrics: Clean layout switch without heavy measurement passes
                val isCompact = LocalDensity.current.fontScale > 1.3f

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoneyMetric(
                            "Bond principal",
                            total.bondFaceValue,
                            total.currency,
                            Icons.Outlined.AccountBalance,
                            Modifier.fillMaxWidth()
                        )
                        MoneyMetric(
                            "RSE equity value",
                            valuation.shareValue,
                            total.currency,
                            Icons.Outlined.BarChart,
                            Modifier.fillMaxWidth()
                        )
                        MoneyMetric(
                            "Annual net coupon",
                            total.netAnnualCoupon,
                            total.currency,
                            Icons.Outlined.Percent,
                            Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MoneyMetric(
                            "Bond principal",
                            total.bondFaceValue,
                            total.currency,
                            Icons.Outlined.AccountBalance,
                            Modifier.weight(1f)
                        )
                        MoneyMetric(
                            "RSE equity value",
                            valuation.shareValue,
                            total.currency,
                            Icons.Outlined.BarChart,
                            Modifier.weight(1f)
                        )
                        MoneyMetric(
                            "Annual net coupon",
                            total.netAnnualCoupon,
                            total.currency,
                            Icons.Outlined.Percent,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyMetric(label: String, value: Double, currency: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AppIcon(icon, null, Modifier.size(20.dp), tint = StructColors.Dashboard.gold)
        AppText(
            label,
            style = MaterialTheme.typography.labelSmall,
            minLines = 2,
            color = StructColors.Dashboard.worthMetricLabel
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(
                currency,
                style = MaterialTheme.typography.labelSmall,
                color = StructColors.Dashboard.worthMetricLabel,
                maxLines = 1, softWrap = false)
            FinancialText(money(value, currency).removePrefix("$currency "), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 14.sp, stepSize = .5.sp))
        }
    }
}

private enum class InsightStyle { ALLOCATION, INCOME, PAYMENT }

@Composable
private fun insightAccent(style: InsightStyle): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return when (style) {
        InsightStyle.ALLOCATION -> StructColors.Dashboard.allocationAccent.resolve(dark)
        InsightStyle.INCOME -> StructColors.Dashboard.incomeAccent.resolve(dark)
        InsightStyle.PAYMENT -> StructColors.Dashboard.paymentAccent.resolve(dark)
    }
}

@Composable
private fun InsightCard(title: String, style: InsightStyle, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = insightAccent(style)
    val palette = when (style) {
        InsightStyle.ALLOCATION -> StructColors.Dashboard.allocationGradient.resolve(dark)
        InsightStyle.INCOME -> StructColors.Dashboard.incomeGradient.resolve(dark)
        InsightStyle.PAYMENT -> StructColors.Dashboard.paymentGradient.resolve(dark)
    }
    val foreground = StructColors.Dashboard.insightForeground.resolve(dark)
    // One explicit shared height, scaled for accessibility, keeps every carousel edge aligned.
    val height = 236.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(modifier.height(height), shape = RoundedCornerShape(26.dp), color = Color.Transparent, contentColor = foreground,
        border = BorderStroke(1.dp, accent.copy(alpha = if (dark) .2f else .16f))) {
        Box(Modifier.background(Brush.linearGradient(palette))) {
            Canvas(Modifier.matchParentSize().clipToBounds()) {
                val center = Offset(size.width * .97f, size.height * .12f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .14f), Color.Transparent), center = center,
                    radius = size.width * .8f), radius = size.width * .8f, center = center)
                if (style == InsightStyle.PAYMENT) {
                    for (index in 0..3) {
                        val path = Path().apply {
                            moveTo(size.width * .45f, size.height + index * 18.dp.toPx())
                            cubicTo(size.width * .6f, size.height * .3f, size.width * .9f, size.height * 1.1f,
                                size.width * 1.1f, size.height * .35f + index * 14.dp.toPx())
                        }
                        drawPath(path, accent.copy(alpha = .08f), style = Stroke(1.dp.toPx()))
                    }
                } else {
                    for (index in 0..3) drawCircle(accent.copy(alpha = .055f), size.width * (.3f + index * .1f),
                        Offset(size.width * 1.02f, size.height * .95f), style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                content()
            }
        }
    }
}

@Composable
private fun AllocationCard(total: CurrencyInvestment, modifier: Modifier) {
    val fraction = if (total.investedCash > 0) (total.shareCashCost / total.investedCash).toFloat().coerceIn(0f, 1f) else 0f
    val foreground = LocalContentColor.current
    val hidden = LocalNumbersHidden.current
    InsightCard("Cash allocation", InsightStyle.ALLOCATION, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (hidden) HiddenFinancialChart(Modifier.size(112.dp)) else CaptureRedactionRegion(Modifier.size(112.dp), masked = { HiddenFinancialChart(Modifier.fillMaxSize()) }) { Box(Modifier.fillMaxSize().semantics { contentDescription = localized("Shares ${percent(fraction.toDouble())}, bonds ${percent(if (total.investedCash > 0) 1.0 - fraction else 0.0)} of cash cost") }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                    val stroke = Stroke(13.dp.toPx(), cap = StrokeCap.Butt)
                    drawArc(foreground.copy(alpha = .08f), -90f, 360f, false, style = stroke)
                    if (total.investedCash > 0) {
                        drawArc(
                            StructColors.Dashboard.allocationGreen,
                            -90f,
                            360f,
                            false,
                            style = stroke
                        )
                        drawArc(
                            Brush.sweepGradient(
                                listOf(
                                    StructColors.Dashboard.gold,
                                    StructColors.Dashboard.allocationHighlight,
                                    StructColors.Dashboard.gold
                                )
                            ), -90f, fraction * 360f, false, style = stroke
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText(if (total.investedCash > 0) "${(maxOf(fraction, 1f - fraction) * 100).roundToInt()}%" else "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    AppText(if (total.investedCash <= 0) "No cost" else if (fraction >= .5f) "Shares" else "Bonds", style = MaterialTheme.typography.labelSmall)
                }
            }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AllocationLegend(
                    "Shares",
                    total.shareCashCost,
                    total.currency,
                    StructColors.Dashboard.gold
                )
                AllocationLegend(
                    "Bonds",
                    total.bondCashCost,
                    total.currency,
                    StructColors.Dashboard.allocationGreen
                )
            }
        }
        HorizontalDivider(color = insightAccent(InsightStyle.ALLOCATION).copy(alpha = .2f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Total cash cost", style = MaterialTheme.typography.labelMedium)
            FinancialText(money(total.investedCash, total.currency), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun AllocationLegend(label: String, cost: Double, currency: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(7.dp)) { drawCircle(color) }
            AppText(label, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = .65f))
        }
        FinancialText(money(cost, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InsightIcon(icon: ImageVector, accent: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = .12f)) {
        AppIcon(icon, null, Modifier.padding(13.dp).size(25.dp), tint = accent)
    }
}

@Composable
private fun CouponIncomeCard(
    total: CurrencyInvestment,
    portfolio: PortfolioSnapshot,
    coupons: List<InvestmentCalendarEvent>,
    modifier: Modifier,
    openCalendar: (LocalDate) -> Unit,
) {
    val tracks = portfolio.investmentBonds(Today).filter { it.text("currency") == total.currency }
        .mapNotNull { it.terms()?.let { terms -> trackBond(terms, Today) } }
    val accent = insightAccent(InsightStyle.INCOME)
    val next = coupons.firstOrNull()
    InsightCard("Coupon income", InsightStyle.INCOME, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Annual net" to total.netAnnualCoupon, "Accrued" to tracks.sumOf { it.accruedCoupon }).forEach { (label, amount) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(label, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = .68f))
                    FinancialText(money(amount, total.currency), Modifier,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(color = accent.copy(alpha = .2f))
        if (next == null) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InsightIcon(Icons.Outlined.EventAvailable, accent)
                AppText("No upcoming coupons", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val amount = coupons.filter { it.date == next.date }.sumOf { it.amount ?: 0.0 }
            Row(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = localized("Open coupon in calendar")) { openCalendar(next.date) }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = .12f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(next.date.monthLabel().take(3).uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
                        AppText(next.date.day.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText("Next coupon · ${next.date.year}", style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = .68f))
                    FinancialText(money(amount, total.currency), Modifier,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                AppIcon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = accent)
            }
        }
    }
}

/** The photo scrolls; this light/dark veil keeps system icons legible above every list item. */
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

@Composable
private fun MonthlyTargetCard(model: PortfolioModel, modifier: Modifier) {
    val progress = model.monthlyTargetProgress()
    InsightCard("Monthly target", InsightStyle.PAYMENT, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FinancialText(money(model.monthlyInvestmentTarget.toDouble(), "RWF"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            AppIcon(if (progress?.achieved == true) Icons.Outlined.CheckCircle else Icons.Outlined.Flag, null)
        }
        if (progress == null) {
            AppLoadingLine(model.busy, reserveSpace = true)
            if (!model.busy) AppText("Progress unavailable", style = MaterialTheme.typography.bodySmall)
        }
        else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!LocalNumbersHidden.current) CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = { Spacer(Modifier.fillMaxSize()) }) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            }
            else Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    AppText("Invested", style = MaterialTheme.typography.labelSmall)
                    FinancialText(money(progress.invested, "RWF"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    AppText("Remaining", style = MaterialTheme.typography.labelSmall)
                    FinancialText(money(progress.remaining, "RWF"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            AppText(if (progress.achieved) "${Today.monthLabel()} · Target reached" else Today.monthLabel(), style = MaterialTheme.typography.labelLarge)
            if (progress.pending > 0) FinancialText("${money(progress.pending, "RWF")} pending", style = MaterialTheme.typography.labelSmall)
        }
    }
}
