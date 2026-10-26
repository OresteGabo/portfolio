package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Native delivery only; accounts, unread state and all financial calculations live in Spring. */
object NativePushBridge {
    var registration by mutableStateOf<JsonRecord?>(null)
        private set
    var available by mutableStateOf(false)
        private set
    var allowed by mutableStateOf(false)
        private set
    var inboxRequest by mutableIntStateOf(0)
        private set
    var updateRevision by mutableIntStateOf(0)
        private set
    fun priceUpdate() { updateRevision++ }
    private var permissionAction: (() -> Unit)? = null
    fun install(available: Boolean, allowed: Boolean, requestPermission: () -> Unit) {
        this.available=available;this.allowed=allowed;permissionAction=requestPermission
    }
    fun permissionChanged(allowed: Boolean) { this.allowed=allowed }
    fun register(deviceId: String, platform: String, token: String, environment: String) {
        registration=JsonRecord().put("deviceId",deviceId).put("platform",platform).put("token",token).put("environment",environment)
    }
    fun openInbox() { inboxRequest++ }
    fun requestPermission() { permissionAction?.invoke() }
}

@Composable
fun StockNotificationBell(unread: Int, open: () -> Unit) {
    IconButton(onClick=open) {
        BadgedBox(badge={ if(unread>0) Badge { AppText(if(unread>99) "99+" else unread.toString()) } }) {
            AppIcon(Icons.Outlined.Notifications, "Price notifications")
        }
    }
}

