package dev.orestegabo.portfolio.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.lerp

/** Brand-owned neutral surfaces; error/tertiary roles retain the supported Material defaults. */
fun structCapitalColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = StructColors.Brand.cream,
    onPrimary = StructColors.Brand.ink,
    primaryContainer = StructColors.Theme.darkContainer,
    onPrimaryContainer = StructColors.Brand.cream,
    secondary = StructColors.Brand.cream,
    onSecondary = StructColors.Brand.ink,
    secondaryContainer = StructColors.Theme.darkContainer,
    onSecondaryContainer = StructColors.Brand.cream,
    background = StructColors.Theme.darkBackground,
    onBackground = StructColors.Theme.darkOnSurface,
    surfaceDim = StructColors.Theme.darkBackground,
    surfaceBright = lerp(StructColors.Theme.darkSurface, StructColors.Theme.darkOnSurface, .08f),
    surfaceContainerLowest = StructColors.Theme.darkBackground,
    surfaceContainerLow = StructColors.Theme.darkSurface,
    surfaceContainer = lerp(StructColors.Theme.darkSurface, StructColors.Theme.darkOnSurface, .025f),
    surfaceContainerHigh = lerp(StructColors.Theme.darkSurface, StructColors.Theme.darkOnSurface, .05f),
    surfaceContainerHighest = lerp(StructColors.Theme.darkSurface, StructColors.Theme.darkOnSurface, .08f),
    surfaceVariant = lerp(StructColors.Theme.darkSurface, StructColors.Theme.darkOnSurface, .08f),
    outline = lerp(StructColors.Theme.darkSurface, StructColors.Theme.darkOnSurface, .45f),
    surfaceTint = StructColors.Brand.cream,
    surface = StructColors.Theme.darkSurface,
    onSurface = StructColors.Theme.darkOnSurface,
    onSurfaceVariant = StructColors.Theme.darkOnSurfaceVariant,
    outlineVariant = StructColors.Theme.darkOutlineVariant,
) else lightColorScheme(
    primary = StructColors.Brand.olive,
    onPrimary = StructColors.Fixed.white,
    primaryContainer = StructColors.Brand.cream,
    onPrimaryContainer = StructColors.Brand.ink,
    secondary = StructColors.Brand.olive,
    onSecondary = StructColors.Fixed.white,
    secondaryContainer = StructColors.Brand.cream,
    onSecondaryContainer = StructColors.Brand.ink,
    background = StructColors.Brand.canvas,
    onBackground = StructColors.Brand.ink,
    surfaceDim = lerp(StructColors.Brand.canvas, StructColors.Brand.ink, .08f),
    surfaceBright = StructColors.Fixed.white,
    surfaceContainerLowest = StructColors.Fixed.white,
    surfaceContainerLow = StructColors.Brand.canvas,
    surfaceContainer = lerp(StructColors.Brand.canvas, StructColors.Brand.ink, .015f),
    surfaceContainerHigh = lerp(StructColors.Brand.canvas, StructColors.Brand.ink, .035f),
    surfaceContainerHighest = lerp(StructColors.Brand.canvas, StructColors.Brand.ink, .06f),
    surfaceVariant = lerp(StructColors.Brand.canvas, StructColors.Brand.ink, .06f),
    outline = lerp(StructColors.Brand.ink, StructColors.Brand.canvas, .5f),
    surfaceTint = StructColors.Brand.olive,
    onSurfaceVariant = lerp(StructColors.Brand.ink, StructColors.Brand.canvas, .28f),
    outlineVariant = lerp(StructColors.Brand.ink, StructColors.Brand.canvas, .78f),
    surface = StructColors.Fixed.white,
    onSurface = StructColors.Brand.ink,
)
