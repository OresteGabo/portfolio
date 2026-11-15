package dev.orestegabo.portfolio.investments

/** Recorded purchase cost: [gross] excludes fees, [fees] is their sum, [payable] includes both. */
data class ShareAcquisitionCost(val gross: Double, val fees: Double, val payable: Double)

/**
 * Calculates the recorded cost of whole shares from the supplied fee amounts, not a commission rate.
 * Gross and the sum of fees are rounded separately with the portfolio's decimal/HALF_UP boundary,
 * then added. Do not round each fee before summing or apply the pending-order commission again.
 *
 * @throws IllegalArgumentException for non-positive/non-finite price or share count, fractional
 * shares, or negative/non-finite fees. No order-entry lot increment is imposed here.
 */
fun shareAcquisitionCost(shares: Double, price: Double, fees: List<Double>): ShareAcquisitionCost {
    require(shares.isFinite() && shares > 0 && shares % 1.0 == 0.0 && price.isFinite() && price > 0)
    require(fees.all { it.isFinite() && it >= 0 })
    val gross = decimalAmount(listOf(shares, price))
    val totalFees = sumMoney(fees)
    return ShareAcquisitionCost(gross, totalFees, sumMoney(listOf(gross, totalFees)))
}
