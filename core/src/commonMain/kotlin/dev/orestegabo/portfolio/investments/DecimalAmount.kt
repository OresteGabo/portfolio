package dev.orestegabo.portfolio.investments

/** Decimal arithmetic at the existing money boundary: rates are fractions, amounts round once
 * to two places HALF_UP, including RWF broker charges. Double remains the wire/UI compatibility
 * type, never the intermediate arithmetic type. This is a primitive used by existing calculations.
 */
internal expect fun decimalAmount(
    factors: List<Double>, divisor: Int = 1, taxRate: Double = 0.0, addends: List<Double> = emptyList(),
): Double

fun roundedMoney(value: Double): Double = decimalAmount(listOf(value))
