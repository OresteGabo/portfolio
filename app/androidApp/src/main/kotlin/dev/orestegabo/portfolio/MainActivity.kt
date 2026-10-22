package dev.orestegabo.portfolio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.orestegabo.portfolio.investments.InvestmentApp
import dev.orestegabo.portfolio.investments.PortfolioModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[PortfolioModel::class.java]
        setContent { InvestmentApp(model) }
    }
}
