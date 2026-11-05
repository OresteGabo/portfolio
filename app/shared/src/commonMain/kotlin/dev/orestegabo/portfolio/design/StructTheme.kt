package dev.orestegabo.portfolio.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/** One mapping from brand roles to Material roles; uncustomized roles keep Material defaults. */
fun structCapitalColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = StructColors.Brand.cream,
    onPrimary = StructColors.Brand.ink,
    primaryContainer = StructColors.Theme.darkContainer,
    onPrimaryContainer = StructColors.Brand.cream,
    secondary = StructColors.Brand.cream,
    secondaryContainer = StructColors.Theme.darkContainer,
    onSecondaryContainer = StructColors.Brand.cream,
    background = StructColors.Theme.darkBackground,
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
    secondaryContainer = StructColors.Brand.cream,
    onSecondaryContainer = StructColors.Brand.ink,
    background = StructColors.Brand.canvas,
    surface = StructColors.Fixed.white,
    onSurface = StructColors.Brand.ink,
)
