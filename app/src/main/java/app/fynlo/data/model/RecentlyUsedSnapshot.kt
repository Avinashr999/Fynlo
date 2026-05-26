package app.fynlo.data.model

import kotlinx.serialization.Serializable

/**
 * C04 — recently-used picker memory (UX_AUDIT §C04).
 *
 * Stored as a single JSON blob in DataStore under the `recently_used`
 * preference key. The top-level [slots] map is keyed by
 * `"<formId>/<fieldId>"` (e.g. `"add_transaction/category_expense"`),
 * and each slot holds a list of [RecentEntry] **most-recent first**,
 * capped at `RecentlyUsedLogic.MAX_ENTRIES` (5).
 *
 * Why a single JSON blob instead of a Room table:
 *  - The dataset is tiny (5 entries × ~10 form/field combinations ≤ 50
 *    rows). Room's per-query overhead exceeds JSON parse cost at this
 *    scale.
 *  - No new schema version / migration is needed.
 *  - kotlinx-serialization's default-value semantics give us free
 *    forward-compatibility: a future field added with a default will
 *    decode cleanly from older stored blobs.
 *
 * @property slots map from `"formId/fieldId"` → recency-ordered list.
 *           Missing key = no recently-used values for that slot.
 */
@Serializable
data class RecentlyUsedSnapshot(
    val slots: Map<String, List<RecentEntry>> = emptyMap(),
)

/**
 * One recently-used value for a single picker slot.
 *
 * @property value the user-facing string the picker stored (e.g. "Food",
 *                 "HDFC Bank"). Always trimmed and non-blank — the
 *                 [RecentlyUsedLogic.add] helper drops blank inputs and
 *                 the [RecentlyUsedTracker.record] entry point does too.
 * @property lastUsedAt epoch millis of the most recent time this value
 *                       was recorded. The list is sorted descending by
 *                       this field; dedup operations bump existing
 *                       entries to the top instead of duplicating them.
 */
@Serializable
data class RecentEntry(
    val value: String,
    val lastUsedAt: Long,
)
