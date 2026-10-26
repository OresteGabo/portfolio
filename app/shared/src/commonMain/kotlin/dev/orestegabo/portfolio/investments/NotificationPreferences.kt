package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun NotificationPreferencesDialog(model: PortfolioModel,close: () -> Unit) {
    AlertDialog(onDismissRequest=close,title={ AppText("Notification preferences") },
        text={ Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(!model.notificationSettingsAvailable) {
                AppText("Notification settings need the updated server.")
                TextButton(onClick=model::refreshNotifications) { AppText("Retry") }
            }
            val categories=model.notificationPreferences.optJSONObject("categories")
            listOf("prices" to "Share price changes","coupons" to "Coupon reminders","maturities" to "Bond maturities",
                "subscriptions" to "Subscription windows","pending" to "Pending purchases","documents" to "Missing documents",
                "targets" to "Investment targets","reports" to "Report updates","security" to "Security alerts").forEach { (key,label) ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    AppText(label,modifier=Modifier.weight(1f))
                    Switch(checked=categories?.optBoolean(key,true) ?: true,enabled=model.notificationSettingsAvailable && !model.notificationSettingsSaving,
                        onCheckedChange={ model.updateNotificationPreference(key,it) })
                }
            }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Auto-confirm coupons",modifier=Modifier.weight(1f))
                Switch(checked=model.notificationPreferences.optBoolean("autoCoupon"),enabled=model.notificationSettingsAvailable && !model.notificationSettingsSaving,
                    onCheckedChange={ model.updateNotificationPreference(enabled=it) })
            }
            AppText("Mark received on the due date, from today onward. You can undo any receipt.",style=MaterialTheme.typography.bodySmall)
            if(model.notificationSettingsSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },confirmButton={ TextButton(onClick=close) { AppText("Done") } })
}
