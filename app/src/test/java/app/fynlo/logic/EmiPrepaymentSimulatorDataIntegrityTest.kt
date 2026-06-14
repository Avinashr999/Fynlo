package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 3.2.80 — EmiPrepaymentSimulator unit tests.
 */
class EmiPrepaymentSimulatorDataIntegrityTest {

    // ── baseline ──────────────────────────────────────────────────

    @Test
    fun baselineMatchesStandardReducingFormulaForZeroInterest() {
        // 0% interest, 12 months, 1200 principal → EMI = 100, total interest = 0
        val (emi, interest) = EmiPrepaymentSimulator.baseline(1200.0, 0.0, 12)
        assertEquals(100.0, emi, 0.01)
        assertEquals(0.0, interest, 0.01)
    }

    @Test
    fun baselineCalculatesPositiveInterest() {
        // 100000 at 12% for 12 months → EMI ≈ 8884.88, total interest ≈ 6618.55
        val (emi, interest) = EmiPrepaymentSimulator.baseline(100_000.0, 12.0, 12)
        assertEquals(8884.88, emi, 1.0)
        assertTrue(interest > 6000 && interest < 7000)
    }

    @Test
    fun baselineRejectsZeroPrincipal() {
        try {
            EmiPrepaymentSimulator.baseline(0.0, 10.0, 12)
            error("Zero principal should be rejected.")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun baselineRejectsZeroTenure() {
        try {
            EmiPrepaymentSimulator.baseline(1000.0, 10.0, 0)
            error("Zero tenure should be rejected.")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    // ── simulate: no-extra (sanity) ───────────────────────────────

    @Test
    fun simulateWithZeroMonthlyExtraMatchesBaselineTenure() {
        // Zero extra → same as baseline → totalMonths == tenure, no savings.
        val r = EmiPrepaymentSimulator.simulate(
            principal = 100_000.0,
            annualRatePct = 12.0,
            tenureMonths = 24,
            mode = EmiPrepaymentSimulator.Mode.MonthlyExtra(0.0),
        )
        assertEquals(24, r.totalMonths)
        assertEquals(0, r.monthsSaved)
        assertEquals(0.0, r.interestSaved, 1.0)
        assertTrue(r.feasible)
    }

    @Test
    fun simulateWithZeroLumpSumMatchesBaseline() {
        val r = EmiPrepaymentSimulator.simulate(
            100_000.0, 12.0, 24,
            EmiPrepaymentSimulator.Mode.LumpSum(0.0, 6),
        )
        assertEquals(24, r.totalMonths)
        assertEquals(0, r.monthsSaved)
    }

    // ── simulate: monthly extra saves months + interest ───────────

    @Test
    fun monthlyExtraReducesTenureAndInterest() {
        // Baseline EMI for 24 months ≈ 4707. Add 1000/month extra → ~5 months saved.
        val r = EmiPrepaymentSimulator.simulate(
            principal = 100_000.0,
            annualRatePct = 12.0,
            tenureMonths = 24,
            mode = EmiPrepaymentSimulator.Mode.MonthlyExtra(1000.0),
        )
        assertTrue("Should clear in fewer months", r.totalMonths < 24)
        assertTrue("Months saved should be positive", r.monthsSaved > 0)
        assertTrue("Interest saved should be positive", r.interestSaved > 0)
        assertTrue(r.feasible)
    }

    // ── simulate: lump sum saves months + interest ────────────────

    @Test
    fun lumpSumAtMonth6ReducesTenureAndInterest() {
        val r = EmiPrepaymentSimulator.simulate(
            100_000.0, 12.0, 24,
            EmiPrepaymentSimulator.Mode.LumpSum(20_000.0, 6),
        )
        assertTrue("Tenure should be shorter than baseline 24", r.totalMonths < 24)
        assertTrue(r.monthsSaved > 0)
        assertTrue(r.interestSaved > 0)
    }

    @Test
    fun lumpSumLargerThanRemainingBalanceClearsImmediately() {
        // Huge lump sum at month 1 clears the loan within 1-2 months.
        val r = EmiPrepaymentSimulator.simulate(
            10_000.0, 12.0, 12,
            EmiPrepaymentSimulator.Mode.LumpSum(50_000.0, 1),
        )
        assertEquals("Loan should clear in month 1", 1, r.totalMonths)
    }

    @Test
    fun lumpSumInMonthBeyondTenureBehavesAsBaseline() {
        // If the user picks a lump-sum month larger than tenure, the
        // baseline EMI clears the loan before the lump-sum fires.
        val r = EmiPrepaymentSimulator.simulate(
            10_000.0, 12.0, 6,
            EmiPrepaymentSimulator.Mode.LumpSum(5_000.0, 12),
        )
        assertEquals(6, r.totalMonths)
        assertEquals(0, r.monthsSaved)
    }

    // ── simulate: negative extra clamped to no-op ─────────────────

    @Test
    fun negativeMonthlyExtraIsClampedToZero() {
        val r = EmiPrepaymentSimulator.simulate(
            10_000.0, 10.0, 12,
            EmiPrepaymentSimulator.Mode.MonthlyExtra(-500.0),
        )
        // Negative is coerced to 0 internally → identical to baseline.
        assertEquals(12, r.totalMonths)
        assertEquals(0, r.monthsSaved)
    }

    // ── infeasibility guard ───────────────────────────────────────

    @Test
    fun zeroPrincipalReturnsInfeasible() {
        val r = EmiPrepaymentSimulator.simulate(
            0.0, 10.0, 12,
            EmiPrepaymentSimulator.Mode.MonthlyExtra(100.0),
        )
        assertFalse(r.feasible)
        assertEquals(0, r.totalMonths)
    }
}
