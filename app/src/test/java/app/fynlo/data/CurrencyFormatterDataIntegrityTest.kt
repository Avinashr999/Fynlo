package app.fynlo.data

import app.fynlo.logic.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * C08 Stage 1 — [CurrencyFormatter] invariants (UX_AUDIT §C08).
 *
 * Pure-function tests over the six display styles the audit specifies.
 * Stage 2+ migrates the ~257 call sites that this object replaces; these
 * tests pin the formatter's contract so that sweep can be done confidently.
 *
 * Matches the `*DataIntegrity*` filter — picked up by `checks.yml`'s
 * data-integrity CI gate.
 *
 * File lives under `app/src/test/java/app/fynlo/data/` rather than `.../logic/`
 * to keep all `*DataIntegrity*` tests in one package for the CI gate's
 * pattern match — convention established by `BackupDataIntegrityTest`,
 * `RecentlyUsedDataIntegrityTest`, etc.
 */
class CurrencyFormatterDataIntegrityTest {

    // ── Hero / Detail — full number, no decimals, locale-correct grouping ─

    @Test
    fun `hero INR uses Indian lakh-crore grouping`() {
        // The audit's headline example: ₹241,663 (Western comma) is wrong
        // for an Indian-currency project. Must render as ₹2,41,663 with
        // the lakh-grouping break after the first three digits from the
        // right, then groups of two.
        assertEquals("₹2,41,663", CurrencyFormatter.hero(241_663.0, "INR"))
    }

    @Test
    fun `hero INR for crore-scale numbers`() {
        // 1.5 crore = 1,50,00,000 (two-digit groups all the way up).
        assertEquals("₹1,50,00,000", CurrencyFormatter.hero(1_50_00_000.0, "INR"))
    }

    @Test
    fun `hero USD uses Western thousand-comma grouping`() {
        // Non-Indian currencies must use the caller-supplied locale's
        // grouping — NOT bleed Indian grouping into USD output.
        assertEquals("$241,663", CurrencyFormatter.hero(241_663.0, "USD", Locale.US))
    }

    @Test
    fun `hero EUR uses Western thousand-comma grouping with US locale`() {
        assertEquals("€241,663", CurrencyFormatter.hero(241_663.0, "EUR", Locale.US))
    }

    @Test
    fun `hero zero`() {
        assertEquals("₹0", CurrencyFormatter.hero(0.0, "INR"))
    }

    @Test
    fun `hero negative uses en-dash not ASCII hyphen`() {
        // Audit requirement: negatives prefix U+2212 MINUS SIGN, never the
        // ASCII hyphen-minus. Hyphen renders too short and is ambiguous
        // with punctuation.
        val result = CurrencyFormatter.hero(-15_000.0, "INR")
        assertEquals("−₹15,000", result)
        assertFalse(
            "Negative output must use U+2212 (en-dash), not ASCII hyphen.",
            result.contains("-"),
        )
    }

    @Test
    fun `hero small number no grouping needed`() {
        assertEquals("₹999", CurrencyFormatter.hero(999.0, "INR"))
    }

    @Test
    fun `hero handles NaN gracefully without crashing`() {
        // Defensive: shouldn't happen, but a stray NaN from a division
        // upstream shouldn't crash the UI.
        assertEquals("₹0", CurrencyFormatter.hero(Double.NaN, "INR"))
    }

    @Test
    fun `hero handles infinity gracefully`() {
        assertEquals("₹0", CurrencyFormatter.hero(Double.POSITIVE_INFINITY, "INR"))
    }

    @Test
    fun `detail aliases hero`() {
        // [detail] is a documentation alias — semantically equivalent output.
        assertEquals(
            CurrencyFormatter.hero(241_663.0, "INR"),
            CurrencyFormatter.detail(241_663.0, "INR"),
        )
    }

    @Test
    fun `unknown currency code falls back to rupee symbol`() {
        // [CurrencyUtils.symbolFor] defaults to ₹ for unknown codes; this
        // test pins that the formatter inherits that fallback rather than
        // crashing or rendering "null₹241,663".
        assertEquals("₹241,663", CurrencyFormatter.hero(241_663.0, "XYZ", Locale.US))
    }

    @Test
    fun `hero NPR uses Indian grouping with Rs symbol`() {
        // NPR is in the INDIAN_GROUPING_CURRENCIES set per the audit;
        // symbol is "Rs" per CurrencyUtils.supported.
        assertEquals("Rs2,41,663", CurrencyFormatter.hero(241_663.0, "NPR"))
    }

    // ── ListRow — always abbreviates ──────────────────────────────────────

    @Test
    fun `listRow INR shows full under 1000`() {
        assertEquals("₹999", CurrencyFormatter.listRow(999.0, "INR"))
    }

    @Test
    fun `listRow INR uses K for thousands under 1 lakh`() {
        assertEquals("₹15K", CurrencyFormatter.listRow(15_000.0, "INR"))
        assertEquals("₹1.5K", CurrencyFormatter.listRow(1_500.0, "INR"))
    }

    @Test
    fun `listRow INR uses L for lakh-range`() {
        assertEquals("₹2.4L", CurrencyFormatter.listRow(2_41_663.0, "INR"))
        assertEquals("₹1L", CurrencyFormatter.listRow(1_00_000.0, "INR"))
    }

    @Test
    fun `listRow INR uses Cr for crore-range`() {
        assertEquals("₹1.5Cr", CurrencyFormatter.listRow(1_50_00_000.0, "INR"))
    }