@Composable
fun StockNotificationsScreen(model: PortfolioModel, openStocks: () -> Unit) {
    LaunchedEffect(Unit) { model.refreshNotifications() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(model.notifications.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Price changes",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                IconButton(onClick={ model.refreshNotifications() },enabled=!model.notificationsLoading) { AppIcon(Icons.Outlined.Refresh,"Refresh") }
            }
        }
        if(model.notificationsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(model.notifications.isNotEmpty() && NativePushBridge.available && !NativePushBridge.allowed) item {
            OutlinedButton(onClick=NativePushBridge::requestPermission,modifier=Modifier.fillMaxWidth()) { AppText("Enable phone notifications") }
        }
        model.notificationsError?.let { message -> item {
            if(model.notifications.isEmpty()) NotificationEmptyCard(
                title="Notifications unavailable",subtitle=message,failed=true,
                actionLabel="Retry",onAction={ model.refreshNotifications() },
                secondaryLabel="View stocks",secondaryAction=openStocks)
            else {
                AppText(message,color=MaterialTheme.colorScheme.error)
                TextButton(onClick={ model.refreshNotifications() }) { AppText("Retry") }
            }
        } }
        if(model.notifications.isEmpty() && !model.notificationsLoading && model.notificationsError==null) item {
            val enablePush=NativePushBridge.available && !NativePushBridge.allowed
            NotificationEmptyCard(
                title="No price alerts yet",subtitle="For companies you own.",
                actionLabel=if(enablePush) "Enable phone notifications" else "View stocks",
                onAction=if(enablePush) NativePushBridge::requestPermission else openStocks,
                secondaryLabel=if(enablePush) "View stocks" else null,
                secondaryAction=if(enablePush) openStocks else null)
        }
        items(model.notifications,key={ it.text("id") }) { item ->
            var expanded by rememberSaveable(item.text("id")) { mutableStateOf(false) }
            val up=item.optDouble("impact")>=0
            val color=if(up) androidx.compose.ui.graphics.Color(0xFF32845B) else MaterialTheme.colorScheme.error
            Card(onClick={ expanded=!expanded;if(expanded)model.readNotification(item.text("id")) },
                colors=CardDefaults.cardColors(containerColor=if(item.optBoolean("read")) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        InvestmentLogo(ticker=item.text("ticker"))
                        Column(Modifier.weight(1f)) {
                            AppText(item.text("ticker"),fontWeight=FontWeight.Bold)
                            AppText(item.text("date"),style=MaterialTheme.typography.labelSmall)
                        }
                        FinancialText((if(up) "+" else "")+"${kotlin.math.round(item.optDouble("percent")*100)/100}%",color=color,fontWeight=FontWeight.Bold)
                    }
                    FinancialText("${money(item.optDouble("previousPrice"))} → ${money(item.optDouble("closingPrice"))}",style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        AppText("Holding value change",style=MaterialTheme.typography.bodySmall)
                        FinancialText((if(up) "+" else "")+money(item.optDouble("impact")),color=color,fontWeight=FontWeight.Bold)
                    }
                    if(expanded) {
                        HorizontalDivider()
                        AppText(localized("Compared with {0}").replace("{0}",item.text("previousDate")),style=MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            AppText("Estimated total gain/loss",style=MaterialTheme.typography.bodySmall)
                            FinancialText(money(item.optDouble("totalGain")),color=if(item.optDouble("totalGain")>=0) androidx.compose.ui.graphics.Color(0xFF32845B) else MaterialTheme.colorScheme.error)
                        }
                        AppText("Based on your holdings at this market date. Unrealised, before selling costs.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(model.notificationNextBefore!=null) item {
            TextButton(onClick={ model.moreNotifications() },enabled=!model.notificationsLoading,modifier=Modifier.fillMaxWidth()) { AppText("Earlier notifications") }
        }
    }
}


@Composable
private fun NotificationEmptyCard(
    title: String, subtitle: String, actionLabel: String, onAction: () -> Unit,
    failed: Boolean=false, secondaryLabel: String?=null, secondaryAction: (() -> Unit)?=null,
) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(28.dp),
        border=BorderStroke(1.dp,colors.outlineVariant.copy(alpha=.5f)),
        color=colors.surfaceContainerLow,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha=.35f),colors.surfaceContainerLow)))
            .padding(horizontal=24.dp,vertical=32.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(196.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val middle=Offset(size.width/2,size.height/2)
                    drawCircle(colors.primary.copy(alpha=.08f),size.width*.47f,middle,style=Stroke(1.dp.toPx()))
                    drawCircle(colors.primary.copy(alpha=.045f),size.width*.36f,middle)
                    drawCircle(colors.primary.copy(alpha=.25f),4.dp.toPx(),Offset(size.width*.13f,size.height*.28f))
                    drawCircle(colors.primary.copy(alpha=.18f),3.dp.toPx(),Offset(size.width*.82f,size.height*.79f))
                }
                Surface(shape=RoundedCornerShape(30.dp),color=colors.surface,
                    shadowElevation=3.dp,modifier=Modifier.size(108.dp)) {
                    Box(contentAlignment=Alignment.Center) {
                        AppIcon(if(failed) Icons.Outlined.CloudOff else Icons.Outlined.Notifications,null,
                            Modifier.size(52.dp),tint=colors.primary)
                    }
                }
                if(!failed) Surface(shape=CircleShape,color=colors.primaryContainer,
                    border=BorderStroke(3.dp,colors.surfaceContainerLow),
                    modifier=Modifier.align(Alignment.BottomEnd).offset(x=(-25).dp,y=(-25).dp).size(38.dp)) {
                    Box(contentAlignment=Alignment.Center) { AppIcon(Icons.Outlined.Done,null,Modifier.size(20.dp),tint=colors.onPrimaryContainer) }
                }
            }
            AppText(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
            AppText(subtitle,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant,textAlign=TextAlign.Center)
            Button(onClick=onAction,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp)) {
                AppIcon(if(failed) Icons.Outlined.Refresh else if(actionLabel=="View stocks") Icons.AutoMirrored.Outlined.TrendingUp else Icons.Outlined.Notifications,
                    null,Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                AppText(actionLabel,textAlign=TextAlign.Center)
            }
            if(secondaryLabel!=null && secondaryAction!=null) TextButton(onClick=secondaryAction) { AppText(secondaryLabel) }
        }
    }
}
