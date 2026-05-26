package app.fynlo.data

import app.fynlo.data.model.RecentEntry
import app.fynlo.data.model.RecentlyUsedSnapshot

/**
 * C04 — pure helpers for the recently-used picker memory (UX_AUDIT §C04).
 *
 * The audit asks for:
 *   1. A `RecentlyUsedTracker` with per-form, per-field memory.
 *   2. Prefill on form open with `recentlyUsed.last(formId, fieldId)`.
 *   3. Picker dropdowns sort: recently-used (top, ≤5) → alphabetical (rest).
 *
 * This `object` is the *logic* side — pure, deterministic, no Context, no
 * I/O. The `RecentlyUsedTracker` class wraps these helpers with DataStore
 * I/O behind `UserPreferences`. Splitting the two means the dedup, cap,
 * and per-slot isolation contracts are unit-testable from plain JVM
 * without Robolectric or DataStore plumbing.
 */
object RecentlyUsedLogic {

    /**
     * Maximum number of recently-used entries kept per `(formId, fieldId)`
     * slot. Matches the audit's "≤5 items" requirement for the
     * recently-used group at the top of picker dropdowns.
     */
    const val MAX_ENTRIES: Int = 5

    /**
     * Composite map key. Public so the `RecentlyUsedTracker` and call
     * sites can compute it without rediscovering the formula.
     */
    fun keyOf(formId: String, fieldId: String): String = "$formId/$fieldId"

    /**
     * Returns a new [RecentlyUsedSnapshot] with [value] recorded for the
     * given slot. Behaviour:
     *   - **Blank input is rejected** — returns [snapshot] unchanged.
     *     Picker fields with no selection shouldn't pollute recency.
     *   - **Dedup**: if an entry for [value] already exists in the slot,
     *     it is removed first, so the result has [value] exactly once
     *     and at the top (most recent).
     *   - **Cap**: the resulting list is truncated to [MAX_ENTRIES] —
     *     the oldest entry falls off if the user already had 5 distinct
     *     values for this slot.
     *   - Other slots are untouched.
     */
    fun add(
        snapshot: RecentlyUsedSnapshot,
        formId: String,
        fieldId: String,
        value: String,
        now: Long,
    ): RecentlyUsedSnapshot {
        if (value.isBlank()) return snapshot
        val key = keyOf(formId, fieldId)
        val existing = snapshot.slots[key] ?: emptyList()
        val withoutDup = existing.filter { it.value != value }
        val updatedList = (listOf(RecentEntry(value, now)) + withoutDup).take(MAX_ENTRIES)
        return snapshot.copy(slots = snapshot.slots + (key to updatedList))
    }

    /**
     * Returns the top-N recently-used **values** (string only — the
     * `lastUsedAt` is internal). The list is most-recent first. If [n]
     * is greater than [MAX_ENTRIES], it's silently clamped; callers can
     * still pass `n = MAX_ENTRIES` for the audit's "top 5" picker group.
     * Missing slot → empty list.
     */
    fun topN(
        snapshot: RecentlyUsedSnapshot,
        formId: String,
        fieldId: String,
        n: Int = MAX_ENTRIES,
    ): List<String> {
        val key = keyOf(formId, fieldId)
        val entries = snapshot.slots[key] ?: return emptyList()
        return entries.take(n.coerceAtMost(MAX_ENTRIES)).map { it.value }
    }

    /**
     * Convenience: the most-recently-used value for the slot, or `null`
     * if the slot has no entries. Used by the audit's step 2 prefill
     * (`recentlyUsed.last(formId, fieldId)`).
     */
    fun last(
        snapshot: RecentlyUsedSnapshot,
        formId: String,
        fieldId: String,
    ): String? = topN(snapshot, formId, fieldId, n = 1).firstOrNull()
}
