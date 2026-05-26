package app.fynlo.logic

import java.text.NumberFormat
import java.util.Locale

/**
 * C08 — unified currency formatter (UX_AUDIT §C08).
 *
 * Pre-3.2.13 the codebase had ~257 ad-hoc number-formatting call sites
 * scattered across ~119 files: `String.format("%,.0f")`, raw `₹` prefixes,
 * `.toInt().toString()` truncations, and three different K/L abbreviation
 * implementations. The result was inconsistent output (same data shown as
 * `₹15,000.00` on one screen and `₹15,000` on another), broken
 * multi-currency support (hardcoded `₹` ignored the user's currency pref),
 * and silent data loss (toInt() drops decimals on display).
 *
 * This object is the single source of truth for currency display. Every
 * screen, dialog, exporter, and widget should route through one of the
 * six style functions below. Stage 2+ of C08 migrates the call sites; this
 * file lands first so the migration has a stable target.
 *
 * Six display styles per the audit (DESIGN_SYSTEM.md §8.1):
 *
 *   • [hero]        — full amount, no decimals, locale-correct grouping.
 *                     INR/NPR/LKR/BDT use lakh-crore grouping (₹2,41,663).
 *                     Others use Western thousand-comma (£241,663 / $241,663).
 *                     Use for: dashboard cards, big net-worth numbers.
 *
 *   • [chartHero]   — same as Hero unless the rendered string is ≥10 chars,
 *                     in which case it abbreviates via [listRow]. Saves
 *                     horizontal space on chart axes / hero tiles where
 *                     full-precision values would clip or wrap.
 *
 *   • [listRow]     — always abbreviates. K/L/Cr for INR-family currencies,
 *                     K/M/B for others. Use for: dense list rows where a
 *                     consistent compact form beats per-item full precision.
 *
 *   • [detail]      — alias for Hero. Kept as a separate name so call sites
 *                     can document their intent ("this is a detail-view
 *                     amount, not a hero").
 *
 *   • [input]       — raw integer (no symbol, no commas) for text-field
 *                     state during typing. Truncates fractional parts.
 *                     Caller is responsible for displaying the currency
 *                     symbol separately (typically as the field's prefix).
 *
 *   • [negative]    — Hero with the audit-mandated en-dash prefix
 *                     (U+2212, NOT ASCII hyphen U+002D). Callers usually
 *                     combine this with a `SemanticRed` text colour for
 *                     the "loss / expense / out-of-budget" semantic.
 *
 * Symbol lookup goes through [CurrencyUtils.symbolFor] so the canonical
 * single source for currency-code → symbol stays there.
 *
 * Thread-safety: each call constructs its own [NumberFormat] (Java
 * convention — `NumberFormat` is not thread-safe). The allocation is
 * cheap on modern JVMs; no need for ThreadLocal pooling.
 *
 * Pure functions — no Context, no I/O. Unit-testable from JVM.
 * Matches the `*DataIntegrity*` test filter via [CurrencyFormatterDataIntegrityTest].
 */
object CurrencyFormatter {

    /**
     * U+2212 MINUS SIGN — audit-mandated prefix for negative amounts.
     * Distinct from the ASCII hyphen-minus U+002D (which renders too short
     * and is semantically ambiguous with the "dash" punctuation mark).
     */
    const val NEGATIVE_PREFIX = "−"

    /**
     * Currencies that use the South-Asian lakh-crore grouping convention
     * (`1,50,00,000` instead of `15,000,000`). Per the audit, NPR / LKR /
     * BDT all follow the same convention as INR; the symbol differs but
     * the grouping is identical.
     */
    private val INDIAN_GROUPING_CURRENCIES = setOf("INR", "NPR", "LKR", "BDT")


    // ── Hero / Detail ─────────────────────────────────────────────────────

    fun hero(
        amount: Double,
        currencyCode: String = "INR",
        locale: Locale = Locale.getDefault(),
    ): String = formatGrouped(amount, currencyCode, locale, decimals = 0)

    fun detail(
        amount: Double,
        currencyCode: String = "INR",
        locale: Locale = Locale.getDefault(),
    ): String = hero(amount, currencyCode, locale)

    // ── ChartHero ─────────────────────────────────────────────────────────

    fun chartHero(
        amount: Double,
        currencyCode: String = "INR",
        locale: Locale = Locale.getDefault(),
    ): String {
        val full = hero(amount, currencyCode, locale)
        return if (full.length >= 10) listRow(amount, currencyCode, locale) else full
    }

    // ── ListRow (always abbreviates) ──────────────────────────────────────

