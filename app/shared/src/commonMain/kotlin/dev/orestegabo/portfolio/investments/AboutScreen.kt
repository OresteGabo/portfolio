package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutScreen(open: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val actions = LocalNativeActions.current
    val version = rememberAppVersion()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(32.dp), border = BorderStroke(1.dp, colors.primary.copy(alpha = .12f))) {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = .6f), colors.surface)))
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(32.dp), color = colors.background,
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = .12f))) {
                            StructMark(Modifier.padding(20.dp).size(112.dp))
                        }
                        Surface(shape = CircleShape, color = colors.primary.copy(alpha = .09f)) {
                            AppText("BY GABO", Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppText("Struct Capital", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            AppText("Your investments, clearly structured.", style = MaterialTheme.typography.titleMedium,
                                color = colors.primary, textAlign = TextAlign.Center)
                        }
                        AppText("An investment journal for Rwanda’s bonds and shares. Keep your records together, follow important dates and see the bigger picture.",
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                    }
                }
            }
            item {
                Panel {
                    AppText("A little structure. A clearer view.", Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    AboutFeature(Icons.Outlined.AccountBalanceWallet, "Your portfolio, in one place",
                        "Keep bond and share records, supporting documents and portfolio reports together.")
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .5f))
                    AboutFeature(Icons.Outlined.CalendarMonth, "Stay ahead of important dates",
                        "Follow subscription windows, expected coupons and maturity dates in your calendar.")
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .5f))
                    AboutFeature(Icons.Outlined.Insights, "Plan with perspective",
                        "Explore investment scenarios and understand how your recorded holdings fit together.")
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer.copy(alpha = .35f)) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AppIcon(Icons.Outlined.Info, null, tint = colors.primary)
                        AppText("Struct Capital helps you track and understand your records. It does not place trades or hold your money. Figures and projections are estimates; confirm important details with your broker.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppText("Keep in touch", Modifier.padding(start = 4.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(24.dp)) {
                        Column {
                            AboutContact(Icons.Outlined.Language, "Official website", "structcapital.net") { actions.openUrl(OFFICIAL_WEBSITE_ORIGIN) }
                            AboutContact(Icons.Outlined.Email, "App support", BrandContacts.SUPPORT) { actions.openEmail(BrandContacts.SUPPORT) }
                            AboutContact(Icons.Outlined.PrivacyTip, "Privacy and data requests", BrandContacts.PRIVACY) { actions.openEmail(BrandContacts.PRIVACY) }
                            AboutContact(Icons.Outlined.Security, "Security and phishing reports", BrandContacts.SECURITY, divider = false) { actions.openEmail(BrandContacts.SECURITY) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open("hub") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        AppIcon(Icons.Outlined.Policy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        AppText("Legal & privacy")
                    }
                    TextButton(onClick = { open("credits") }) { AppText("Credits & open-source software") }
                    version?.let {
                        val label = "${localized("Version")} ${it.name}" + (it.build?.let { build -> " ($build)" } ?: "")
                        AppText(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    AppText("Built by GABO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AboutFeature(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
            AppIcon(icon, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AppText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            AppText(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutContact(icon: ImageVector, title: String, detail: String, divider: Boolean = true, click: () -> Unit) {
    Surface(onClick = click, color = MaterialTheme.colorScheme.surface) {
        ListItem(
            headlineContent = { AppText(title, style = MaterialTheme.typography.titleSmall) },
            supportingContent = { AppText(detail, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { AppIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Opens in another app", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
    if (divider) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
}
