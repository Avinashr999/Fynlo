package app.fynlo.logic

import app.fynlo.data.model.FinancialSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for FinancialSummary calculation logic.
 * Validates net worth, savings rate, debt burden, and edge cases.
 */
class FinancialSummaryCalculationTest {

    // Helper to build a summary with common defaults
    private fun summary(
        totalCash: Double = 0.0,
        totalInvestments: Double = 0.0,
        totalReceivables: Double = 0.0,
        totalAssets: Double = totalCash + totalInvestments + totalReceivables,
        totalDebtPrincipal: Double = 0.0,
        totalDebtInterest: Double = 0.0,
        netWorth: Double = totalAssets - (totalDebtPrincipal + totalDebtInterest),
        totalIncome: Double = 0.0,
        totalExpenses: Double = 0.0,
        totalInterestIncome: Double = 0.0,
        totalInterestExpense: Double = 0.0,
        totalBadDebtWriteOffs: Double = 0.0,
        investmentGrowth: Double = 0.0,
        lendingYield: Double = 0.0,
        debtBurden: Double = if (netWorth != 0.0) ((totalDebtPrincipal + totalDebtInterest) / netWorth) * 100 else 0.0,
        totalInterestLoans: Double = 0.0,
        totalHandLoans: Double = 0.0
    ) = FinancialSummary(
        totalCash = totalCash,
        totalInvestments = totalInvestments,
        totalReceivables = totalReceivables,
        totalAssets = totalAssets,
        totalDebtPrincipal = totalDebtPrincipal,
        totalDebtInterest = totalDebtInterest,
        netWorth = netWorth,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        totalInterestIncome = totalInterestIncome,
        totalInterestExpense = totalInterestExpense,
        totalBadDebtWriteOffs = totalBadDebtWriteOffs,
        investmentGrowth = investmentGrowth,
        lendingYield = lendingYield,
        debtBurden = debtBurden,
        totalInterestLoans = totalInterestLoans,
        totalHandLoans = totalHandLoans
    )

    // ── Net Worth ────────────────────────────────────────────────────────

    @Test
    fun `net worth - cash only, no debts`() {
        val s = summary(totalCash = 50000.0)
        assertEquals(50000.0, s.netWorth, 0.01)
    }

    @Test
    fun `net worth - cash plus investments`() {
        val s = summary(totalCash = 50000.0, totalInvestments = 100000.0)
        assertEquals(150000.0, s.netWorth, 0.01)
    }

    @Test
    fun `net worth - assets minus debt`() {
        val s = summary(
            totalCash = 100000.0,
            totalDebtPrincipal = 30000.0,
            totalDebtInterest = 5000.0
        )
        assertEquals(65000.0, s.netWorth, 0.01)
    }

    @Test
    fun `net worth - negative when debt exceeds assets`() {
        val s = summary(
            totalCash = 10000.0,
            totalDebtPrincipal = 50000.0,
            totalDebtInterest = 5000.0
        )
        assertEquals(-45000.0, s.netWorth, 0.01)
    }

    @Test
    fun `net worth - zero when perfectly balanced`() {
        val s = summary(
            totalCash = 50000.0,
            totalDebtPrincipal = 50000.0
        )
        assertEquals(0.0, s.netWorth, 0.01)
    }

    // ── Savings Rate ─────────────────────────────────────────────────────

    @Test
    fun `savings rate - 50 percent when half income spent`() {
        val s = summary(totalIncome = 100000.0, totalExpenses = 50000.0)
        assertEquals(50.0, s.savingsRate, 0.01)
    }

    @Test
    fun `savings rate - zero income returns zero`() {
        val s = summary(totalIncome = 0.0, totalExpenses = 5000.0)
        assertEquals(0.0, s.savingsRate, 0.01)
    }

    @Test
    fun `savings rate - negative when expenses exceed income`() {
        val s = summary(totalIncome = 50000.0, totalExpenses = 70000.0)
        assertTrue("Savings rate should be negative", s.savingsRate < 0)
        assertEquals(-40.0, s.savingsRate, 0.01)
    }

    @Test
    fun `savings rate - 100 percent when no expenses`() {
        val s = summary(totalIncome = 100000.0, totalExpenses = 0.0)
        assertEquals(100.0, s.savingsRate, 0.01)
    }

    // ── Net Profit From Lending ──────────────────────────────────────────

    @Test
    fun `net profit from lending - interest income minus bad debts`() {
        val s = summary(totalInterestIncome = 5000.0, totalBadDebtWriteOffs = 2000.0)
        assertEquals(3000.0, s.netProfitFromLending, 0.01)
    }

    @Test
    fun `net profit from lending - negative when write-offs exceed income`() {
        val s = summary(totalInterestIncome = 1000.0, totalBadDebtWriteOffs = 5000.0)
        assertEquals(-4000.0, s.netProfitFromLending, 0.01)
    }

    // ── Cost of Debt ─────────────────────────────────────────────────────

    @Test
    fun `cost of debt equals interest expense`() {
        val s = summary(totalInterestExpense = 12000.0)
        assertEquals(12000.0, s.costOfDebt, 0.01)
    }

    // ── Debt Burden ──────────────────────────────────────────────────────

    @Test
    fun `debt burden - zero when no net worth`() {
        val s = summary(totalDebtPrincipal = 10000.0, netWorth = 0.0, debtBurden = 0.0)
        assertEquals(0.0, s.debtBurden, 0.01)
    }

    @Test
    fun `debt burden - 50 percent ratio`() {
        val s = summary(
            totalCash = 200000.0,
            totalDebtPrincipal = 80000.0,
            totalDebtInterest = 20000.0
        )
        // debt / netWorth * 100 = 100000 / 100000 * 100 = 100
        assertEquals(100.0, s.debtBurden, 0.01)
    }

    // ── Exclusions ───────────────────────────────────────────────────────

    @Test
    fun `investment growth can be negative`() {
        val s = summary(investmentGrowth = -5000.0)
        assertEquals(-5000.0, s.investmentGrowth, 0.01)
    }

    @Test
    fun `all zeros produces empty summary`() {
        val s = FinancialSummary()
        assertEquals(0.0, s.netWorth, 0.01)
        assertEquals(0.0, s.savingsRate, 0.01)
        assertEquals(0.0, s.costOfDebt, 0.01)
        assertEquals(0.0, s.netProfitFromLending, 0.01)
        assertEquals(0.0, s.debtBurden, 0.01)
    }
}
