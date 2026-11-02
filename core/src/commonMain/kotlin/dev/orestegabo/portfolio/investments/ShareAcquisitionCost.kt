package dev.orestegabo.portfolio.investments

/** Same decimal/HALF_UP boundary as portfolio costs; fees are added once, not compounded. */
data class ShareAcquisitionCost(val gross: Double, val fees: Double, val payable: Double)
fun shareAcquisitionCost(shares: Double, price: Double, fees: List<Double>): ShareAcquisitionCost {
    require(shares.isFinite() && shares > 0 && shares % 1.0 == 0.0 && price.isFinite() && price > 0)
    require(fees.all { it.isFinite() && it >= 0 })
    val gross = decimalAmount(listOf(shares, price))
    val charges = sumMoney(fees)
    return ShareAcquisitionCost(gross, charges, sumMoney(listOf(gross, charges)))
}
