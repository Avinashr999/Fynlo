package app.fynlo.logic

import app.fynlo.logic.DebtPayoffPlanner.DebtInput
import app.fynlo.logic.DebtPayoffPlanner.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C22 (3.2.60) — DebtPayoffPlanner unit tests. Pure helper, no Android
 * deps, runs in the regular `*DataIntegrity*` filter.
 */
class DebtPayoffPlannerDataIntegrityTest {

    @Test
    fun emptyDebtListYieldsZeroMonthsAndFeasibleTrue() {
        val r = DebtPayoffPlanner.plan(emptyList(), 1000.0, Strategy.SNOWBALL)
        assertEquals(0, r.totalMonths)
        assertEquals(0.0, r.totalInterestPaid, 0.01)
        assertTrue(r.feasible)
        assertTrue(r.perDebt.isEmpty())
    }

    @Test
    fun singleZeroInterestDebtPaysOffInExpectedMonths() {
        // 1000 owed at 0%, paying 250/month → 4 months exactly.
        val r = DebtPayoffPlanner.plan(
            debts = listOf(DebtInput("d1", "X", 1000.0, 0.0)),
            monthlyBudget = 250.0,
            strategy = Strategy.SNOWBALL,
        )
        assertEquals(4, r.totalMonths)
        assertEquals(0.0, r.totalInterestPaid, 0.01)
        assertTrue(r.feasible)
        assertEquals(4, r.perDebt.single().payoffMonth)
    }

    @Test
    fun snowballOrdersBySmallestBalanceFirst() {
        // Three debts; budget enough to clear them. Smallest balance
        // should be cleared first under SNOWBALL regardless of rate.
        val debts = listOf(
            DebtInput("big",   "Big Loan",   10000.0, 5.0),
            DebtInput("small", "Small Card", 500.0,   30.0),
            DebtInput("mid",   "Mid Loan",   3000.0,  12.0),
        )
        val r = DebtPayoffPlanner.plan(debts, monthlyBudget = 600.0, strategy = Strategy.SNOWBALL)
        // Payoff order: small (500), mid (3000), big (10000).
        val byName = r.perDebt.associateBy { it.name }
        assertTrue("small must clear before mid",
            byName["Small Card"]!!.payoffMonth < byName["Mid Loan"]!!.payoffMonth)
        assertTrue("mid must clear before big",
            byName["Mid Loan"]!!.payoffMonth < byName["Big Loan"]!!.payoffMonth)
    }

    @Test
    fun avalancheOrdersByHighestRateFirst() {
        // Same three debts; AVALANCHE should target highest rate first
        // (small at 30% even though it's also the smallest balance) — but
        // tie-break differs, so to make order unambiguous use distinct
        // balance/rate pairs where the highest-rate is NOT the smallest.
        val debts = listOf(
            DebtInput("low",  "Low Rate",  500.0,   3.0),
            DebtInput("high", "High Rate", 10000.0, 25.0),  // largest AND highest rate
            DebtInput("mid",  "Mid Rate",  3000.0,  10.0),
        )
        val r = DebtPayoffPlanner.plan(debts, monthlyBudget = 1000.0, strategy = Strategy.AVALANCHE)
        // First in priority list = high-rate one (cleared first OR receives
        // the most aggressive surplus). With this much budget, "high" will
        // clear before "mid" or "low" because all surplus targets it.
        val byName = r.perDebt.associateBy { it.name }
        assertTrue("high-rate must clear before mid",
            byName["High Rate"]!!.payoffMonth < byName["Mid Rate"]!!.payoffMonth)
    }

    @Test
    fun avalancheSavesInterestVsSnowball() {
        // Pick debts where the two strategies diverge — small/low-rate vs
        // big/high-rate. Snowball clears the small one first (low rate, low
        // interest cost) and lets the big high-rate one keep accruing.
        // Avalanche kills the big high-rate one first → less total interest.
        // Snowball: small first.  Avalanche: big first.
        val debts = listOf(
            DebtInput("small_low",  "Small Loan", 1000.0, 5.0),   // smallest balance
            DebtInput("big_high",   "Big Card",   5000.0, 25.0),  // highest rate
        )
        val snow = DebtPayoffPlanner.plan(debts, 500.0, Strategy.SNOWBALL)
        val aval = DebtPayoffPlanner.plan(debts, 500.0, Strategy.AVALANCHE)
        assertTrue("Avalanche must save interest vs Snowball (saved=${snow.totalInterestPaid - aval.totalInterestPaid})",
            aval.totalInterestPaid < snow.totalInterestPaid)
    }

