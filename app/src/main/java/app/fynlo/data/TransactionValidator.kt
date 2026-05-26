package app.fynlo.data

import app.fynlo.data.model.Transaction

/**
 * C03a Stage 2 — runtime guard against the literal-type-as-category bug
 * (UX_AUDIT §C03 stage 3a item #5).
 *
 * The audit observed one transaction in the wild with `type = "Expense"`
 * AND `category = "Expense"` — the category dropdown had "Expense" /
 * "Income" / "Transfer" as picker options, which is wrong: those are
 * TRANSACTION TYPES, never categories. A user who tapped that option
 * ended up with a meaningless category label that breaks downstream
 * P&L grouping (the "Expense" category bucket would lump unrelated
 * spending together).
 *
 * This validator sits at every Transaction write call site
 * (`FinanceRepository.insertTransaction` and the new-side of
 * `FinanceRepository.editTransaction`) and rewrites any of the three
 * forbidden literals to the sentinel `"Uncategorized"`. The user can
 * pick a real category on their next edit; the bad value never reaches
 * SQLite.
 *
 * The matching is **case-sensitive and exact** — only the three literal
 * strings are forbidden. Legitimate categories that happen to share a
 * substring (e.g., "Expense Reimbursement" if a user creates one,
 * "Income Tax") are untouched.
 *
 * Existing rows with bad categories are cleaned up by the one-shot
 * `UPDATE transactions SET category = 'Uncategorized' ...` inside
 * `MIGRATION_16_17`.
 *
 * Pure functions only — no Context, no Room. Unit-testable from JVM.
 */
object TransactionValidator {

    /**
     * The three literal `category` values that are actually transaction
     * types. None of these should ever live in the category column.
     */
    val FORBIDDEN_CATEGORIES: Set<String> = setOf("Expense", "Income", "Transfer")

    /**
     * The sentinel that replaces any forbidden category. Surfaces as a
     * neutral "Uncategorized" tag in the UI so the user can pick a real
     * category on the next edit.
     */
    const val FALLBACK_CATEGORY: String = "Uncategorized"

    /**
     * Replaces a forbidden literal category with [FALLBACK_CATEGORY];
     * passes everything else through unchanged. Idempotent — safe to
     * apply at every write call site without re-checking who already ran it.
     */
    fun sanitizeCategory(category: String): String =
        if (category in FORBIDDEN_CATEGORIES) FALLBACK_CATEGORY else category

    /**
     * Convenience: returns a `Transaction` with its `category` sanitized.
     * Apply this at the boundary of every write — `insertTransaction` and
     * the new-side of `editTransaction`. Other fields untouched.
     */
    fun sanitize(transaction: Transaction): Transaction =
        if (transaction.category in FORBIDDEN_CATEGORIES)
            transaction.copy(category = FALLBACK_CATEGORY)
        else
            transaction
}
