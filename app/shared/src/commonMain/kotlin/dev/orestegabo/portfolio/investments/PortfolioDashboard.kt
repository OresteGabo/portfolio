package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.*

private val DashboardGold = Color(0xFFECC75F)
private val AllocationGreen = Color(0xFF739A87)

@Composable
fun PortfolioScreen(model: PortfolioModel, detail: (JsonRecord) -> Unit) {
    var kind by rememberSaveable { mutableStateOf("all") }
    var status by rememberSaveable { mutableStateOf("all") }
    val summary = model.portfolio.summary(Today)
    val insights = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val payments = remember(model.portfolio) { investmentCalendarEvents(emptyList(), model.portfolio) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PortfolioHero(summary, model.busy, model::refresh) }
        if (summary.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Asset allocation", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(enabled = insights.canScrollBackward, onClick = {
                        scope.launch { insights.animateScrollToItem((insights.firstVisibleItemIndex - 1).coerceAtLeast(0)) }
                    }) { Icon(Icons.Outlined.ChevronLeft, "Previous insight") }
                    IconButton(enabled = insights.canScrollForward, onClick = {
                        scope.launch { insights.animateScrollToItem((insights.firstVisibleItemIndex + 1).coerceAtMost(summary.size * 3 - 1)) }
                    }) { Icon(Icons.Outlined.ChevronRight, "Next insight") }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = (maxWidth - 56.dp).coerceAtMost(340.dp)
                    LazyRow(state = insights, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        summary.forEach { total ->
                            item(key = "allocation:${total.currency}") { AllocationCard(total, Modifier.width(cardWidth)) }
                            item(key = "income:${total.currency}") { CouponIncomeCard(total, model.portfolio, Modifier.width(cardWidth)) }
                            item(key = "next:${total.currency}") {
                                val upcoming = payments.filter { it.date >= Today && it.record.text("currency") == total.currency }
                                NextPaymentCard(upcoming, total.currency, Modifier.width(cardWidth))
                            }
                        }
                    }
                }
            }
        }
        item { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your investments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ChoiceRow(listOf("all", "shares", "bonds"), kind) { kind = it }
        } }
        if (model.busy && model.purchases.isEmpty() && model.holdings.isEmpty()) item { LoadingState() }
        else {
            if (kind != "shares") {
                if (kind == "all") item { SectionLabel("Bonds") }
                if (model.purchases.isEmpty()) item { Inset { EmptyState("No bonds yet", EmptyArtwork.BONDS, compact = true) } }
                if (kind == "bonds" && model.purchases.isNotEmpty()) item { Inset { ChoiceRow(listOf("all", "active", "submitted", "sold", "matured"), status) { status = it } } }
                val bonds = model.purchases.filter { kind != "bonds" || status == "all" || it.text("status") == status }
                if (bonds.isEmpty() && model.purchases.isNotEmpty()) item { Inset {
                    EmptyState("No $status bonds", EmptyArtwork.SEARCH, compact = true, actionLabel = "Show all", onAction = { status = "all" })
                } }
                items(bonds, key = { "bond:${it.text("id")}" }) { purchase -> Inset { BondPurchaseTile(purchase) { detail(purchase) } } }
            }
            if (kind != "bonds") {
                if (kind == "all") item { SectionLabel("Shares") }
                if (model.holdings.isEmpty()) item { Inset { EmptyState("No shares yet", EmptyArtwork.SHARES, compact = true) } }
                items(model.holdings, key = { "share:${it.text("id")}" }) { holding -> Inset { SharePurchaseTile(holding) } }
            }
        }
    }
}

