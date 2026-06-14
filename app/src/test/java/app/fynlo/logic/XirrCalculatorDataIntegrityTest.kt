package app.fynlo.logic

import app.fynlo.logic.XirrCalculator.Cashflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XirrCalculatorDataIntegrityTest {

    // ── happy paths ────────────────────────────────────────────────

    @Test
    fun twoCashflowsOneYearDouble() {
        // Lent 1000, got 2000 back exactly one year later → 100% XIRR.
        val r = XirrCalculator.calc(listOf(
            Cashflow(-1000.0, "2025-05-28"),
            Cashflow(+2000.0, "2026-05-28"),
        ))
        assertEquals(1.0, r, 0.01)
    }

    @Test
    fun twoCashflowsTwoYearDoubleIsApproximately41Percent() {
        // Same shape as CAGR √2-1 ≈ 0.414 — XIRR collapses to CAGR with 2 cashflows.
        val r = XirrCalculator.calc(listOf(
            Cashflow(-1000.0, "2024-05-28"),
            Cashflow(+2000.0, "2026-05-28"),
        ))
        assertEquals(0.414, r, 0.01)
    }

    @Test
    fun monthlyAnnuityYieldsReasonableXirr() {
        // Lent 12000 today, receive 1100 every month for 12 months.
        // Total repayment 13200 → 10% nominal interest, XIRR ≈ 18% due to
        // the early-amortisation effect (each installment returns money
        // faster than a single lump sum).
        val cashflows = mutableListOf<Cashflow>()
        cashflows += Cashflow(-12000.0, "2025-05-28")
        for (m in 1..12) {
            // Approximate "one month" as +30 days.
            val date = java.time.LocalDate.of(2025, 5, 28).plusMonths(m.toLong())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            cashflows += Cashflow(+1100.0, date)
        }
        val r = XirrCalculator.calc(cashflows)
        // Just assert it's in a plausible range; exact value depends on
        // the rounding of "one month" → 30 days vs 30.4 days.
        assertTrue("Expected 15–25% XIRR, got ${r * 100}%", r in 0.15..0.25)
    }

    @Test
    fun irregularRepaymentsConverge() {
        // Lent ₹10000, got ₹3000 after 90 days + ₹8000 after 180 days.
        // Plausible XIRR somewhere in the 30-50% range (high effective
        // return because repaid in 6 months).
        val r = XirrCalculator.calc(listOf(
            Cashflow(-10000.0, "2025-01-01"),
            Cashflow(+3000.0,  "2025-04-01"),
            Cashflow(+8000.0,  "2025-07-01"),
        ))
        assertTrue("Expected positive XIRR, got ${r * 100}%", r > 0.0)
        assertTrue("Expected XIRR < 100%, got ${r * 100}%", r < 1.0)
    }

    // ── undefined-input cases ──────────────────────────────────────

    @Test
    fun emptyListReturnsUndefined() {
        assertTrue(XirrCalculator.calc(emptyList()).isNaN())
    }

    @Test
    fun singleCashflowReturnsUndefined() {
        assertTrue(XirrCalculator.calc(listOf(Cashflow(-1000.0, "2025-01-01"))).isNaN())
    }

    @Test
    fun allPositiveReturnsUndefined() {
        // No outflow → can't compute return.
        assertTrue(XirrCalculator.calc(listOf(
            Cashflow(+1000.0, "2025-01-01"),
            Cashflow(+2000.0, "2026-01-01"),
        )).isNaN())
    }

    @Test
    fun allNegativeReturnsUndefined() {
        // No inflow → can't compute return.
        assertTrue(XirrCalculator.calc(listOf(
            Cashflow(-1000.0, "2025-01-01"),
            Cashflow(-2000.0, "2026-01-01"),
        )).isNaN())
    }

    @Test
    fun unparseableDateRejectsWholeSeries() {
        assertTrue(XirrCalculator.calc(listOf(
            Cashflow(-1000.0, "not-a-date"),
            Cashflow(+2000.0, "2026-01-01"),
        )).isNaN())
    }

    @Test
    fun blankDateRejectsWholeSeries() {
        assertTrue(XirrCalculator.calc(listOf(
            Cashflow(-1000.0, ""),
            Cashflow(+2000.0, "2026-01-01"),
        )).isNaN())
    }

    @Test
    fun sameDayInAndOutZero() {
        // Lent and received on the same day → no time elapsed, but the
        // amounts net to zero → XIRR is technically 0. Solver should
        // find the trivial root. Allow some slop.
        val r = XirrCalculator.calc(listOf(
            Cashflow(-1000.0, "2025-05-28"),
            Cashflow(+1000.0, "2025-05-28"),
        ))
        // Could be NaN (degenerate) or 0; both are acceptable. Just don't crash.
        assertTrue(r.isNaN() || abs(r) < 0.001)
    }

    // ── format helper ──────────────────────────────────────────────

    @Test
    fun formatRendersOneDecimalPercent() {
        assertEquals("12.4%", XirrCalculator.format(0.1235))
        assertEquals("-50.0%", XirrCalculator.format(-0.5))
    }

    @Test
    fun formatRendersUndefinedAsDash() {
        assertEquals("—", XirrCalculator.format(XirrCalculator.UNDEFINED))
    }
}

private fun abs(x: Double) = kotlin.math.abs(x)
