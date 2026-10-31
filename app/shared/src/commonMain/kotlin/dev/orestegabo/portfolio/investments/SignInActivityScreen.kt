package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun SignInActivityScreen(model: PortfolioModel, notificationId: String?) {
    var activity by remember(model.origin, model.user?.text("id"), notificationId) { mutableStateOf<JsonRecord?>(null) }
    var attempted by remember(notificationId) { mutableStateOf(false) }
    LaunchedEffect(attempted, model.busy, notificationId) {
        if (!attempted && !model.busy) {
            attempted = true
            model.action {
                require(notificationId == null || notificationId.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid security notification." }
                activity = model.api.request("/api/bonds/notifications/sign-in-activity" + (notificationId?.let { "?notificationId=$it" } ?: ""))
            }
        }
    }
    val devices = activity?.rows("devices").orEmpty()
    val highlight = activity?.text("highlightDeviceId").orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AppText("These are devices recorded when signing in, not a list of active sessions. Reinstalling the app can register a device again.", style = MaterialTheme.typography.bodyMedium) }
        if (activity == null) item {
            if (!attempted || model.busy) LoadingState()
            else {
                AppText("Sign-in activity could not be loaded. Try again.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempted = false }) { AppText("Retry") }
            }
        }
        else {
            if (devices.isEmpty()) item { EmptyState("No recorded devices", icon = Icons.Outlined.Devices) }
            if (highlight.isNotBlank() && devices.none { it.text("deviceId") == highlight }) item {
                AppText("This alert’s device is no longer in the recent device list.", style = MaterialTheme.typography.bodySmall)
            }
            items(devices, key = { it.text("deviceId") }) { device ->
                val selected = device.text("deviceId") == highlight
                Surface(shape = MaterialTheme.shapes.large,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(if (device.text("platform") == "ios") Icons.Outlined.PhoneIphone else Icons.Outlined.PhoneAndroid, null)
                        AppText(if (device.text("platform") == "ios") "iPhone" else "Android", fontWeight = FontWeight.SemiBold)
                        if (selected) AppText("Device from this alert", style = MaterialTheme.typography.labelMedium)
                        if (device.text("deviceId") == NativePushBridge.deviceId) AppText("This device", style = MaterialTheme.typography.labelMedium)
                        AppText("First recorded", style = MaterialTheme.typography.labelSmall)
                        AppText(signInActivityTime(device.text("firstSeen")), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { TextButton(enabled = !model.busy, onClick = { attempted = false }) { AppText("Refresh") } }
        }
    }
}

private fun signInActivityTime(raw: String): String = runCatching {
    val value = Instant.parse(raw).toLocalDateTime(TimeZone.currentSystemDefault())
    "${value.day} ${value.date.monthLabel().take(3)} ${value.year} · ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
}.getOrDefault("")
