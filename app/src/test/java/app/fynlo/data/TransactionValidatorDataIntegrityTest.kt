package app.fynlo.data

import app.fynlo.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * C03a Stage 2 — Transaction category sanitization contract
 * (UX_AUDIT §C03 stage 3a item #5).
 *
 * Pure-function tests for [TransactionValidator]. Documents the exact set
 * of rejected category literals and the exact-string matching semantics
 * (the validator does NOT lowercase, fuzzy-match, or substring-match —
 * only the three exact strings "Expense", "Income", "Transfer" are
 * forbidden).
 *
 * Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s
 * data-integrity gate alongside the C01/C02/C03a-Stage-1 tests. Gate
 * count after this lands: **21 → 30** (3 C01 + 8 C02 + 10 C03a-Stage-1
 * + 9 C03a-Stage-2).
 */
class TransactionValidatorDataIntegrityTest {

    @Test
    fun `sanitizeCategory rewrites the three forbidden literal types to Uncategorized`() {
        assertEquals("Uncategorized", TransactionValidator.sanitizeCategory("Expense"))
        assertEquals("Uncategorized", TransactionValidator.sanitizeCategory("Income"))
        assertEquals("Uncategorized", TransactionValidator.sanitizeCategory("Transfer"))
    }

    @Test
    fun `sanitizeCategory passes legitimate categories through unchanged`() {
        for (legit in listOf("Food", "Fuel", "Salary", "Lending", "Loan Repayment", "Investment", "Shopping")) {
            assertEquals(
                "Legitimate category '$legit' must round-trip unchanged.",
                legit,
                TransactionValidator.sanitizeCategory(legit),
            )
        }
    }

    @Test
    fun `sanitizeCategory passes empty string through unchanged`() {
        assertEquals("", TransactionValidator.sanitizeCategory(""))
    }

    @Test
    fun `sanitizeCategory matches case-sensitively and exactly only`() {
        // Lowercase / mixed-case must NOT be rewritten — only the three
        // canonical literals "Expense"/"Income"/"Transfer" are forbidden.
        assertEquals("expense", TransactionValidator.sanitizeCategory("expense"))
        assertEquals("INCOME", TransactionValidator.sanitizeCategory("INCOME"))
        assertEquals("transferred", TransactionValidator.sanitizeCategory("transferred"))
        // Substring containment must also pass through.
        assertEquals("Expense Reimbursement", TransactionValidator.sanitizeCategory("Expense Reimbursement"))
        assertEquals("Income Tax", TransactionValidator.sanitizeCategory("Income Tax"))
    }

    @Test
    fun `sanitize() returns a copy with rewritten category when forbidden`() {
        val bad = Transaction(
            id       = "t1",
            date     = "2026-05-26",
            type     = "Expense",
            amount   = 100.0,
            fromAcct = "Cash",
            category = "Expense",
        )
        val out = TransactionValidator.sanitize(bad)
        assertEquals("Uncategorized", out.category)
        // Type / amount / accounts / etc. must pass through untouched.
        assertEquals("Expense", out.type)
        assertEquals(100.0, out.amount, 0.0)
        assertEquals("Cash", out.fromAcct)
    }

    @Test
    fun `sanitize() returns the SAME instance when category is already valid (no unnecessary allocation)`() {
        val good = Transaction(
            id       = "t1",
            date     = "2026-05-26",
            type     = "Expense",
            amount   = 100.0,
            category = "Food",
        )
        assertSame(
            "Idempotent fast path: untouched input must NOT allocate a new Transaction — saves GC pressure in the hot insert/edit paths.",
            good,
            TransactionValidator.sanitize(good),
        )
    }

    @Test
    fun `sanitize() always allocates a fresh copy when category was forbidden`() {
        val bad = Transaction(id = "t1", date = "2026-05-26", type = "Income", amount = 50.0, category = "Income")
        val out = TransactionValidator.sanitize(bad)
        assertNotSame(
            "The sanitized output must be a distinct copy so the caller's reference to the original is preserved.",
            bad, out,
        )
    }

    @Test
    fun `sanitize() is idempotent — applying twice yields the same result as once`() {
        val bad = Transaction(id = "t1", date = "2026-05-26", type = "Income", amount = 50.0, category = "Transfer")
        val once = TransactionValidator.sanitize(bad)
        val twice = TransactionValidator.sanitize(once)
        assertEquals(
            "Validator must be safe to call multiple times in the write pipeline.",
            once.category, twice.category,
        )
        assertEquals("Uncategorized", twice.category)
    }

    @Test
    fun `the FORBIDDEN_CATEGORIES set is exactly the three documented type-literals`() {
        // Documents the exact contract — if a future change wants to add
        // or remove a forbidden category, this test fails and forces the
        // contributor to read this kdoc and update the migration's
        // one-shot cleanup UPDATE in FynloDatabase.MIGRATION_16_17 too.
        assertEquals(
            setOf("Expense", "Income", "Transfer"),
            TransactionValidator.FORBIDDEN_CATEGORIES,
        )
        assertEquals("Uncategorized", TransactionValidator.FALLBACK_CATEGORY)
    }
}