    @Test
    fun unfeasibleBudgetReturnsNotFeasible() {
        // 10000 at 24% accrues ~200/month interest just from interest. A
        // 100 budget can't even cover interest → infinite-loop guard.
        val r = DebtPayoffPlanner.plan(
            debts = listOf(DebtInput("d1", "X", 10000.0, 24.0)),
            monthlyBudget = 100.0,
            strategy = Strategy.SNOWBALL,
        )
        assertFalse(r.feasible)
        assertEquals(-1, r.totalMonths)
    }

    @Test
    fun zeroBalanceDebtIsTreatedAsCleared() {
        // Defensive: a debt with 0 outstanding shouldn't add months.
        val r = DebtPayoffPlanner.plan(
            debts = listOf(
                DebtInput("paid", "Paid", 0.0, 10.0),
                DebtInput("real", "Real", 1000.0, 0.0),
            ),
            monthlyBudget = 500.0,
            strategy = Strategy.AVALANCHE,
        )
        // Only the "real" debt drives totalMonths.
        assertEquals(2, r.totalMonths)
    }

    @Test
    fun rolloverFromClearedDebtAcceleratesNext() {
        // Two debts, big budget. Without rollover the second priority
        // would only start receiving payments after the first cleared.
        // With rollover the same-month surplus applies immediately.
        // Use 500 + 1500 (priority order under SNOWBALL) and budget 800.
        // Month 1: 500 → A (clears) + 300 → B. (B = 1200 remaining)
        // Month 2: 800 → B. (B = 400 remaining)
        // Month 3: 400 → B. (cleared) → total 3 months.
        // Without rollover this would be: m1 800→A clears (300 wasted),
        // m2 800→B, m3 700→B → also 3 months but with 300 wasted, so
        // the strict assertion is that A clears in m1 AND B clears in m3.
        val debts = listOf(
            DebtInput("a", "A", 500.0,  0.0),
            DebtInput("b", "B", 1500.0, 0.0),
        )
        val r = DebtPayoffPlanner.plan(debts, monthlyBudget = 800.0, strategy = Strategy.SNOWBALL)
        val byName = r.perDebt.associateBy { it.name }
        assertEquals("A must clear in month 1", 1, byName["A"]!!.payoffMonth)
        assertEquals("B must clear in month 3 thanks to month-1 rollover", 3, byName["B"]!!.payoffMonth)
        assertEquals(3, r.totalMonths)
    }

    @Test
    fun infiniteLoopGuardCapsAtFiftyYears() {
        // Edge: a debt with interest exactly equal to budget should not
        // hang the test runner. Should hit MAX_MONTHS and return.
        // 12% annual / 12 = 1% monthly. 10000 * 0.01 = 100 interest/month.
        // Budget exactly 100 → never makes progress on principal.
        val r = DebtPayoffPlanner.plan(
            debts = listOf(DebtInput("d1", "X", 10000.0, 12.0)),
            monthlyBudget = 100.0,
            strategy = Strategy.SNOWBALL,
        )
        // Should return as infeasible without hanging.
        assertFalse(r.feasible)
    }

    @Test
    fun resultPerDebtListIsInPayoffOrder() {
        // Snowball-cleared debts should come back in the order they were
        // paid off so the UI can render the timeline naturally.
        val debts = listOf(
            DebtInput("third",  "Third",  10000.0, 5.0),
            DebtInput("first",  "First",  500.0,   5.0),
            DebtInput("second", "Second", 2000.0,  5.0),
        )
        val r = DebtPayoffPlanner.plan(debts, 800.0, Strategy.SNOWBALL)
        assertEquals(listOf("First", "Second", "Third"), r.perDebt.map { it.name })
    }
}