@Composable private fun Inset(content: @Composable () -> Unit) { Box(Modifier.padding(horizontal = 20.dp)) { content() } }
@Composable private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PortfolioHero(totals: List<CurrencyInvestment>, busy: Boolean, refresh: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Image(painterResource(Res.drawable.kigali_hero), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                alignment = Alignment.Center)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xE6101814), Color(0x65202B21)))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, background), startY = 100f)))
            ImigongoMotif(Modifier.align(Alignment.CenterEnd).size(230.dp).offset(x = 52.dp, y = 24.dp), DashboardGold.copy(alpha = .26f))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(38.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFFFAF7E8)) {
                    Image(painterResource(Res.drawable.gabo_mark), "Gabo logo", Modifier.padding(7.dp).size(40.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Portfolio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Rwanda investments", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f))
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = .22f)) {
                    IconButton(onClick = refresh, enabled = !busy) { Icon(Icons.Outlined.Sync, "Sync portfolio", tint = Color.White) }
                }
            }
            if (totals.isEmpty()) {
                Spacer(Modifier.height(46.dp))
            } else totals.forEach { total -> PortfolioMoneyCard(total) }
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
private fun PortfolioMoneyCard(total: CurrencyInvestment) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent, contentColor = Color.White,
        border = BorderStroke(1.dp, DashboardGold.copy(alpha = .24f))) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xF02E321C), Color(0xF24D4117), Color(0xFA20251B))))) {
            ImigongoMotif(Modifier.align(Alignment.CenterEnd).size(200.dp).offset(x = 75.dp), DashboardGold.copy(alpha = .075f))
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total cash cost", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE8E4D3))
                    Text(money(total.investedCash, total.currency), style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, maxLines = 2)
                }
                HorizontalDivider(color = DashboardGold.copy(alpha = .2f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoneyMetric("Bond principal", money(total.bondFaceValue, total.currency), Icons.Outlined.AccountBalance, Modifier.weight(1f))
                    MoneyMetric("RSE equity cost", money(total.shareCashCost, total.currency), Icons.Outlined.BarChart, Modifier.weight(1f))
                    MoneyMetric("Annual net coupon", money(total.netAnnualCoupon, total.currency), Icons.Outlined.Percent, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MoneyMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = DashboardGold)
        Text(label, style = MaterialTheme.typography.labelSmall, minLines = 2, color = Color(0xFFDAD7C6))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InsightCard(title: String, currency: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.heightIn(min = 228.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(currency, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun AllocationCard(total: CurrencyInvestment, modifier: Modifier) {
    val fraction = if (total.investedCash > 0) (total.shareCashCost / total.investedCash).toFloat().coerceIn(0f, 1f) else 0f
    val ringBackground = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f)
    InsightCard("Cash allocation", total.currency, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(112.dp).semantics { contentDescription = "Shares ${percent(fraction.toDouble())}, bonds ${percent(if (total.investedCash > 0) 1.0 - fraction else 0.0)} of cash cost" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                    val stroke = Stroke(13.dp.toPx(), cap = StrokeCap.Butt)
                    drawArc(ringBackground, -90f, 360f, false, style = stroke)
                    if (total.investedCash > 0) {
                        drawArc(AllocationGreen, -90f, 360f, false, style = stroke)
                        drawArc(DashboardGold, -90f, fraction * 360f, false, style = stroke)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (total.investedCash > 0) "${(maxOf(fraction, 1f - fraction) * 100).roundToInt()}%" else "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (total.investedCash <= 0) "No cost" else if (fraction >= .5f) "Shares" else "Bonds", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AllocationLegend("Shares", total.shareCashCost, total.currency, DashboardGold)
                AllocationLegend("Bonds", total.bondCashCost, total.currency, AllocationGreen)
            }
        }
    }
}

@Composable private fun AllocationLegend(label: String, cost: Double, currency: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(7.dp)) { drawCircle(color) }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(money(cost, currency), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CouponIncomeCard(total: CurrencyInvestment, portfolio: PortfolioSnapshot, modifier: Modifier) {
    val tracks = portfolio.purchases.filter { it.text("currency") == total.currency && it.text("status") == "active" }
        .mapNotNull { it.terms()?.let { terms -> trackBond(terms, Today) } }
    InsightCard("Coupon income", total.currency, modifier) {
        Icon(Icons.Outlined.Savings, null, tint = MaterialTheme.colorScheme.primary)
        Text(money(total.netAnnualCoupon, total.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Annual net coupon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Fact("Accrued coupon", money(tracks.sumOf { it.accruedCoupon }, total.currency))
    }
}

@Composable
private fun NextPaymentCard(events: List<InvestmentCalendarEvent>, currency: String, modifier: Modifier) {
    val next = events.firstOrNull()
    InsightCard("Next payment", currency, modifier) {
        if (next == null) EmptyState("No upcoming payments", EmptyArtwork.CALENDAR, compact = true, framed = false)
        else {
            val sameDay = events.filter { it.date == next.date }
            Icon(Icons.Outlined.EventAvailable, null, tint = MaterialTheme.colorScheme.primary)
            Text(money(sameDay.sumOf { it.amount ?: 0.0 }, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${next.date.day} ${next.date.monthLabel()} ${next.date.year}", style = MaterialTheme.typography.titleSmall)
            Text(if (sameDay.size == 1) next.title else "${sameDay.size} payments", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Expected · confirm receipt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
