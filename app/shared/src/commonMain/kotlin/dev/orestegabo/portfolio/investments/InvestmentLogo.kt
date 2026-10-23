package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import portfolio.app.shared.generated.resources.*

@Composable
fun InvestmentLogo(ticker: String = "", bond: Boolean = false, modifier: Modifier = Modifier) {
    val resource = when {
        bond -> Res.drawable.logo_bnr
        ticker.uppercase() in listOf("BOK", "BK", "BK GROUP") -> Res.drawable.logo_bk
        ticker.uppercase() in listOf("IMR", "I&M", "IMBANK") -> Res.drawable.logo_im
        ticker.uppercase() in listOf("BLR", "BRALIRWA") -> Res.drawable.logo_bralirwa
        ticker.uppercase() in listOf("CMR", "CIMERWA") -> Res.drawable.logo_cimerwa
        ticker.uppercase() == "KCB" -> Res.drawable.logo_kcb
        else -> null
    }
    Surface(modifier.size(44.dp), shape = CircleShape,
        color = if (resource != null) Color.White else MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            if (resource != null) Image(painterResource(resource), null,
                Modifier.fillMaxSize().padding(if (bond || ticker.uppercase() == "KCB") 3.dp else 7.dp), contentScale = ContentScale.Fit)
            else Text(ticker.take(3), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
