package app.fynlo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C05 — typed category lists invariants (UX_AUDIT §C05).
 *
 * Pure-function tests over [Categories]. Documents the contracts that
 * the UI rewrites in `TransactionDialog` and `EditTransactionDialog`
 * depend on:
 *
 *  - `INCOME ∩ EXPENSE = ∅` — no category is in both lists. This is the
 *    *core* C05 fix: it's literally impossible for the picker to offer
 *    "Salary" on an Expense transaction (and vice versa), because such a
 *    category doesn't exist on the relevant list.
 *  - `forType()` returns the right list for each canonical type, is
 *    case-insensitive on the type string, and falls back to EXPENSE for
 *    unknown / blank types (conservative — see kdoc).
 *  - Both lists are non-empty so the dialog never renders an empty chip row.
 *  - "Salary" / "Freelance" are in INCOME, not EXPENSE — guards against
 *    a regression that puts them on the wrong side.
 *  - "Food" / "Fuel" / "Shopping" / "Bills" are in EXPENSE, not INCOME.
 *
 * Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s
 * data-integrity gate. Gate count after this lands: **30 → 38**
 * (3 C01 + 8 C02 + 10 C03a-Stage-1 + 9 C03a-Stage-2 + 8 C05).
 */
class CategoriesDataIntegrityTest {

    @Test
    fun `INCOME and EXPENSE lists do not overlap — the core C05 invariant`() {
        val intersection = Categories.INCOME.toSet() intersect Categories.EXPENSE.toSet()
        assertTrue(
            "INCOME ∩ EXPENSE must be empty so the picker can never offer a category that crosses types. " +
                "Offending entries: $intersection.",
            intersection.isEmpty(),
        )
    }

    @Test
    fun `INCOME contains the categories the audit names`() {
        // Audit acceptance: "Toggle to Income → see Salary/Freelance/Interest."
        for (expected in listOf("Salary", "Freelance", "Interest")) {
            assertTrue(
                "INCOME must contain '$expected' so the audit's acceptance scenario passes.",
                expected in Categories.INCOME,
            )
        }
    }

    @Test
    fun `EXPENSE contains the categories the audit names`() {
        // Audit acceptance: "Toggle to Expense → see Food/Fuel/Shopping/Bills."
        for (expected in listOf("Food", "Fuel", "Shopping", "Bills")) {
            assertTrue(
                "EXPENSE must contain '$expected' so the audit's acceptance scenario passes.",
                expected in Categories.EXPENSE,
            )
        }
    }

    @Test
    fun `income-flavoured categories are NOT in EXPENSE`() {
        for (incomeOnly in listOf("Salary", "Freelance", "Interest", "Dividend", "Loan Repayment")) {
            assertFalse(
                "'$incomeOnly' is an income category; it must not appear in EXPENSE (the C05 bug was exactly this kind of bleed).",
                incomeOnly in Categories.EXPENSE,
            )
        }
    }

    @Test
    fun `expense-flavoured categories are NOT in INCOME`() {
        for (expenseOnly in listOf("Food", "Fuel", "Shopping", "Bills", "Rent", "Healthcare", "Subscriptions")) {
            assertFalse(
                "'$expenseOnly' is an expense category; it must not appear in INCOME.",
                expenseOnly in Categories.INCOME,
            )
        }
    }

    @Test
    fun `forType returns the right list for the three canonical types`() {
        assertEquals(Categories.INCOME, Categories.forType("Income"))
        assertEquals(Categories.EXPENSE, Categories.forType("Expense"))
        assertEquals(Categories.TRANSFER, Categories.forType("Transfer"))
    }

    @Test
    fun `forType is case-insensitive on the type string`() {
        assertEquals(Categories.INCOME, Categories.forType("income"))
        assertEquals(Categories.INCOME, Categories.forType("INCOME"))
        assertEquals(Categories.EXPENSE, Categories.forType("eXpEnSe"))
        assertEquals(Categories.TRANSFER, Categories.forType("TRANSFER"))
    }

    @Test
    fun `forType falls back to EXPENSE for unknown or blank types`() {
        // Conservative default — see Categories.kt kdoc. Falling back to
        // INCOME would risk income-flavoured categories bleeding into
        // misformed/unknown-type transactions, which is the exact bug
        // C05 is fixing. EXPENSE is the historical default the picker
        // showed before C05, so this preserves existing UX intuition.
        assertEquals(Categories.EXPENSE, Categories.forType(""))
        assertEquals(Categories.EXPENSE, Categories.forType("Other"))
        assertEquals(Categories.EXPENSE, Categories.forType("???"))
    }

    @Test
    fun `both lists are non-empty so the picker never renders an empty chip row`() {
        assertTrue("INCOME must have at least one entry.", Categories.INCOME.isNotEmpty())
        assertTrue("EXPENSE must have at least one entry.", Categories.EXPENSE.isNotEmpty())
        assertTrue("TRANSFER must have at least one entry.", Categories.TRANSFER.isNotEmpty())
    }
}
