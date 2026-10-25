package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal actual fun platformSystemLanguage(): String = LocalConfiguration.current.locales[0].toLanguageTag()
