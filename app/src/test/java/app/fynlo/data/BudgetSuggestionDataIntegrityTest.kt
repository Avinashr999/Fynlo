package app.fynlo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C04 Stage 3 — [BudgetSuggestion.suggest] invariants (UX_AUDIT §C04).
 *
 * Pure-function tests over the highest-uncapped-spend heuristic that
 * `FinanceViewModel.suggestBudgetCategory()` delegates to. The
 * BudgetScreen's "Set Category Limit" dialog uses this as the **first**
 * step in its chained-fallback prefill:
 *
 *   1. [BudgetSuggestion.suggest] — highest-spend EXPENSE not yet capped.
 *   2. If null → `viewModel.rememberLastBudgetCategory()` — pure recency.
 *   3. If still null → blank.
 *
 * The audit's reasoning: "the category most likely to need a budget is
 * the one with the biggest unmanaged spend, not the one most recently
 * used." Recency is the fallback, NOT the primary signal.
 *
 * Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s
 * data-integrity CI gate.
 */
class BudgetSuggestionDataIntegrityTest {

    // ── Empty-input edge cases ────────────────────────────────────────────

    @Test
    fun `empty budgets and empty expenseAnalytics returns null`() {
        // Fresh install: no budgets, no expenses. The chained fallback in
        // the dialog falls through to recency (also empty) and then blank.
        assertNull(
            "With no expenses to analyse, the heuristic has nothing to suggest.",
            BudgetSuggestion.suggest(
                cappedCategories = emptySet(),
                expenseAnalytics = emptyMap(),
            ),
        )
    }

    @Test
    fun `empty budgets and some expenses returns the highest-spend category`() {
        // Typical first-time-budget scenario: user has been logging
        // expenses for weeks, never set a budget. The dialog should
        // pre-select the biggest spend category.
        val result = BudgetSuggestion.suggest(
            cappedCategories = emptySet(),
            expenseAnalytics = mapOf(
                "Food"     to 1_200.0,
                "Fuel"     to   800.0,
                "Shopping" to 3_500.0,  // biggest
                "Rent"     to 2_000.0,
            ),
        )
        assertEquals("Shopping", result)
    }

    // ── Fully-capped: heuristic returns null so recency takes over ────────

    @Test
    fun `every spent category already budgeted returns null`() {
        // Mature user: every category they've spent on has a budget.
        // Heuristic returns null → the dialog falls back to recency.
        assertNull(
            "When every spent-on category is already capped, the heuristic should yield to the recency fallback.",
            BudgetSuggestion.suggest(
                cappedCategories = setOf("Food", "Fuel", "Shopping"),
                expenseAnalytics = mapOf(
                    "Food"     to 1_200.0,
                    "Fuel"     to   800.0,
                    "Shopping" to 3_500.0,
                ),
            ),
        )
    }

    // ── Mixed: the load-bearing case ──────────────────────────────────────

    @Test
    fun `some categories budgeted returns the highest-spend uncapped one`() {
        // The audit's exact scenario: user has budgets on Food and Rent,
        // but their biggest unmanaged spend is Shopping. Even though Rent
        // (2_000) > Shopping (1_500) in absolute terms, Rent is already
        // capped — so Shopping is the right suggestion.
        val result = BudgetSuggestion.suggest(
            cappedCategories = setOf("Food", "Rent"),
            expenseAnalytics = mapOf(
                "Food"     to 1_200.0,
                "Fuel"     to   800.0,
                "Shopping" to 1_500.0,  // biggest uncapped
                "Rent"     to 2_000.0,  // bigger but capped
            ),
        )
        assertEquals("Shopping", result)
    }

