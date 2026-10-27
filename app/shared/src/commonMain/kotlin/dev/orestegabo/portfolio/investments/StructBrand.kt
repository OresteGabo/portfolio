package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.Res
import portfolio.app.shared.generated.resources.struct_mark_dark
import portfolio.app.shared.generated.resources.struct_mark_light

/** Transparent source monogram; callers on fixed backgrounds select contrast explicitly. */
@Composable
internal fun StructMark(
    modifier: Modifier = Modifier,
    onDark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    Image(
        painterResource(if (onDark) Res.drawable.struct_mark_dark else Res.drawable.struct_mark_light),
        "Struct Capital",
        modifier,
    )
}
