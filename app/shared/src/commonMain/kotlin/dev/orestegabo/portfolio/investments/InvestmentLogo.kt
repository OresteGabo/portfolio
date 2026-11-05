package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.design.StructColors

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

internal fun investmentLogoResource(ticker: String, bond: Boolean = false): org.jetbrains.compose.resources.DrawableResource? {
    val symbol = ticker.trim().uppercase()
    return when {
        bond -> Res.drawable.logo_bnr
        symbol in listOf("BOK", "BK", "BK GROUP") -> Res.drawable.logo_bk
        symbol in listOf("IMR", "I&M", "IMBANK") -> Res.drawable.logo_im
        symbol in listOf("BLR", "BRALIRWA") -> Res.drawable.logo_bralirwa
        symbol in listOf("CMR", "CIMERWA") -> Res.drawable.logo_cimerwa
        symbol == "KCB" -> Res.drawable.logo_kcb
        symbol in listOf("MTNR", "MTN") -> Res.drawable.logo_mtn
        symbol in listOf("EQTY", "EQUITY", "EQUITY BANK") -> Res.drawable.logo_equity
        else -> null
    }
}

@Composable
fun InvestmentLogo(ticker: String = "", bond: Boolean = false, modifier: Modifier = Modifier, compact: Boolean = false, prominent: Boolean = false) {
    val symbol = ticker.trim().uppercase()
    val resource = investmentLogoResource(ticker, bond)
    Surface(modifier.size(if (prominent) 76.dp else 44.dp), shape = if (prominent) RoundedCornerShape(16.dp) else CircleShape,
        color = if (resource != null) StructColors.Document.issuerLogoBacking else MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (resource != null) Image(painterResource(resource), null,
                Modifier.fillMaxSize().padding(if (prominent) 8.dp else if (compact || bond || symbol in listOf("KCB", "MTNR", "MTN")) 3.dp else 7.dp), contentScale = ContentScale.Fit)
            else AppText(ticker.take(3), style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