    @Test
    fun `tie-break behaviour is deterministic — does not crash`() {
        // Two uncapped categories with identical spend. `maxByOrNull` is
        // documented to return *some* maximum (the first one encountered
        // in iteration order). We don't pin which one — that's an
        // implementation detail of LinkedHashMap iteration order — but
        // we DO pin that the result is non-null and is one of the two.
        val result = BudgetSuggestion.suggest(
            cappedCategories = emptySet(),
            expenseAnalytics = linkedMapOf(
                "Food"     to 1_000.0,
                "Shopping" to 1_000.0,
            ),
        )
        assertTrue(
            "Tie-break must return one of the tied categories, not null.",
            result == "Food" || result == "Shopping",
        )
    }

    // ── Defensive filters ─────────────────────────────────────────────────

    @Test
    fun `blank category in expenseAnalytics is ignored`() {
        // Historical / migrated transactions occasionally have a blank
        // `category` field. Surfacing "" as the suggestion would render
        // an empty chip that the user can't easily deselect.
        val result = BudgetSuggestion.suggest(
            cappedCategories = emptySet(),
            expenseAnalytics = mapOf(
                ""     to 9_999.0,   // largest, but blank — must be filtered
                "Food" to 1_200.0,
            ),
        )
        assertEquals(
            "Blank-category entries must be filtered before the max-by step.",
            "Food",
            result,
        )
    }

    @Test
    fun `zero-spend category is ignored`() {
        // Shouldn't happen in practice (expenseAnalytics is derived from
        // sumOf on positive amounts), but defensively excluded so a stray
        // zero-amount entry can't win a max-by against an empty set.
        val result = BudgetSuggestion.suggest(
            cappedCategories = emptySet(),
            expenseAnalytics = mapOf(
                "Food"     to 0.0,
                "Shopping" to 100.0,
            ),
        )
        assertEquals("Shopping", result)
    }

    @Test
    fun `all-zero spend with empty budgets returns null`() {
        // Edge case: every category in the map has zero spend. Nothing
        // meaningful to suggest; let the recency fallback take over.
        assertNull(
            BudgetSuggestion.suggest(
                cappedCategories = emptySet(),
                expenseAnalytics = mapOf(
                    "Food"     to 0.0,
                    "Shopping" to 0.0,
                ),
            ),
        )
    }

    @Test
    fun `negative-spend category is ignored`() {
        // Defensive: `spent > 0.0` strictly excludes negatives. A
        // negative `expenseAnalytics` value would indicate corrupt
        // analytics (refunds incorrectly summed as negative expenses);
        // we don't want such a row to surface as a suggestion.
        val result = BudgetSuggestion.suggest(
            cappedCategories = emptySet(),
            expenseAnalytics = mapOf(
                "Food"     to -50.0,    // negative — ignored
                "Shopping" to  10.0,
            ),
        )
        assertEquals("Shopping", result)
    }

    // ── Capped-set semantics ──────────────────────────────────────────────

    @Test
    fun `capped category is excluded even if it has the highest spend`() {
        // Direct read of the contract: a category appearing in
        // `cappedCategories` is never returned, regardless of its spend
        // ranking.
        val result = BudgetSuggestion.suggest(
            cappedCategories = setOf("Shopping"),
            expenseAnalytics = mapOf(
                "Food"     to   800.0,
                "Shopping" to 9_999.0,  // biggest but capped
            ),
        )
        assertEquals("Food", result)
        assertFalse("Capped category must never be returned.", result == "Shopping")
    }

    @Test
    fun `capped category that has no spend record does not affect suggestion`() {
        // The user budgeted a category they haven't spent on yet (e.g.,
        // an aspirational "Education" budget). That budget shouldn't
        // shadow the highest-spend uncapped category.
        val result = BudgetSuggestion.suggest(
            cappedCategories = setOf("Education"),
            expenseAnalytics = mapOf(
                "Food"     to 1_200.0,
                "Shopping" to 3_500.0,
            ),
        )
        assertEquals("Shopping", result)
    }

    @Test
    fun `single uncapped category with positive spend is returned`() {
        val result = BudgetSuggestion.suggest(
            cappedCategories = emptySet(),
            expenseAnalytics = mapOf("Food" to 1.0),
        )
        assertEquals("Food", result)
    }
}
