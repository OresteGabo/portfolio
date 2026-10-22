package dev.orestegabo.portfolio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orestegabo.portfolio.investments.*

@Composable
fun App() {
    val settings = rememberServerSettings()
    val actions = rememberNativeActions()
    val model = viewModel { PortfolioModel(settings) }
    CompositionLocalProvider(LocalNativeActions provides actions) {
        InvestmentApp(model)
    }
}
