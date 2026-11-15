package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** Expected contractual payments, not evidence that cash has been received. */
enum class BondCashFlowKind { COUPON, PRINCIPAL }

data class ScheduledBondPayment(val date: LocalDate, val kind: BondCashFlowKind, val amount: Double)

/** Uses the existing schedule/rounding policy; presentation supplies titles, labels and evidence links. */
fun scheduledBondPayments(terms: BondTerms): List<ScheduledBondPayment> {
    val tracking = trackBond(terms, terms.purchaseDate)
    val coupons = tracking.schedule.filter { terms.couponRate > 0 }.map { date ->
        ScheduledBondPayment(date, BondCashFlowKind.COUPON, tracking.netPerPayment)
    }
    return coupons + ScheduledBondPayment(terms.maturityDate, BondCashFlowKind.PRINCIPAL, terms.faceValue)
}
