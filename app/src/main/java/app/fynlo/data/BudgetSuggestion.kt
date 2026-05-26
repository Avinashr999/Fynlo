package app.fynlo.data

/**
 * C04 Stage 3 — pure heuristic for the BudgetScreen "Set Category Limit"
 * dialog's category prefill (UX_AUDIT §C04).
 *
 * The audit's BudgetScreen criterion is **not** pure recency — it is
 * "pre-select the category most likely to need a budget," which the audit
 * defines as "the category with the biggest unmanaged spend." Recency is
 * only a fallback for when every spent-on category already has a budget
 * (or when there are no expense transactions at all on a fresh install).
 *
 * This object holds the pure-function form of that heuristic so it can be
 * unit-tested without a ViewModel, without Room, and without a coroutine
 * dispatcher. `FinanceViewModel.suggestBudgetCategory()` is a thin
 * adapter that reads `budgets.value` + `expenseAnalytics.value` and
 * delegates to [suggest].
 *
 * Pure constants — no Context, no I/O, no Room. Unit-testable from JVM.
 *
 * Matches the `*DataIntegrity*` test filter via
 * `BudgetSuggestionDataIntegrityTest`.
 */
object BudgetSuggestion {

    /**
     * System-generated expense categories that should NEVER appear as a
     * budget suggestion, even if they're the user's highest-spend uncapped
     * line. These represent non-discretionary financial activity that
     * doesn't fit a "monthly cap" model:
     *
     *   - **"Lending"** — outbound loan to a borrower. Auto-created by
     *     `FinanceRepository.insertBorrowerWithSource` whenever the user
     *     lends money. The user expects it back; capping it would prevent
     *     them from making loans they fully intend to recover.
     *   - **"Investment"** — asset purchase (stocks, gold, FD, etc.).
     *     Auto-created when the user funds an investment from cash, debt,
     *     or a new loan. The money is still owned, just in a different
     *     form; it's not consumption.
     *   - **"Interest Expense"** — accrued interest on debts. Auto-created
     *     as a `journal_only` entry by the interest-engine code path. The
     *     user can't "spend less" on accrued interest within a month —
     *     it's a function of the debt principal and rate, not behaviour.
     *   - **"Balance Correction"** — manual account adjustment from
     *     `quickEditBalance`. Internal book-keeping, never user-facing
     *     spend.
     *   - **"Bad Debt"** — borrower write-off journal entry. Loss
     *     accounting, not discretionary spend.
     *
     * Surfaced as a smoke-test finding on 3.2.6 install — the heuristic
     * was correctly picking "Lending" because the user had lent money,
     * but that's the opposite of what a "what should I budget?" answer
     * should look like. Users can still pick any of these manually from
     * the AddBudget chip list if they really want to; this filter only
     * affects the auto-suggestion.
     */
    val NON_DISCRETIONARY_CATEGORIES: Set<String> = setOf(
        "Lending",
        "Investment",
        "Interest Expense",
        "Balance Correction",
        "Bad Debt",
    )

    /**
     * Returns the EXPENSE category most likely to need a budget given the
     * user's current state, or `null` when no such category exists.
     *
     * The "best candidate" is defined as: the category present in
     * [expenseAnalytics] (i.e., the user has actually spent on it),
     * with strictly positive spend, that does NOT yet appear in
     * [cappedCategories] (the categories that already have a budget),
     * that is NOT a [NON_DISCRETIONARY_CATEGORIES] system category
     * (lending, investment, accrued interest, etc.), and that has the
     * highest spend among such candidates.
     *
     * Returns `null` in four distinct scenarios:
     *   1. `expenseAnalytics` is empty (fresh install, no expenses logged).
     *   2. Every category in `expenseAnalytics` is also in `cappedCategories`
     *      (the user has already budgeted everything they've spent on).
     *   3. Every remaining uncapped category is in
     *      [NON_DISCRETIONARY_CATEGORIES] (e.g., user only has lending /
     *      investment activity logged so far).
     *   4. The remaining uncapped categories all have zero / negative spend
     *      (defensive — shouldn't happen in practice since `expenseAnalytics`
     *      is derived from `sumOf { it.amount }` over positive expense
     *      transactions, but a stray zero-amount entry would otherwise
     *      win the `maxByOrNull` tie-break against nothing).
     *
     * Blank-category entries in `expenseAnalytics` are ignored —
     * historical / migrated rows occasionally have a blank `category`
     * field, and surfacing "" as the suggested chip would render an empty
     * pill that the user couldn't deselect.
     *
     * @param cappedCategories  the set of category names that already
     *                          have a budget row. Read from
     *                          `budgets.value.map { it.category }.toSet()`
     *                          in the ViewModel adapter.
     * @param expenseAnalytics  `category → total-spend` map for the
     *                          current project's expense transactions.
     *                          Read from `expenseAnalytics.value` in the
     *                          ViewModel adapter.
     */
    fun suggest(
        cappedCategories: Set<String>,
        expenseAnalytics: Map<String, Double>,
    ): String? =
        expenseAnalytics
            .asSequence()
            .filter { (cat, spent) ->
                cat.isNotBlank() &&
                    spent > 0.0 &&
                    cat !in cappedCategories &&
                    cat !in NON_DISCRETIONARY_CATEGORIES
            }
            .maxByOrNull { it.value }
            ?.key
}
