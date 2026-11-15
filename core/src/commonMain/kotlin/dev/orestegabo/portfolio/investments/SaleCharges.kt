package dev.orestegabo.portfolio.investments

/**
 * Compare non-negative fees + tax against proceeds in the decimal meaning of the input values.
 * No rounding or epsilon is applied: even a sub-cent excess must fail admission. Double remains
 * the existing input contract; digits lost before this boundary cannot be recovered.
 *
 * This comparison aligns decimal digits as integers, rather than adding binary Doubles or
 * rounding a money sum. It also avoids imposing Foundation decimal exponent/precision limits
 * on validation. It does not calculate a persisted amount or change monetary rounding.
 */
internal fun saleChargesWithinProceeds(fees: Double, tax: Double, gross: Double): Boolean {
    require(listOf(fees, tax, gross).all { it.isFinite() && it >= 0 })
    val values = listOf(fees, tax, gross).map(::decimalDigits)
    val scale = maxOf(0, values.maxOf { it.scale })
    val aligned = values.map { it.digits + "0".repeat(scale - it.scale) }
    val charges = addUnsignedDigits(aligned[0], aligned[1])
    val proceeds = aligned[2].trimStart('0').ifEmpty { "0" }
    return charges.length < proceeds.length || charges.length == proceeds.length && charges <= proceeds
}

private data class DecimalDigits(val digits: String, val scale: Int)

/** Finite Double text contains a significand and optionally a base-ten exponent. */
private fun decimalDigits(value: Double): DecimalDigits {
    val parts = value.toString().lowercase().split('e')
    val significand = parts[0].removePrefix("-") // -0.0 has the same non-negative value as zero.
    val fractionSize = significand.substringAfter('.', "").length
    val exponent = parts.getOrNull(1)?.toInt() ?: 0
    return DecimalDigits(significand.replace(".", "").trimStart('0').ifEmpty { "0" }, fractionSize - exponent)
}

/** Bounded by the finite Double input text; at most roughly 650 aligned decimal columns. */
private fun addUnsignedDigits(first: String, second: String): String {
    val result = StringBuilder()
    var carry = 0
    for (offset in 0 until maxOf(first.length, second.length)) {
        val left = first.getOrNull(first.lastIndex - offset)?.digitToInt() ?: 0
        val right = second.getOrNull(second.lastIndex - offset)?.digitToInt() ?: 0
        val total = left + right + carry
        result.append(total % 10)
        carry = total / 10
    }
    if (carry != 0) result.append(carry)
    return result.reverse().toString().trimStart('0').ifEmpty { "0" }
}