    @Test
    fun `listRow USD uses K M B for non-Indian currencies`() {
        assertEquals("$15K", CurrencyFormatter.listRow(15_000.0, "USD", Locale.US))
        assertEquals("$2.4M", CurrencyFormatter.listRow(2_400_000.0, "USD", Locale.US))
        assertEquals("$1.5B", CurrencyFormatter.listRow(1_500_000_000.0, "USD", Locale.US))
    }

    @Test
    fun `listRow negative uses en-dash prefix on abbreviation`() {
        assertEquals("−₹15K", CurrencyFormatter.listRow(-15_000.0, "INR"))
    }

    @Test
    fun `listRow boundary - exactly 1000 shows K`() {
        // Boundary check: 1000 sits on the K threshold inclusive.
        assertEquals("₹1K", CurrencyFormatter.listRow(1_000.0, "INR"))
    }

    @Test
    fun `listRow boundary - exactly 1 lakh shows L`() {
        assertEquals("₹1L", CurrencyFormatter.listRow(1_00_000.0, "INR"))
    }

    @Test
    fun `listRow boundary - exactly 1 crore shows Cr`() {
        assertEquals("₹1Cr", CurrencyFormatter.listRow(1_00_00_000.0, "INR"))
    }

    // ── ChartHero — Hero unless ≥10 chars then abbreviate ─────────────────

    @Test
    fun `chartHero stays full when short`() {
        assertEquals("₹241", CurrencyFormatter.chartHero(241.0, "INR"))
    }

    @Test
    fun `chartHero stays full for 9-char output`() {
        // "₹2,41,663" is exactly 9 chars — under the threshold, stays full.
        val result = CurrencyFormatter.chartHero(2_41_663.0, "INR")
        assertEquals("₹2,41,663", result)
        assertEquals(9, result.length)
    }

    @Test
    fun `chartHero abbreviates when 10 or more chars`() {
        // "₹15,00,000" is 10 chars — abbreviates to ₹15L.
        assertEquals("₹15L", CurrencyFormatter.chartHero(15_00_000.0, "INR"))
    }

    // ── Input — raw integer for text-field state ──────────────────────────

    @Test
    fun `input zero returns zero string`() {
        // Text fields need a stable string representation for state;
        // empty would clear the field which is the wrong default for a
        // freshly-opened "amount" field.
        assertEquals("0", CurrencyFormatter.input(0.0))
    }

    @Test
    fun `input integer amount`() {
        assertEquals("15000", CurrencyFormatter.input(15_000.0))
    }

    @Test
    fun `input truncates fractional part`() {
        // .toLong() floors toward zero; users editing an integer-amount
        // field don't expect to see the decimal residue from a prior calc.
        assertEquals("15000", CurrencyFormatter.input(15_000.7))
    }

    @Test
    fun `input has no commas and no currency symbol`() {
        val result = CurrencyFormatter.input(241_663.0)
        assertFalse("Input must have no comma (would break IME during typing).", result.contains(","))
        assertFalse("Input must have no currency symbol (caller renders it as field prefix).", result.contains("₹"))
        assertFalse("Input must have no currency symbol.", result.contains("$"))
    }

    @Test
    fun `input handles NaN gracefully`() {
        assertEquals("0", CurrencyFormatter.input(Double.NaN))
    }

    // ── Negative — explicit en-dash prefix on absolute value ──────────────

    @Test
    fun `negative always prefixes en-dash on absolute value regardless of input sign`() {
        // Both positive and negative inputs produce a negative-prefixed
        // output — caller has already decided "this is a loss / expense"
        // semantically; the value's sign is implementation detail.
        assertEquals("−₹15,000", CurrencyFormatter.negative(15_000.0, "INR"))
        assertEquals("−₹15,000", CurrencyFormatter.negative(-15_000.0, "INR"))
    }

    @Test
    fun `negative output never contains ASCII hyphen`() {
        val result = CurrencyFormatter.negative(15_000.0, "INR")
        assertFalse("Audit-mandated en-dash; ASCII hyphen forbidden.", result.contains("-"))
    }

    @Test
    fun `negative is idempotent on multiple calls (does not double-prefix)`() {
        // Defensive: if a caller accidentally wraps an already-negative-
        // formatted string and feeds it back, we shouldn't end up with
        // "−−₹15,000". The current implementation can't accept strings as
        // input — only doubles — so this is structurally impossible, but
        // the test makes the contract explicit.
        val first = CurrencyFormatter.negative(15_000.0, "INR")
        // Sanity: exactly one prefix.
        assertEquals(1, first.split(CurrencyFormatter.NEGATIVE_PREFIX).size - 1)
    }

    // ── NEGATIVE_PREFIX constant lockdown ─────────────────────────────────

    @Test
    fun `NEGATIVE_PREFIX is exactly U+2212 MINUS SIGN`() {
        // Lockdown test — anyone tempted to "simplify" to ASCII "-"
        // gets a failing test telling them why we use U+2212.
        assertEquals("−", CurrencyFormatter.NEGATIVE_PREFIX)
        assertEquals(1, CurrencyFormatter.NEGATIVE_PREFIX.length)
        assertTrue(
            "NEGATIVE_PREFIX must NOT be ASCII hyphen-minus.",
            CurrencyFormatter.NEGATIVE_PREFIX != "-",
        )
    }
}
