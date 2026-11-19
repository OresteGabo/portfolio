package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import dev.orestegabo.portfolio.design.StructColors
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.struct_mark_dark
import portfolio.app.shared.generated.resources.struct_mark_light
import portfolio.app.shared.generated.resources.inzira_path_wordmark_3d_light
import portfolio.app.shared.generated.resources.inzira_path_wordmark_3d_dark
import portfolio.app.shared.generated.resources.inzira_path_wordmark_flat

/** Transparent source monogram; callers on fixed backgrounds select contrast explicitly. */
@Composable
internal fun StructMark(
    modifier: Modifier = Modifier,
    onDark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    Image(
        painterResource(if (onDark) Res.drawable.struct_mark_dark else Res.drawable.struct_mark_light),
        "Inzira",
        modifier,
    )
}

/** Path lettering shares one silhouette; the dashboard uses theme-specific metallic artwork. */
@Composable
internal fun InziraWordmark(modifier: Modifier = Modifier,
                           onDark: Boolean = MaterialTheme.colorScheme.background.luminance() < .5f,
                           sculpted: Boolean = false) {
    val artwork = if (!sculpted) Res.drawable.inzira_path_wordmark_flat
        else if (onDark) Res.drawable.inzira_path_wordmark_3d_dark else Res.drawable.inzira_path_wordmark_3d_light
    Image(painterResource(artwork), "Inzira", modifier.aspectRatio(3f),
        colorFilter = if (sculpted) null else ColorFilter.tint(if (onDark) StructColors.Brand.cream else StructColors.Brand.olive))
}
