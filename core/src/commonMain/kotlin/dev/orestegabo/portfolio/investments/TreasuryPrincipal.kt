package dev.orestegabo.portfolio.investments

const val TREASURY_PRINCIPAL_INCREMENT = 100_000L

/** Existing recorded-allocation/request rule; this predicate does not round or impose a budget cap.
 * Identity, dates, fees, prices and context-specific upper limits remain with their callers.
 */
fun validTreasuryPrincipal(amount: Double): Boolean = amount.isFinite() &&
    amount >= TREASURY_PRINCIPAL_INCREMENT.toDouble() && amount % TREASURY_PRINCIPAL_INCREMENT.toDouble() == 0.0
