package dev.orestegabo.portfolio.investments

const val SHARE_ORDER_COMMISSION_RATE = 0.0171

data class ShareOrderEstimate(val gross: Double, val commission: Double, val total: Double)

/** An order budget, never a position or a valuation. The broker's actual fill remains authoritative. */
fun shareOrderEstimate(shares: Double, price: Double): ShareOrderEstimate {
    require(shares.isFinite() && shares > 0 && shares <= 1_000_000_000 && shares % 1.0 == 0.0)
    require(price.isFinite() && price > 0 && price <= 1_000_000_000 && roundedMoney(price) == price)
    val gross = decimalAmount(listOf(shares, price))
    require(gross <= 10_000_000_000_000)
    val commission = decimalAmount(listOf(gross, SHARE_ORDER_COMMISSION_RATE))
    return ShareOrderEstimate(gross, commission, sumMoney(listOf(gross, commission)))
}