    fun listRow(
        amount: Double,
        currencyCode: String = "INR",
        locale: Locale = Locale.getDefault(),
    ): String {
        if (!amount.isFinite()) return hero(0.0, currencyCode, locale)
        val sign = if (amount < 0) NEGATIVE_PREFIX else ""
        val abs = kotlin.math.abs(amount)
        val symbol = CurrencyUtils.symbolFor(currencyCode)
        val isIndian = currencyCode.uppercase() in INDIAN_GROUPING_CURRENCIES
        return when {
            isIndian && abs >= 1_00_00_000.0 -> "$sign$symbol${trimZero(abs / 1_00_00_000.0)}Cr"
            isIndian && abs >= 1_00_000.0    -> "$sign$symbol${trimZero(abs / 1_00_000.0)}L"
            isIndian && abs >= 1_000.0       -> "$sign$symbol${trimZero(abs / 1_000.0)}K"
            !isIndian && abs >= 1_000_000_000.0 -> "$sign$symbol${trimZero(abs / 1_000_000_000.0)}B"
            !isIndian && abs >= 1_000_000.0     -> "$sign$symbol${trimZero(abs / 1_000_000.0)}M"
            !isIndian && abs >= 1_000.0         -> "$sign$symbol${trimZero(abs / 1_000.0)}K"
            else -> hero(amount, currencyCode, locale)
        }
    }

    // ── Input (raw integer for text-field state during typing) ────────────

    fun input(amount: Double): String =
        if (amount.isFinite()) amount.toLong().toString() else "0"

    // ── Negative (explicit en-dash prefix on absolute value) ──────────────

    fun negative(
        amount: Double,
        currencyCode: String = "INR",
        locale: Locale = Locale.getDefault(),
    ): String {
        val abs = kotlin.math.abs(amount)
        return NEGATIVE_PREFIX + hero(abs, currencyCode, locale)
            .removePrefix(NEGATIVE_PREFIX)   // defensive: abs() can't go negative but covers Double.MIN_VALUE quirks
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun formatGrouped(
        amount: Double,
        currencyCode: String,
        locale: Locale,
        decimals: Int,
    ): String {
        if (!amount.isFinite()) return formatGrouped(0.0, currencyCode, locale, decimals)
        val symbol = CurrencyUtils.symbolFor(currencyCode)
        val sign = if (amount < 0) NEGATIVE_PREFIX else ""
        val abs = kotlin.math.abs(amount)
        val isIndian = currencyCode.uppercase() in INDIAN_GROUPING_CURRENCIES
        val body = if (isIndian) {
            formatLakhCrore(abs, decimals)
        } else {
            NumberFormat.getInstance(locale).apply {
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
            }.format(abs)
        }
        return "$sign$symbol$body"
    }

    /**
     * Hand-rolled lakh-crore grouping. JVM's `DecimalFormat` secondary-
     * grouping-via-pattern (`#,##,###`) is unreliable across JDK / ICU
     * versions — some runtimes silently degrade to all-3-digit groups,
     * which silently corrupts INR display. Direct string-manipulation
     * doesn't depend on the platform's `DecimalFormat` parser, so it
     * behaves identically on every Android device and every JDK the unit
     * tests run on.
     *
     * Algorithm:
     *   1. Split the absolute integer at the last 3 digits.
     *   2. Group the rest into 2-digit chunks from the right.
     *   3. Join with commas.
     *   4. Append `.<decimals>` if requested.
     *
     * Examples (decimals = 0):
     *   - 999             → `999`
     *   - 1_500           → `1,500`
     *   - 2_41_663        → `2,41,663`
     *   - 1_50_00_000     → `1,50,00,000`
     *   - 1_00_00_00_000  → `1,00,00,00,000`
     */
    private fun formatLakhCrore(absAmount: Double, decimals: Int): String {
        val rounded = kotlin.math.round(absAmount * pow10(decimals)) / pow10(decimals)
        val integerPart = rounded.toLong()
        val intStr = integerPart.toString()
        val grouped = if (intStr.length <= 3) {
            intStr
        } else {
            val last3 = intStr.takeLast(3)
            val rest = intStr.dropLast(3)
            // Chunk from the right: reverse, chunk by 2, join, reverse back.
            val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
            "$restGrouped,$last3"
        }
        if (decimals == 0) return grouped
        val fractional = ((rounded - integerPart) * pow10(decimals))
            .let { kotlin.math.round(it).toLong() }
            .toString()
            .padStart(decimals, '0')
        return "$grouped.$fractional"
    }

    private fun pow10(exponent: Int): Double {
        var result = 1.0
        repeat(exponent) { result *= 10.0 }
        return result
    }

    /**
     * Render [value] as a compact decimal for K/L/Cr/M/B suffixes:
     * `1.0` → `"1"`, `2.4` → `"2.4"`, `15.0` → `"15"`. Rounds to 1 decimal
     * place. Uses `Locale.US` for the decimal separator so the abbreviation
     * is always `.` regardless of device locale (the prefix symbol +
     * abbreviation suffix already telegraph that this is a currency value;
     * a comma decimal separator inside `2,4L` would read as a thousands
     * separator and confuse).
     */
    private fun trimZero(value: Double): String {
        val rounded = kotlin.math.round(value * 10) / 10
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
