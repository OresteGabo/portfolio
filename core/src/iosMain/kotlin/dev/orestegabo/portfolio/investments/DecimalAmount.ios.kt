package dev.orestegabo.portfolio.investments

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSDecimalNumberHandler
import platform.Foundation.NSRoundingMode

internal actual fun decimalAmount(factors: List<Double>, divisor: Int, taxRate: Double, addends: List<Double>): Double {
    require(divisor > 0 && taxRate.isFinite() && taxRate in 0.0..1.0 && (factors + addends).all(Double::isFinite))
    fun decimal(value: Double) = NSDecimalNumber.decimalNumberWithString(value.toString())
    val zero = decimal(0.0)
    val one = decimal(1.0)
    var numerator = if (factors.isEmpty()) zero else factors.fold(one) { total, value -> total.decimalNumberByMultiplyingBy(decimal(value)) }
    numerator = numerator.decimalNumberByMultiplyingBy(one.decimalNumberBySubtracting(decimal(taxRate)))
    val denominator = decimal(divisor.toDouble())
    numerator = numerator.decimalNumberByAdding(addends.fold(zero) { total, value -> total.decimalNumberByAdding(decimal(value)) }.decimalNumberByMultiplyingBy(denominator))
    val rounding = NSDecimalNumberHandler.decimalNumberHandlerWithRoundingMode(NSRoundingMode.NSRoundPlain, 2, false, false, false, true)
    return numerator.decimalNumberByDividingBy(denominator).decimalNumberByRoundingAccordingToBehavior(rounding).doubleValue
}
