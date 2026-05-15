package app.fynlo

import app.fynlo.logic.InterestEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Extended interest engine tests covering edge cases not in the original file.
 * Tests the critical business logic that directly impacts user money.
 */
class InterestEngineExtendedTest {

    // ── Boundary values ───────────────────────────────────────────────────────
    @Test fun `very small principal 1 rupee SI 12pct 1 year`() {
        val r = InterestEngine.calcIntAccrued(1.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        assertEquals(0.12, r, 0.01)
    }

    @Test fun `very large principal 1 crore SI 10pct 1 year = 10 lakhs`() {
        val r = InterestEngine.calcIntAccrued(1_00_00_000.0, 10.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        assertTrue("Expected ~10L, got $r", r in 9_80_000.0..10_20_000.0)
    }

    @Test fun `high interest rate 36pct SI 1 year on 10000`() {
        val r = InterestEngine.calcIntAccrued(10_000.0, 36.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        assertEquals(3_600.0, r, 10.0)
    }

    // ── Payment scenarios ─────────────────────────────────────────────────────
    @Test fun `partial payment reduces interest proportionally SI`() {
        val full = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        val half = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2023-01-01", "Simple Interest",
            totalPaid = 5_000.0, asOf = "2024-01-01")
        assertEquals(full / 2, half, 5.0)
    }

    @Test fun `payment exceeding principal returns 0 interest`() {
        val r = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2023-01-01", "Simple Interest",
            totalPaid = 15_000.0, asOf = "2024-01-01")
        assertEquals(0.0, r, 0.0)
    }

    @Test fun `exact full payment returns 0 interest`() {
        val r = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2023-01-01", "Simple Interest",
            totalPaid = 10_000.0, asOf = "2024-01-01")
        assertEquals(0.0, r, 0.0)
    }

    // ── Compound vs Simple comparison at multiple durations ───────────────────
    @Test fun `CI and SI are equal at exactly 1 year (360 day period)`() {
        val si = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2024-01-01", "Simple Interest", asOf = "2025-01-01")
        val ci = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2024-01-01", "Compound Interest", asOf = "2025-01-01")
        assertEquals("CI ≈ SI at 1 year", si, ci, 2.0)
    }

    @Test fun `CI exceeds SI at 2 years`() {
        val si = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2022-01-01", "Simple Interest", asOf = "2024-01-01")
        val ci = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2022-01-01", "Compound Interest", asOf = "2024-01-01")
        assertTrue("CI ($ci) should exceed SI ($si) at 2 years", ci > si)
    }

    @Test fun `CI exceeds SI at 5 years by significant margin`() {
        val si = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2019-01-01", "Simple Interest", asOf = "2024-01-01")
        val ci = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2019-01-01", "Compound Interest", asOf = "2024-01-01")
        assertTrue("CI ($ci) should be at least 20% more than SI ($si) at 5 years", ci > si * 1.2)
    }

    // ── Outstanding calculation ───────────────────────────────────────────────
    @Test fun `outstanding with no payments = principal + interest`() {
        val outstanding = InterestEngine.calcOutstanding(10_000.0, 1_200.0, 0.0)
        assertEquals(11_200.0, outstanding, 0.01)
    }

    @Test fun `outstanding with partial payment`() {
        val outstanding = InterestEngine.calcOutstanding(10_000.0, 1_200.0, 5_000.0)
        assertEquals(6_200.0, outstanding, 0.01)
    }

    @Test fun `outstanding fully paid is exactly 0`() {
        assertEquals(0.0, InterestEngine.calcOutstanding(10_000.0, 1_200.0, 11_200.0), 0.0)
    }

    @Test fun `outstanding never goes negative`() {
        val result = InterestEngine.calcOutstanding(10_000.0, 1_200.0, 99_999.0)
        assertEquals(0.0, result, 0.0)
    }

    // ── Date edge cases ───────────────────────────────────────────────────────
    @Test fun `leap year 2024 has 366 days`() {
        val days = InterestEngine.daysBetween("2024-01-01", "2025-01-01")
        assertEquals(366L, days)
    }

    @Test fun `non-leap year 2023 has 365 days`() {
        val days = InterestEngine.daysBetween("2023-01-01", "2024-01-01")
        assertEquals(365L, days)
    }

    @Test fun `same-day returns 0 interest (no time elapsed)`() {
        val r = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2024-01-01", "Simple Interest", asOf = "2024-01-01")
        assertEquals(0.0, r, 0.0)
    }

    @Test fun `1 day interest is small but positive`() {
        val r = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2024-01-01", "Simple Interest", asOf = "2024-01-02")
        assertTrue("1 day interest should be positive and small", r in 0.01..5.0)
    }

    // ── Type switching ("Both" mode) ──────────────────────────────────────────
    @Test fun `Both type before due date behaves like Simple Interest`() {
        val both = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2024-01-01", "Both",
            dueDate = "2030-01-01", asOf = "2025-01-01")
        val si   = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2024-01-01", "Simple Interest",
            asOf = "2025-01-01")
        assertEquals("Before due: Both should equal SI", si, both, 1.0)
    }

    @Test fun `Both type after due date switches to Compound Interest`() {
        val both = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2022-01-01", "Both",
            dueDate = "2023-01-01", asOf = "2024-01-01")
        val ci   = InterestEngine.calcIntAccrued(10_000.0, 12.0, "2022-01-01", "Compound Interest",
            asOf = "2024-01-01")
        assertEquals("After due: Both should equal CI", ci, both, 1.0)
    }

    @Test fun `Reducing Balance is less than Simple Interest for same inputs`() {
        val si = InterestEngine.calcIntAccrued(1_00_000.0, 9.0, "2023-01-01", "Simple Interest", asOf = "2024-01-01")
        val rb = InterestEngine.calcIntAccrued(1_00_000.0, 9.0, "2023-01-01", "Reducing Balance", asOf = "2024-01-01")
        assertTrue("RB ($rb) should be less than SI ($si)", rb < si)
    }
}
