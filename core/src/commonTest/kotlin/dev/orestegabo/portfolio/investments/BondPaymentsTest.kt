package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class BondPaymentsTest {
    private fun terms(rate: Double = .1, frequency: Int = 2, dates: List<LocalDate> = emptyList()) = BondTerms(
        LocalDate(2026, 1, 1), LocalDate(2027, 1, 1), 100000.0, rate, .05, frequency, confirmedDates = dates)

    @Test fun returnsNetCouponsThenSeparatePrincipalUsingExistingTrackingPolicy() {
        val values = scheduledBondPayments(terms())
        assertEquals(listOf(LocalDate(2026, 7, 1), LocalDate(2027, 1, 1), LocalDate(2027, 1, 1)), values.map { it.date })
        assertEquals(listOf(BondCashFlowKind.COUPON, BondCashFlowKind.COUPON, BondCashFlowKind.PRINCIPAL), values.map { it.kind })
        assertEquals(listOf(4750.0, 4750.0, 100000.0), values.map { it.amount })
    }

    @Test fun zeroCouponHasOnlyPrincipal() {
        assertEquals(listOf(ScheduledBondPayment(LocalDate(2027, 1, 1), BondCashFlowKind.PRINCIPAL, 100000.0)), scheduledBondPayments(terms(rate = 0.0)))
    }

    @Test fun unsupportedInferredFrequencyDoesNotInventPayments() {
        assertEquals(listOf(BondCashFlowKind.PRINCIPAL), scheduledBondPayments(terms(frequency = 7)).map { it.kind })
    }

    @Test fun publishedDatesKeepExistingFilteringAndDeduplication() {
        val published = listOf(LocalDate(2027, 2, 1), LocalDate(2026, 8, 5), LocalDate(2026, 8, 5), LocalDate(2026, 1, 1))
        val input = terms(dates = published)
        val flows = scheduledBondPayments(input)
        assertEquals(listOf(LocalDate(2026, 8, 5), LocalDate(2027, 1, 1)), flows.map { it.date })
        assertEquals(input.netCouponPayment(), flows.first().amount)
        assertEquals(published, input.confirmedDates)
    }
}
