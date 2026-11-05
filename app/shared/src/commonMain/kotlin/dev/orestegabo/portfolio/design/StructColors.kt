package dev.orestegabo.portfolio.design

import androidx.compose.ui.graphics.Color

/** An explicit light/dark role; select the same theme the component uses for its surfaces. */
data class ThemeColor(val light: Color, val dark: Color) {
    fun resolve(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

/** Authored screen colors live here. Equal values are aliases, not copied literals. */
object StructColors {
    object Fixed {
        val white = Color(0xFFFFFFFF)
        val black = Color(0xFF000000)
    }

    object Brand {
        val olive = Color(0xFF6D5E0F)
        val cream = Color(0xFFF2E6A7)
        val ink = Color(0xFF252317)
        val canvas = Color(0xFFFAF8F2)
    }

    object Theme {
        val darkContainer = Color(0xFF514716)
        val darkBackground = Color(0xFF11150F)
        val darkSurface = Color(0xFF1D2219)
        val darkOnSurface = Color(0xFFF3F1E8)
        val darkOnSurfaceVariant = Color(0xFFC5C7BA)
        val darkOutlineVariant = Color(0xFF414735)
    }

    object Document {
        val paper = Fixed.white
        val readerCanvas = Color(0xFF30332D)
        val thumbnailShadow = Fixed.black
        val issuerLogoBacking = Fixed.white
    }

    object LanguageSelector {
        val track = ThemeColor(Color(0xFFF0F1F3), Color(0xFF181A1D))
        val selection = ThemeColor(Fixed.white, Color(0xFF35383D))
        val outline = ThemeColor(Color(0xFFD8DBE0), Color(0xFF484C52))
        val selectedText = ThemeColor(Color(0xFF20242A), Color(0xFFF5F6F7))
        val inactiveText = ThemeColor(Color(0xFF666D77), Color(0xFFADB2BA))
    }

    /** National identity colors are fixed artwork colors, independent of the app brand. */
    object Flag {
        val white = Fixed.white
        val border = Fixed.black
        val franceBlue = Color(0xFF002395)
        val franceRed = Color(0xFFED2939)
        val unitedKingdomBlue = Color(0xFF012169)
        val unitedKingdomRed = Color(0xFFC8102E)
    }

    object Onboarding {
        val background = ThemeColor(Brand.canvas, Color(0xFF181F18))
        val artworkSurface = ThemeColor(Brand.canvas, Color(0xFF272B1B))
        val action = ThemeColor(Brand.olive, Color(0xFFE6D787))
        val onAction = ThemeColor(Fixed.white, Color(0xFF242915))
    }

    object Calendar {
        val open = ThemeColor(Color(0xFF22705D), Color(0xFF8DDBB7))
        val closingToday = ThemeColor(Color(0xFF875000), Color(0xFFFFD180))
        val coupon = ThemeColor(Color(0xFF246C9F), Color(0xFF9DCBFD))
        val maturity = open
    }

    object Financial {
        val holdingGain = ThemeColor(Color(0xFF147D46), Color(0xFF81D8A7))
        val notificationGain = Color(0xFF32845B)
        val shareDocument = ThemeColor(Color(0xFF397562), Color(0xFF9CCBBA))
    }

    /** Deliberately fixed preview swatch: this illustrates a theme rather than following it. */
    object AppearancePreview {
        val cream = Color(0xFFF8F5E9)
        val ink = Color(0xFF242A20)
        val accent = Color(0xFFE1C76B)
    }

    object Dashboard {
        val gold = Color(0xFFECC75F)
        val allocationGreen = Color(0xFF739A87)
        val imageScrimStart = Color(0xE6101814)
        val imageScrimEnd = Color(0x65202B21)
        val logoBacking = Color(0xFFFAF7E8)
        val onImage = Fixed.white
        val worthGradient = listOf(Color(0xF02E321C), Color(0xF24D4117), Color(0xFA20251B))
        val worthLabel = Color(0xFFE8E4D3)
        val worthMetricLabel = Color(0xFFDAD7C6)
        val allocationAccent = ThemeColor(Color(0xFF9A731B), gold)
        val incomeAccent = ThemeColor(Color(0xFF63732E), Color(0xFFD5CC7D))
        val paymentAccent = ThemeColor(Color(0xFF386D71), Color(0xFFACCECB))
        val allocationGradient = ThemeGradient(
            listOf(Color(0xFFF8F0D8), Color(0xFFF3F5EC)),
            listOf(Color(0xFF303021), Color(0xFF1C2119))
        )
        val incomeGradient = ThemeGradient(
            listOf(Color(0xFFF1F1D9), Color(0xFFE8F0E3)),
            listOf(Color(0xFF303025), Color(0xFF20251A))
        )
        val paymentGradient = ThemeGradient(
            listOf(Color(0xFFE4F0EB), Color(0xFFF2F0DF)),
            listOf(Color(0xFF263334), Color(0xFF1B2423))
        )
        val insightForeground = ThemeColor(Color(0xFF252C24), Color(0xFFF4F1E6))
        val allocationHighlight = Color(0xFFF6DC87)
    }
}

data class ThemeGradient(val light: List<Color>, val dark: List<Color>) {
    fun resolve(darkTheme: Boolean): List<Color> = if (darkTheme) dark else light
}
