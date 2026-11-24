package dev.orestegabo.portfolio.investments

import kotlin.math.abs

/** Overview display only. Full records, inputs and calculations continue to use the original amount.
 * M/B/T use two decimal places, HALF_UP on the canonical decimal representation, with unit carry.
 * String scaling avoids overflowing integer intermediates or introducing a binary division boundary.
 */
internal fun compactNumber(value: Double): String {
    if (!value.isFinite() || abs(value) < 1_000_000) return formatNumber(value)
    var power = when {
        abs(value) >= 1_000_000_000_000 -> 12
        abs(value) >= 1_000_000_000 -> 9
        else -> 6
    }
    var hundredths = compactHundredths(value, power)
    if (power < 12 && hundredths == "100000") {
        power += 3
        hundredths = compactHundredths(value, power)
    }
    val decimal = if (AppLanguage.code == "fr") "," else "."
    val suffix = when (power) { 6 -> "M"; 9 -> "B"; else -> "T" }
    return (if (value < 0) "−" else "") + hundredths.dropLast(2) + decimal + hundredths.takeLast(2) + suffix
}

internal fun compactMoney(amount: Double, currency: String = "RWF") = "$currency ${compactNumber(amount)}"

private fun compactHundredths(value: Double, power: Int): String {
    val canonical = abs(value).toString().lowercase()
    val mantissa = canonical.substringBefore('e')
    val exponent = canonical.substringAfter('e', "0").toInt()
    val digits = mantissa.replace(".", "")
    val decimalAt = mantissa.indexOf('.').takeIf { it >= 0 } ?: mantissa.length
    val kept = decimalAt + exponent - power + 2
    val result = digits.take(kept).padEnd(kept, '0').trimStart('0').ifEmpty { "0" }
    if ((digits.getOrNull(kept) ?: '0') < '5') return result.padStart(3, '0')
    val rounded = result.toCharArray()
    for (index in rounded.lastIndex downTo 0) {
        if (rounded[index] != '9') {
            rounded[index] = rounded[index] + 1
            return rounded.concatToString().padStart(3, '0')
        }
        rounded[index] = '0'
    }
    return "1" + rounded.concatToString()
}
