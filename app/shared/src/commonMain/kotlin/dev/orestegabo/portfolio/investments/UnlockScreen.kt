package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.orestegabo.portfolio.design.InziraDimensions
import dev.orestegabo.portfolio.design.InziraShapes
import dev.orestegabo.portfolio.design.InziraSpacing

/** Public brand artwork only. Authentication, cancellation and session ownership stay with the model. */
@Composable
internal fun UnlockScreen(model: PortfolioModel, continueAsGuest: (() -> Unit)? = null, openLegal: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().background(colours.background), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 680.dp || fontScale > 1.3f
        val artworkHeight = when {
            maxHeight < 460.dp -> 110.dp
            compact -> 164.dp
            else -> 230.dp
        }
        UnlockBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = InziraSpacing.section, vertical = InziraSpacing.screen),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = colours.primary.copy(alpha = .07f),
                contentColor = colours.primary, border = BorderStroke(1.dp, colours.primary.copy(alpha = .12f))) {
                Row(Modifier.padding(horizontal = InziraSpacing.large, vertical = InziraSpacing.small),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InziraSpacing.small)) {
                    AppIcon(Icons.Outlined.Lock, null, Modifier.size(14.dp))
                    AppText("SECURE ACCESS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            UnlockBrandArtwork(Modifier.fillMaxWidth().height(artworkHeight))
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(InziraSpacing.medium)) {
                AppText(if (model.verifyingSession) "Device unlocked" else "Portfolio locked",
                    Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                AppText(if (model.verifyingSession) "Checking your server session. Your investments stay protected."
                    else "Your saved sign-in is protected on this device.",
                    Modifier.widthIn(max = 320.dp), style = MaterialTheme.typography.bodyMedium,
                    color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(if (compact) InziraSpacing.section else InziraSpacing.spacious))
            UnlockActions(model, continueAsGuest, openLegal)
            Spacer(Modifier.height(if (compact) InziraSpacing.section else InziraSpacing.spacious))
            UnlockBrandNote()
        }
    }
}

@Composable
private fun UnlockActions(model: PortfolioModel, continueAsGuest: (() -> Unit)?, openLegal: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Button(onClick = model::unlock, enabled = !model.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = InziraDimensions.primaryActionMinHeight),
        contentPadding = PaddingValues(horizontal = InziraSpacing.large, vertical = InziraSpacing.large), shape = InziraShapes.action) {
        if (model.busy) InziraLoadingMark(active = true, modifier = Modifier.size(24.dp), tint = LocalContentColor.current)
        else AppIcon(when (model.unlockLabel) {
            "Face ID" -> Icons.Outlined.Face
            "Touch ID", "Fingerprint" -> Icons.Outlined.Fingerprint
            else -> Icons.Outlined.Lock
        }, null)
        Spacer(Modifier.width(InziraSpacing.small))
        AppText(if (model.busy) "Unlocking…" else localized("Unlock with {0}").replace("{0}", localized(model.unlockLabel)),
            Modifier.weight(1f, fill = false), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(InziraSpacing.medium))
    OutlinedButton(onClick = model::usePassword, enabled = !model.busy || model.verifyingSession,
        modifier = Modifier.fillMaxWidth().heightIn(min = InziraDimensions.primaryActionMinHeight),
        contentPadding = PaddingValues(horizontal = InziraSpacing.large, vertical = InziraSpacing.large),
        shape = InziraShapes.action, border = BorderStroke(1.dp, colours.outline)) {
        AppText("Use password instead", textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(InziraSpacing.small))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(InziraSpacing.tight), maxItemsInEachRow = 2) {
        continueAsGuest?.let { enter ->
            TextButton(onClick = enter, modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = InziraSpacing.medium, vertical = InziraSpacing.medium)) {
                AppIcon(Icons.Outlined.Person, null, Modifier.size(18.dp))
                Spacer(Modifier.width(InziraSpacing.small))
                AppText("Continue as Guest", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
        TextButton(onClick = openLegal, modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = InziraSpacing.medium, vertical = InziraSpacing.medium)) {
            AppIcon(Icons.Outlined.PrivacyTip, null, Modifier.size(18.dp))
            Spacer(Modifier.width(InziraSpacing.small))
            AppText("Legal & privacy", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Static, bundled brand content: no account data, network requests, investment promises or ad targeting. */
@Composable
private fun UnlockBrandNote() {
    val colours = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = InziraShapes.card, color = colours.surfaceContainerLow,
        border = BorderStroke(1.dp, colours.outlineVariant.copy(alpha = .6f))) {
        Column(Modifier.background(Brush.horizontalGradient(listOf(colours.primary.copy(alpha = .035f), colours.surfaceContainerLow)))
            .padding(InziraSpacing.screen).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(InziraSpacing.small)) {
            AppText("Your way forward", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            AppText("Bonds · Shares · Documents", style = MaterialTheme.typography.bodySmall,
                color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
