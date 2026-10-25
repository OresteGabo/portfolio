package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

private data class FormField(val key: String, val label: String, val initial: String = "", val numeric: Boolean = false)
private val bondFields = listOf(
    FormField("bondName", "Bond name"), FormField("issuer", "Issuer", "Government of Rwanda"), FormField("isin", "ISIN"),
    FormField("currency", "Currency", "RWF"), FormField("purchaseDate", "Purchase date · YYYY-MM-DD", Today.toString()),
    FormField("settlementDate", "Settlement date · YYYY-MM-DD", Today.toString()), FormField("maturityDate", "Maturity date · YYYY-MM-DD"),
    FormField("tenorYears", "Tenor in years", "7", true), FormField("faceValue", "Face value", "100000", true),
    FormField("pricePercent", "Price (%)", "100", true), FormField("accruedInterestPaid", "Accrued interest paid", "0", true),
    FormField("feesPaid", "Fees paid", "0", true), FormField("couponRate", "Annual coupon (%)", "11.5", true),
    FormField("withholdingTaxRate", "Withholding tax (%)", "5", true), FormField("couponFrequency", "Coupon payments per year", "2", true),
    FormField("firstCouponDate", "First coupon date · YYYY-MM-DD (optional)"),
    FormField("couponDates", "Confirmed coupon dates, separated by commas"), FormField("broker", "Broker"),
    FormField("accountReference", "Account reference"), FormField("sourceUrl", "Source URL (optional)"), FormField("notes", "Notes")
)

@Composable
fun PurchaseEditor(model: PortfolioModel, existing: JsonRecord, back: () -> Unit, imported: JsonRecord? = null) {
    val id = existing.text("id").takeIf { it.isNotBlank() }
    val values = remember(existing) {
        mutableStateMapOf<String, String>().apply {
            bondFields.forEach { field ->
                this[field.key] = when {
                    !existing.has(field.key) -> field.initial
                    field.key == "couponRate" || field.key == "withholdingTaxRate" -> (existing.optDouble(field.key, 0.0) * 100).toString()
                    field.key == "couponDates" -> existing.optJSONArray("couponDates")?.let { dates -> (0 until dates.length()).joinToString(", ") { dates.getString(it) } }.orEmpty()
                    else -> existing.text(field.key)
                }
            }
        }
    }
    var instrument by rememberSaveable { mutableStateOf(existing.text("instrumentType").ifBlank { "treasury" }) }
    var market by rememberSaveable { mutableStateOf(existing.text("market").ifBlank { "primary" }) }
    var status by rememberSaveable { mutableStateOf(existing.text("status").ifBlank { "active" }) }
    var confidence by rememberSaveable { mutableStateOf(existing.text("scheduleConfidence").ifBlank { "estimated" }) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = back) { AppText("← Cancel") }; PageTitle("Record keeping", if (id == null) "Add bond purchase" else "Edit purchase", "Save the broker-confirmed terms. This updates the same portfolio as your web app.") }
        imported?.let { item { PdfReviewNotice(it) } }
        item { AppText("Instrument", fontWeight = FontWeight.Bold); ChoiceRow(listOf("treasury", "government", "corporate", "municipal", "other"), instrument) { instrument = it } }
        item { AppText("Market", fontWeight = FontWeight.Bold); ChoiceRow(listOf("primary", "secondary", "other"), market) { market = it } }
        bondFields.forEach { field -> item {
            Field(field.label, values[field.key].orEmpty(), { values[field.key] = it }, if (field.numeric) KeyboardType.Decimal else KeyboardType.Text,
                when (field.key) { "faceValue" -> if (instrument == "treasury") "Treasury minimum: 100,000, in multiples of 100,000." else null
                    "withholdingTaxRate" -> if (instrument == "treasury") "The server applies 5% for Treasury bonds." else null
                    "couponDates" -> "Required for confirmed schedules. Leave empty for an estimated schedule."; else -> null })
        } }
        item { AppText("Schedule", fontWeight = FontWeight.Bold); ChoiceRow(listOf("estimated", "confirmed"), confidence) { confidence = it } }
        item { AppText("Status", fontWeight = FontWeight.Bold); ChoiceRow(listOf("submitted", "active", "sold", "matured"), status) { status = it } }
        item {
            val face = values["faceValue"]?.toInputNumberOrNull() ?: 0.0
            val price = values["pricePercent"]?.toInputNumberOrNull() ?: 0.0
            val accrued = values["accruedInterestPaid"]?.toInputNumberOrNull() ?: 0.0
            val fees = values["feesPaid"]?.toInputNumberOrNull() ?: 0.0
            Panel { Fact("Total cash cost", money(face * price / 100 + accrued + fees, values["currency"].orEmpty())) }
        }
        item { Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
            runCatching {
                val body = JsonRecord()
                bondFields.forEach { field ->
                    val raw = values[field.key].orEmpty().trim()
                    when {
                        field.key == "couponDates" -> {
                            val dates = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                            dates.forEach(LocalDate::parse)
                            body.put(field.key, JsonList(dates))
                        }
                        field.numeric -> {
                            val number = raw.toInputNumberOrNull()?.takeIf { it.isFinite() } ?: error("Enter a valid ${localized(field.label)}.")
                            body.put(field.key, if (field.key.endsWith("Rate")) number / 100 else number)
                        }
                        else -> body.put(field.key, raw)
                    }
                }
                listOf("purchaseDate", "settlementDate", "maturityDate").forEach { LocalDate.parse(body.getString(it)) }
                if (body.text("firstCouponDate").isNotBlank()) LocalDate.parse(body.text("firstCouponDate"))
                require(body.text("bondName").isNotBlank()) { "Enter the bond name." }
                require(body.getDouble("faceValue") > 0) { "Face value must be positive." }
                body.put("instrumentType", instrument).put("market", market).put("status", status).put("scheduleConfidence", confidence)
                body.put("currency", body.text("currency").uppercase()).put("isin", body.text("isin").uppercase())
                model.savePurchase(body, id, back)
            }.onFailure { model.error = userErrorMessage(it, "Check the purchase details.") }
        }) { AppText(if (model.busy) "Saving…" else "Save purchase") } }
    }
}

