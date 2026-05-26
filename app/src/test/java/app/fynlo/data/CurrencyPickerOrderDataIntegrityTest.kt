package app.fynlo.data

import app.fynlo.ui.screens.buildCurrencyPickerOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C04 Stage 3 — `buildCurrencyPickerOrder` invariants.
 *
 * The Settings currency picker renders two groups: a "Recently used" section
 * (top, ≤5 entries, most-recent first) and the full alphabetical list below.
 * This helper is the flat-list form of that order with dedupe — useful when
 * a caller needs a single sequence (e.g., keyboard nav, search filter) and
 * load-bearing because the alternative (rendering recents + full naïvely)
 * would either duplicate codes or shuffle the alphabetical group.
 *
 * Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s
 * data-integrity CI gate.
 */
class CurrencyPickerOrderDataIntegrityTest {

    private val full = listOf("AED", "AUD", "BDT", "CAD", "EUR", "GBP", "INR", "JPY", "LKR", "MYR", "NPR", "QAR", "SAR", "SGD", "USD")

    @Test
    fun `empty recent yields the full list verbatim`() {
        assertEquals(
            "When no recents exist (fresh install), the picker should show " +
                "the full list in the order supplied.",
            full,
            buildCurrencyPickerOrder(emptyList(), full),
        )
    }

    @Test
    fun `recent entries appear before the full list`() {
        val order = buildCurrencyPickerOrder(
            recent = listOf("USD", "EUR"),
            full = full,
        )
        assertEquals(
            "Recent entries must lead, most-recent first.",
            listOf("USD", "EUR"),
            order.take(2),
        )
    }

    @Test
    fun `recent and full are deduplicated - codes in both appear exactly once`() {
        val order = buildCurrencyPickerOrder(
            recent = listOf("USD", "INR"),
            full = full,
        )
        assertEquals(
            "USD should appear exactly once (in the recent group, not also in full).",
            1,
            order.count { it == "USD" },
        )
        assertEquals(
            "INR should appear exactly once.",
            1,
            order.count { it == "INR" },
        )
    }

    @Test
    fun `recent preserves its own order, not alphabetical`() {
        // The recency layer is most-recent-first, so the helper must NOT
        // re-sort it alphabetically — that would defeat the whole point.
        val order = buildCurrencyPickerOrder(
            recent = listOf("USD", "EUR", "AED"),
            full = full,
        )
        assertEquals(
            "Recent group must preserve its most-recent-first ordering.",
            listOf("USD", "EUR", "AED"),
            order.take(3),
        )
    }

    @Test
    fun `full list preserves its own order after recents`() {
        val recent = listOf("USD")
        val order = buildCurrencyPickerOrder(recent, full)
        // After dropping the leading "USD", the tail must equal the full
        // list with USD removed (everything else in the order supplied).
        assertEquals(
            "Full-list order (typically alphabetical) must be preserved.",
            full.filterNot { it == "USD" },
            order.drop(1),
        )
    }

    @Test
    fun `blank entries are ignored from both inputs`() {
        // The recency tracker shouldn't record blanks but defence-in-depth
        // matters here — a malformed pref blob mustn't render a "" row.
        val order = buildCurrencyPickerOrder(
            recent = listOf("USD", "", "  "),
            full = listOf("EUR", "", "INR"),
        )
        assertEquals(
            "Blank / whitespace-only entries must be dropped.",
            listOf("USD", "EUR", "INR"),
            order,
        )
    }

    @Test
    fun `duplicates within recent are deduplicated, first occurrence wins`() {
        // The recency tracker upstream guarantees uniqueness, but if a
        // caller somehow passes a duplicated list, the helper must still
        // produce a clean order rather than rendering "USD" twice.
        val order = buildCurrencyPickerOrder(
            recent = listOf("USD", "EUR", "USD"),
            full = listOf("USD", "EUR", "INR"),
        )
        assertEquals(
            listOf("USD", "EUR", "INR"),
            order,
        )
    }

    @Test
    fun `result contains exactly the union of unique blank-stripped codes`() {
        val order = buildCurrencyPickerOrder(
            recent = listOf("USD", "EUR"),
            full = full,
        )
        // No code lost, no code added.
        assertEquals(
            (listOf("USD", "EUR") + full).toSet(),
            order.toSet(),
        )
        assertEquals(
            "Result size must equal the deduplicated union size.",
            (listOf("USD", "EUR") + full).toSet().size,
            order.size,
        )
    }
}
