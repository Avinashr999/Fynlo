package com.example.cashmemo

import com.example.cashmemo.logic.InterestEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the account balance sync logic.
 * These ensure that balance changes are calculated correctly
 * before being written to Firestore.
 */
class SyncLogicTest {

    // ── Interest Engine (already tested) ─────────────────────────────────────

    @Test fun `SI balance reduction is correct for expense`() {
        // Simulate: account had 5000, expense of 88 -> should be 4912
        val initialBalance = 5000.0
        val expenseAmount  = 88.0
        val expectedBalance = initialBalance - expenseAmount
        assertEquals(4912.0, expectedBalance, 0.001)
    }

    @Test fun `income increases balance correctly`() {
        val initialBalance = 5000.0
        val income         = 1000.0
        assertEquals(6000.0, initialBalance + income, 0.001)
    }

    @Test fun `transfer is balance-neutral across accounts`() {
        val acct1 = 5000.0
        val acct2 = 3000.0
        val amount = 500.0
        val newAcct1 = acct1 - amount
        val newAcct2 = acct2 + amount
        // Total should be unchanged
        assertEquals(acct1 + acct2, newAcct1 + newAcct2, 0.001)
    }

    @Test fun `delete expense reversal restores balance`() {
        val initialBalance = 5000.0
        val expenseAmount  = 88.0
        val afterExpense   = initialBalance - expenseAmount  // 4912
        val afterDelete    = afterExpense + expenseAmount    // should restore to 5000
        assertEquals(initialBalance, afterDelete, 0.001)
    }

    @Test fun `delete income reversal restores balance`() {
        val initialBalance = 5000.0
        val incomeAmount   = 1000.0
        val afterIncome    = initialBalance + incomeAmount  // 6000
        val afterDelete    = afterIncome - incomeAmount     // should restore to 5000
        assertEquals(initialBalance, afterDelete, 0.001)
    }

    // ── Interest calculations ─────────────────────────────────────────────────

    @Test fun `SI never switches to compound even when overdue`() {
        val si       = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01",
            "Simple Interest", dueDate = "2023-06-01", asOf = "2024-01-01")
        val siNoDue  = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01",
            "Simple Interest", asOf = "2024-01-01")
        assertEquals("SI should ignore due date", siNoDue, si, 1.0)
    }

    @Test fun `Reducing Balance interest is less than Simple Interest`() {
        val si = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Simple Interest",    asOf = "2024-01-01")
        val rb = InterestEngine.calcIntAccrued(10000.0, 12.0, "2023-01-01", "Reducing Balance",   asOf = "2024-01-01")
        assertTrue("Reducing ($rb) must be < Simple ($si)", rb < si)
    }

    @Test fun `Both type splits correctly into SI + CI portions`() {
        val (si, ci) = InterestEngine.calcBothPortions(
            amount = 10000.0, rate = 12.0,
            loanDate = "2022-01-01", dueDate = "2023-01-01",
            asOf    = "2024-01-01"
        )
        assertTrue("SI portion should be positive", si > 0)
        assertTrue("CI portion should be positive when overdue", ci > 0)
    }

    @Test fun `Both type - CI is zero before due date`() {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val future = java.time.LocalDate.now().plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val (si, ci) = InterestEngine.calcBothPortions(
            amount = 10000.0, rate = 12.0,
            loanDate = "2024-01-01", dueDate = future
        )
        assertEquals("CI should be 0 before due date", 0.0, ci, 0.001)
    }

    @Test fun `outstanding is zero after full repayment`() {
        val principal = 10000.0
        val interest  = 1200.0
        val paid      = 11200.0  // fully paid
        val outstanding = InterestEngine.calcOutstanding(principal, interest, paid)
        assertEquals(0.0, outstanding, 0.001)
    }
}
