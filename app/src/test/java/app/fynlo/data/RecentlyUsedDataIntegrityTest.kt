package app.fynlo.data

import app.fynlo.data.model.RecentEntry
import app.fynlo.data.model.RecentlyUsedSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C04 Stage 1 — `RecentlyUsedLogic` invariants (UX_AUDIT §C04).
 *
 * Pure-function tests over the dedup / cap / per-slot-isolation contract
 * that `RecentlyUsedTracker` and its UI call sites depend on. No
 * Robolectric, no DataStore — the I/O concerns live in
 * `UserPreferences.editRecentlyUsed` / `recentlyUsed(...)` and are
 * tested separately (or, more honestly, are exercised through a
 * manual on-device smoke during Stage 2 wiring).
 *
 * Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s
 * data-integrity CI gate. Gate count after this lands: **39 → 50**
 * (3 C01 + 8 C02 + 10 C03a-Stage-1 + 9 C03a-Stage-2 + 9 C05 + 11 C04).
 */
class RecentlyUsedDataIntegrityTest {

    private val empty = RecentlyUsedSnapshot()

    @Test
    fun `last on an empty snapshot returns null`() {
        assertNull(RecentlyUsedLogic.last(empty, "form_a", "field_x"))
    }

    @Test
    fun `topN on an empty snapshot returns an empty list`() {
        assertEquals(emptyList<String>(), RecentlyUsedLogic.topN(empty, "form_a", "field_x"))
    }

    @Test
    fun `add records the value and last returns it`() {
        val s = RecentlyUsedLogic.add(empty, "form_a", "field_x", "Food", now = 1_000L)
        assertEquals("Food", RecentlyUsedLogic.last(s, "form_a", "field_x"))
    }

    @Test
    fun `add deduplicates and bumps existing value to the top`() {
        // Three distinct values, then re-add the oldest one.
        val s = empty
            .let { RecentlyUsedLogic.add(it, "form", "cat", "Food",     now = 1_000L) }
            .let { RecentlyUsedLogic.add(it, "form", "cat", "Fuel",     now = 2_000L) }
            .let { RecentlyUsedLogic.add(it, "form", "cat", "Shopping", now = 3_000L) }
            .let { RecentlyUsedLogic.add(it, "form", "cat", "Food",     now = 4_000L) }  // re-record

        val top = RecentlyUsedLogic.topN(s, "form", "cat")
        assertEquals(
            "Food must be at the top (most recent) and must not appear twice.",
            listOf("Food", "Shopping", "Fuel"),
            top,
        )
    }

    @Test
    fun `add caps a slot at MAX_ENTRIES (5) and drops the oldest`() {
        val cats = listOf("A", "B", "C", "D", "E", "F")
        val populated = cats.foldIndexed(empty) { i, acc, c ->
            RecentlyUsedLogic.add(acc, "form", "cat", c, now = (i + 1) * 1_000L)
        }
        val top = RecentlyUsedLogic.topN(populated, "form", "cat", n = RecentlyUsedLogic.MAX_ENTRIES + 5)
        assertEquals(
            "After adding 6 distinct values, the oldest (A) must fall off and the slot must hold exactly MAX_ENTRIES entries, most-recent first.",
            listOf("F", "E", "D", "C", "B"),
            top,
        )
    }

    @Test
    fun `add ignores blank values (no slot pollution)`() {
        val s = empty
            .let { RecentlyUsedLogic.add(it, "form", "cat", "",   now = 1_000L) }
            .let { RecentlyUsedLogic.add(it, "form", "cat", "  ", now = 2_000L) }
            .let { RecentlyUsedLogic.add(it, "form", "cat", "\t", now = 3_000L) }
        assertNull(
            "Blank / whitespace-only values must not pollute the slot — `last()` returns null on an effectively-empty slot.",
            RecentlyUsedLogic.last(s, "form", "cat"),
        )
    }

    @Test
    fun `slots are isolated by formId`() {
        val s = empty
            .let { RecentlyUsedLogic.add(it, "add_transaction", "category", "Food",   1_000L) }
            .let { RecentlyUsedLogic.add(it, "add_recurring",   "category", "Salary", 2_000L) }
        assertEquals("Food",   RecentlyUsedLogic.last(s, "add_transaction", "category"))
        assertEquals("Salary", RecentlyUsedLogic.last(s, "add_recurring",   "category"))
    }

    @Test
    fun `slots are isolated by fieldId`() {
        // Within one form, two fields must not share recency — e.g.,
        // category_income vs category_expense, which is exactly the C04
        // motivation for distinct fieldIds across the type toggle.
        val s = empty
            .let { RecentlyUsedLogic.add(it, "add_transaction", "category_expense", "Food",   1_000L) }
            .let { RecentlyUsedLogic.add(it, "add_transaction", "category_income",  "Salary", 2_000L) }
        assertEquals("Food",   RecentlyUsedLogic.last(s, "add_transaction", "category_expense"))
        assertEquals("Salary", RecentlyUsedLogic.last(s, "add_transaction", "category_income"))
    }

    @Test
    fun `topN respects the requested limit and is clamped at MAX_ENTRIES`() {
        val populated = listOf("A","B","C","D","E").foldIndexed(empty) { i, acc, c ->
            RecentlyUsedLogic.add(acc, "form", "cat", c, now = (i + 1) * 1_000L)
        }
        assertEquals(listOf("E", "D"), RecentlyUsedLogic.topN(populated, "form", "cat", n = 2))
        // n larger than MAX_ENTRIES gets clamped — the slot only holds 5.
        assertEquals(
            listOf("E", "D", "C", "B", "A"),
            RecentlyUsedLogic.topN(populated, "form", "cat", n = 100),
        )
    }

    @Test
    fun `add is pure — original snapshot remains unchanged`() {
        val before = empty
        val after  = RecentlyUsedLogic.add(before, "f", "c", "X", 1L)
        assertEquals(
            "RecentlyUsedLogic.add must return a new snapshot without mutating the input — DataStore writes assume value-semantics.",
            RecentlyUsedSnapshot(),
            before,
        )
        assertEquals("X", RecentlyUsedLogic.last(after, "f", "c"))
    }

    @Test
    fun `keyOf produces the expected composite key format`() {
        assertEquals("add_transaction/category_expense",
            RecentlyUsedLogic.keyOf("add_transaction", "category_expense"))
    }

    @Test
    fun `lastUsedAt is recorded so the timestamp is available downstream`() {
        // The list-returning APIs (topN, last) hide the timestamp, but
        // future features (e.g., "purge entries older than 90 days")
        // need it on disk. Spot-check via direct slot inspection.
        val s = RecentlyUsedLogic.add(empty, "f", "c", "Food", now = 1_700_000_000_000L)
        val slot = s.slots[RecentlyUsedLogic.keyOf("f", "c")]!!
        assertEquals(1, slot.size)
        assertEquals(RecentEntry(value = "Food", lastUsedAt = 1_700_000_000_000L), slot.first())
    }
}
