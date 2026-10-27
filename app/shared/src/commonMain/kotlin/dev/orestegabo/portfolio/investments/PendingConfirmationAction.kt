package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PendingConfirmationAction(purchase: JsonRecord,enabled: Boolean,onConfirmed: () -> Unit) {
    if(purchase.text("status")!="submitted" || !purchase.optBoolean("canConfirmWithoutPdf"))return
    val preview=purchase.optJSONObject("manualConfirmation") ?: return
    var review by rememberSaveable(purchase.text("id")) { mutableStateOf(false) }
    Button(onClick={ review=true },enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) { AppText("Confirm purchase") }
    if(review)AlertDialog(onDismissRequest={ review=false },title={ AppText("Confirm your allocation?") },
        text={ Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            AppText("Confirm that your full applied amount was allocated and these figures are correct.")
            AppText(preview.text("bondName"))
            Fact("Bond principal",money(preview.optDouble("faceValue"),preview.text("currency")))
            Fact("Price",preview.text("pricePercent")+"%")
            Fact("Commission",money(preview.optDouble("feesPaid"),preview.text("currency")))
            Fact("Total cash cost",money(preview.optDouble("amountInvested"),preview.text("currency")))
            Fact("Coupon rate",percent(preview.optDouble("couponRate")))
            Fact("First coupon",preview.text("firstCouponDate"))
            Fact("Maturity",preview.text("maturityDate"))
            AppText("No personal confirmation PDF will be added to Documents.",style=MaterialTheme.typography.bodySmall)
        } },confirmButton={ TextButton(enabled=enabled,onClick={ review=false;onConfirmed() }) { AppText("Yes, confirm purchase") } },
        dismissButton={ TextButton(onClick={ review=false }) { AppText("Not yet") } })
}
