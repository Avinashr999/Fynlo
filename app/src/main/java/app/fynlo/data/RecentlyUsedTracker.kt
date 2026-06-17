package app.fynlo.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C04 — per-form, per-field "what did the user pick last" memory.
 *
 * Thin façade over `UserPreferences.recentlyUsed(...)` /
 * `editRecentlyUsed(...)`. The dedup-and-cap logic lives in pure
 * `RecentlyUsedLogic` so call sites (and unit tests) can reason about
 * the contract without needing Context / DataStore.
 *
 * Two ways call sites use this:
 *  - **Recording**: after a form's submit, `record(formId, fieldId, value)`
 *    bumps the chosen value to the top of its slot (deduping if already
 *    present, capping the slot at 5 entries).
 *  - **Prefilling**: on a form's open, `last(formId, fieldId)` returns the
 *    most recently used value (or `null` for a fresh install), and
 *    `observeTopN(formId, fieldId)` exposes the ≤5 list a picker can use
 *    to render its recently-used group at the top.
 *
 * Form IDs and field IDs are plain strings. Call sites should use the
 * constants under [FormIds] / [FieldIds] to avoid typos colliding into
 * unrelated slots. The composite key persists across app upgrades — bumping
 * versionName doesn't reset recency.
 */
@Singleton
class RecentlyUsedTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Records [value] as recently-used in the [formId]/[fieldId] slot.
     * No-op if [value] is blank (picker fields with no selection shouldn't
     * pollute recency). Bumps an existing entry to the top instead of
     * duplicating it; caps the slot at `RecentlyUsedLogic.MAX_ENTRIES` (5).
     */
    suspend fun record(formId: String, fieldId: String, value: String) {
        if (value.isBlank()) return
        val now = System.currentTimeMillis()
        UserPreferences.editRecentlyUsed(context) { current ->
            RecentlyUsedLogic.add(current, formId, fieldId, value, now)
        }
    }

    /**
     * Most-recently-used value for the slot, or `null` if the slot is empty.
     * Used by form-open prefills.
     */
    suspend fun last(formId: String, fieldId: String): String? {
        val snapshot = UserPreferences.recentlyUsed(context).first()
        return RecentlyUsedLogic.last(snapshot, formId, fieldId)
    }

    /**
     * Reactive top-N list (most-recent first) for rendering the
     * recently-used group at the top of a picker dropdown. Default `n`
     * matches the audit's "≤5 items" group size.
     */
    fun observeTopN(
        formId: String,
        fieldId: String,
        n: Int = RecentlyUsedLogic.MAX_ENTRIES,
    ): Flow<List<String>> =
        UserPreferences.recentlyUsed(context).map { snapshot ->
            RecentlyUsedLogic.topN(snapshot, formId, fieldId, n)
        }

    /**
     * Convention strings for the `formId` argument. Centralised so a typo
     * at one call site can't silently address a different slot than another.
     */
    object FormIds {
        const val ADD_TRANSACTION = "add_transaction"
        const val EDIT_TRANSACTION = "edit_transaction"
        const val ADD_RECURRING = "add_recurring"
        const val ADD_BUDGET = "add_budget"
        const val SETTINGS_CURRENCY = "settings_currency"
    }

    /**
     * Convention strings for the `fieldId` argument. The category fields are
     * split by transaction type (`category_income` / `category_expense`)
     * because the audit's C04 fix specifies "default to user's most-recently-
     * used category **for the current type**" — a single shared
     * `"category"` slot would leak across the same Income/Expense boundary
     * that C05 just fixed.
     */
    object FieldIds {
        const val CATEGORY_INCOME = "category_income"
        const val CATEGORY_EXPENSE = "category_expense"
        const val FROM_ACCOUNT = "from_account"
        const val TO_ACCOUNT = "to_account"
        const val CURRENCY = "currency"
    }
}
