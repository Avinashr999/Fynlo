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
     * Returns the EXPENSE category most likely to need a budget given the
     * user's current state, or `null` when no such category exists.
     *
     * The "best candidate" is defined as: the category present in
     * [expenseAnalytics] (i.e., the user has actually spent on it),
     * with strictly positive spend, that does NOT yet appear in
     * [cappedCategories] (the categories that already have a budget),
     * and that has the highest spend among such candidates.
     *
     * Returns `null` in three distinct scenarios:
     *   1. `expenseAnalytics` is empty (fresh install, no expenses logged).
     *   2. Every category in `expenseAnalytics` is also in `cappedCategories`
     *      (the user has already budgeted everything they've spent on).
     *   3. The remaining uncapped categories all have zero / negative spend
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
                cat.isNotBlank() && spent > 0.0 && cat !in cappedCategories
            }
            .maxByOrNull { it.value }
            ?.key
}