private val equityFields = listOf(
    FormField("securityName", "Security name"), FormField("ticker", "Ticker"), FormField("isin", "ISIN"),
    FormField("tradeDate", "Trade date · YYYY-MM-DD", Today.toString()), FormField("settlementDate", "Settlement date · YYYY-MM-DD", Today.toString()),
    FormField("shares", "Number of shares", "", true), FormField("pricePerShare", "Price per share (RWF)", "", true),
    FormField("brokerageCommission", "Brokerage commission", "0", true), FormField("rseTransactionFee", "RSE transaction fee", "0", true),
    FormField("csdTransactionLevy", "CSD transaction levy", "0", true), FormField("cmaTransactionFee", "CMA transaction fee", "0", true),
    FormField("broker", "Broker"), FormField("contractNote", "Contract note"), FormField("cdsAccountNo", "CDS account number"),
    FormField("cdsReference", "CDS reference"), FormField("tradeReportId", "Trade report ID")
)

@Composable
fun EquityEditor(model: PortfolioModel, existing: JsonRecord, back: () -> Unit, imported: JsonRecord? = null) {
    val values = remember(existing) { mutableStateMapOf<String, String>().apply { equityFields.forEach { this[it.key] = if (existing.has(it.key)) existing.text(it.key) else it.initial } } }
    val gross = (values["shares"]?.toInputNumberOrNull() ?: 0.0) * (values["pricePerShare"]?.toInputNumberOrNull() ?: 0.0)
    val fees = listOf("brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee").sumOf { values[it]?.toInputNumberOrNull() ?: 0.0 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = back) { AppText("← Cancel") }; PageTitle("Record keeping", "Add share purchase", "Record the details from your RSE purchase contract note.") }
        imported?.let { item { PdfReviewNotice(it) } }
        equityFields.forEach { field -> item { Field(field.label, values[field.key].orEmpty(), { values[field.key] = it }, if (field.numeric) KeyboardType.Decimal else KeyboardType.Text) } }
        item { Panel { Fact("Gross consideration", money(gross)); Fact("Total charges", money(fees)); Fact("Net amount payable", money(gross + fees)) } }
        item { Button(enabled = !model.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
            runCatching {
                val body = JsonRecord().put("instrumentType", "equity").put("exchange", "RSE").put("currency", "RWF").put("status", "active").put("notes", "")
                equityFields.forEach { field ->
                    val value = values[field.key].orEmpty().trim()
                    if (field.numeric) body.put(field.key, value.toInputNumberOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: error("Enter a valid ${localized(field.label)}."))
                    else body.put(field.key, if (field.key == "isin" || field.key == "ticker") value.uppercase() else value)
                }
                listOf("tradeDate", "settlementDate").forEach { LocalDate.parse(body.text(it)) }
                require(body.getDouble("shares") % 1.0 == 0.0 && body.getDouble("shares") > 0) { "Enter a positive whole number of shares." }
                body.put("grossConsideration", kotlin.math.round(gross * 100) / 100.0).put("totalCharges", kotlin.math.round(fees * 100) / 100.0).put("netAmountPayable", kotlin.math.round((gross + fees) * 100) / 100.0)
                model.saveEquity(body, back)
            }.onFailure { model.error = userErrorMessage(it, "Check the share details.") }
        }) { AppText("Save share purchase") } }
    }
}

@Composable
private fun PdfReviewNotice(review: JsonRecord) = Panel {
    AppText("Filled from your PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    AppText("Review the extracted dates, amounts, and fees below. Nothing is saved until you confirm.", style = MaterialTheme.typography.bodySmall)
    if (review.optBoolean("usedOcr")) AppText("Text recognition was used. Check every field carefully.", style = MaterialTheme.typography.bodySmall)
    review.optJSONArray("warnings")?.let { warnings -> (0 until warnings.length()).forEach { FinancialText(warnings.getString(it), style = MaterialTheme.typography.bodySmall) } }
    review.optJSONArray("missingFields")?.let { missing -> if (missing.length() > 0) AppText(localized("Please complete: ") + (0 until missing.length()).joinToString { missing.getString(it) }, style = MaterialTheme.typography.bodySmall) }
}
