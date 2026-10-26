package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
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
fun StockNotificationsScreen(model: PortfolioModel) {
    LaunchedEffect(Unit) { model.refreshNotifications() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Price changes",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                IconButton(onClick={ model.refreshNotifications() },enabled=!model.notificationsLoading) { AppIcon(Icons.Outlined.Refresh,"Refresh") }
            }
        }
        if(model.notificationsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(NativePushBridge.available && !NativePushBridge.allowed) item {
            OutlinedButton(onClick=NativePushBridge::requestPermission,modifier=Modifier.fillMaxWidth()) { AppText("Enable phone notifications") }
        }
        model.notificationsError?.let { message -> item {
            AppText(message,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={ model.refreshNotifications() }) { AppText("Retry") }
        } }
        if(model.notifications.isEmpty() && !model.notificationsLoading && model.notificationsError==null) item {
            EmptyState("No price changes yet",EmptyArtwork.CHARTS)
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
