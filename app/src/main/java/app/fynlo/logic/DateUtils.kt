package app.fynlo.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * C11 (3.2.40) — formal `DateUtils.format(date, style)` API per UX_AUDIT §C11.
 *
 * Pre-C11 the codebase had `formatToDisplay(dateStr)` returning hardcoded
 * `dd-MM-yyyy` — that worked for Indian users but ignored the Settings
 * "Date Format" dropdown (which stored a choice nothing read). And PDF /
 * XLSX exports rendered raw ISO dates (`2026-05-25`) regardless of user
 * preference.
 *
 * Now: one entry point [format] taking a [Style] enum:
 *   - [Style.Relative]   "2 days ago" / "in 3 days" / "today" / "yesterday"
 *   - [Style.Compact]    user's chosen pattern from Settings (dd-MM-yyyy /
 *                         MM-dd-yyyy / yyyy-MM-dd)
 *   - [Style.Definitive] "25 May 2026" — locale-agnostic month name; used
 *                         on legal-style surfaces (receipts, statements)
 *                         where ambiguity is unacceptable
 *   - [Style.ChartAxis]  "May 26" — for chart x-axis labels
 *
 * For back-compat the original `formatToDisplay` + `parseInput` stay; they
 * just delegate to the new API. Every new date-display site should use
 * [format] directly so the style intent is explicit.
 */
object DateUtils {

    /** Date-rendering style per DESIGN_SYSTEM §8.3. */
    enum class Style { Relative, Compact, Definitive, ChartAxis }

    private val dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    /** Default Compact pattern when the caller doesn't have the user pref. */
    const val DEFAULT_COMPACT_PATTERN = "dd-MM-yyyy"

    /**
     * Format an ISO `yyyy-MM-dd` date string per the requested [style].
     * Returns the input verbatim if it can't be parsed (defensive: don't
     * crash a UI render over a malformed legacy row).
     */
    fun format(
        dateStr: String,
        style: Style,
        compactPattern: String = DEFAULT_COMPACT_PATTERN,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.ENGLISH,
    ): String {
        val date = runCatching { LocalDate.parse(dateStr, dbFormatter) }.getOrNull()
            ?: return dateStr
        return format(date, style, compactPattern, today, locale)
    }

    /** [LocalDate] variant — same contract. */
    fun format(
        date: LocalDate,
        style: Style,
        compactPattern: String = DEFAULT_COMPACT_PATTERN,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.ENGLISH,
    ): String = when (style) {
        Style.Relative   -> relative(date, today)
        Style.Compact    -> date.format(safeFormatter(compactPattern))
        Style.Definitive -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        Style.ChartAxis  -> date.format(DateTimeFormatter.ofPattern("MMM d", locale))
    }

    /**
     * Human-relative phrasing: "today" / "yesterday" / "tomorrow" /
     * "N days ago" / "in N days" / "N weeks ago" / "in N weeks" / etc.
     * Caps at weeks; older than 8 weeks falls back to the Compact format.
     */
    private fun relative(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L  -> "today"
            days == 1L  -> "tomorrow"
            days == -1L -> "yesterday"
            days in 2..6    -> "in ${pluralize(days, "day")}"
            days in -6..-2  -> "${pluralize(-days, "day")} ago"
            days in 7..56   -> "in ${pluralize(days / 7, "week")}"
            days in -56..-7 -> "${pluralize(-days / 7, "week")} ago"
            else            -> date.format(safeFormatter(DEFAULT_COMPACT_PATTERN))
        }
    }

    /**
     * Build a formatter from a user-supplied pattern. If the pattern is
     * malformed (shouldn't happen since Settings restricts to 3 known
     * options, but defensive), fall back to the default. Cached statically
     * to avoid rebuilding on every call.
     */
    private val formatterCache = mutableMapOf<String, DateTimeFormatter>()
    private fun safeFormatter(pattern: String): DateTimeFormatter =
        formatterCache.getOrPut(pattern) {
            runCatching { DateTimeFormatter.ofPattern(pattern) }.getOrElse {
                DateTimeFormatter.ofPattern(DEFAULT_COMPACT_PATTERN)
            }
        }

    /**
     * Back-compat: previous public API. Defaults to Compact with the
     * default pattern; existing in-app call sites continue to work
     * unchanged. New code should use [format] directly.
     */
    fun formatToDisplay(dateStr: String): String = format(dateStr, Style.Compact)

    /**
     * Back-compat: parse a user-typed date in any of the common patterns
     * and return the ISO `yyyy-MM-dd` form. Returns today's ISO if none
     * of the patterns match.
     */
    fun parseInput(input: String): String {
        val patterns = listOf("dd-MM-yyyy", "dd-M-yy", "d-M-yy", "dd/MM/yyyy", "yyyy-MM-dd")
        for (pattern in patterns) {
            try {
                val date = LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern))
                return date.format(dbFormatter)
            } catch (_: Exception) { continue }
        }
        return LocalDate.now().format(dbFormatter)
    }
}
