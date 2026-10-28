package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun NotificationPreferencesDialog(model: PortfolioModel,close: () -> Unit) {
    AlertDialog(onDismissRequest=close,title={ AppText("Notification preferences") },
        text={ Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(!model.notificationSettingsAvailable) {
                AppText("Notification settings need the updated server.")
                TextButton(onClick=model::refreshNotifications) { AppText("Retry") }
            }
            listOf("prices" to "Share price changes","coupons" to "Coupon reminders","maturities" to "Bond maturities",
                "subscriptions" to "Subscription windows","pending" to "Pending purchases","documents" to "Missing documents",
                "targets" to "Investment targets","reports" to "Report updates","security" to "Security alerts").forEach { (key,label) ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    AppText(label,modifier=Modifier.weight(1f))
                    NotificationPreferenceSwitch(model, key, label)
                }
            }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Auto-confirm coupons",modifier=Modifier.weight(1f))
                NotificationPreferenceSwitch(model, null, "Auto-confirm coupons")
            }
            AppText("Mark received on the due date, from today onward. You can undo any receipt.",style=MaterialTheme.typography.bodySmall)
        } },confirmButton={ TextButton(onClick=close) { AppText("Done") } })
}

@Composable
private fun NotificationPreferenceSwitch(model: PortfolioModel, category: String?, label: String) {
    val saving = model.notificationPreferenceSaving(category)
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        if (saving) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
    }
    Switch(checked = model.notificationPreferenceEnabled(category),
        enabled = model.notificationSettingsAvailable && !saving,
        modifier = Modifier.semantics { contentDescription = localized(label) },
        onCheckedChange = { model.updateNotificationPreference(category, it) })
}
