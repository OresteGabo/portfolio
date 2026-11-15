package dev.orestegabo.portfolio.investments

import java.math.BigDecimal
import java.math.RoundingMode

internal actual fun decimalAmount(factors: List<Double>, divisor: Int, taxRate: Double, addends: List<Double>): Double {
    require(divisor > 0 && taxRate.isFinite() && taxRate in 0.0..1.0 && (factors + addends).all(Double::isFinite))
    var numerator = if (factors.isEmpty()) BigDecimal.ZERO else factors.fold(BigDecimal.ONE) { total, value -> total * BigDecimal.valueOf(value) }
    numerator *= BigDecimal.ONE - BigDecimal.valueOf(taxRate)
    // Add before division so there is exactly one rounding at the monetary boundary.
    numerator += addends.fold(BigDecimal.ZERO) { total, value -> total + BigDecimal.valueOf(value) } * divisor.toBigDecimal()
    return numerator.divide(divisor.toBigDecimal(), 2, RoundingMode.HALF_UP).toDouble()
}

internal actual fun proportionalCost(cost: Double, quantity: Double, original: Double): Double {
    require(cost.isFinite() && quantity.isFinite() && original.isFinite() && original > 0)
    return java.math.BigDecimal.valueOf(cost).multiply(java.math.BigDecimal.valueOf(quantity))
        .divide(java.math.BigDecimal.valueOf(original), 2, java.math.RoundingMode.HALF_UP).toDouble()
}
