package app.fynlo.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * C11 (3.2.40) — data-integrity guards for the DateUtils.format style API.
 * Anchored to a fixed `today` date so the relative-style tests stay
 * deterministic.
 */
class DateUtilsDataIntegrityTest {

    private val today = LocalDate.of(2026, 5, 27)

    // ── Style.Compact — user pref pattern ────────────────────────────────

    @Test fun `compact respects default dd-MM-yyyy pattern`() {
        assertEquals("25-05-2026",
            DateUtils.format("2026-05-25", DateUtils.Style.Compact, today = today))
    }

    @Test fun `compact respects MM-dd-yyyy when caller passes it`() {
        assertEquals("05-25-2026",
            DateUtils.format("2026-05-25", DateUtils.Style.Compact,
                compactPattern = "MM-dd-yyyy", today = today))
    }

    @Test fun `compact respects yyyy-MM-dd when caller passes it`() {
        assertEquals("2026-05-25",
            DateUtils.format("2026-05-25", DateUtils.Style.Compact,
                compactPattern = "yyyy-MM-dd", today = today))
    }

    // ── Style.Definitive — locale-agnostic month name ─────────────────────

    @Test fun `definitive formats with three-letter month`() {
        assertEquals("25 May 2026",
            DateUtils.format("2026-05-25", DateUtils.Style.Definitive,
                today = today, locale = Locale.ENGLISH))
        assertEquals("3 Jan 2026",
            DateUtils.format("2026-01-03", DateUtils.Style.Definitive,
                today = today, locale = Locale.ENGLISH))
    }

    // ── Style.ChartAxis — "MMM d" for x-axis labels ───────────────────────

    @Test fun `chart axis formats as month-and-day`() {
        assertEquals("May 25",
            DateUtils.format("2026-05-25", DateUtils.Style.ChartAxis,
                today = today, locale = Locale.ENGLISH))
        assertEquals("Dec 1",
            DateUtils.format("2025-12-01", DateUtils.Style.ChartAxis,
                today = today, locale = Locale.ENGLISH))
    }

    // ── Style.Relative — "today" / "yesterday" / "N days ago" / ...  ──────

    @Test fun `relative says today for today`() {
        assertEquals("today",
            DateUtils.format("2026-05-27", DateUtils.Style.Relative, today = today))
    }

    @Test fun `relative says yesterday and tomorrow`() {
        assertEquals("yesterday",
            DateUtils.format("2026-05-26", DateUtils.Style.Relative, today = today))
        assertEquals("tomorrow",
            DateUtils.format("2026-05-28", DateUtils.Style.Relative, today = today))
    }

    @Test fun `relative uses N days for 2 to 6 days out`() {
        assertEquals("in 3 days",
            DateUtils.format("2026-05-30", DateUtils.Style.Relative, today = today))
        assertEquals("5 days ago",
            DateUtils.format("2026-05-22", DateUtils.Style.Relative, today = today))
    }

    @Test fun `relative singular day at exactly 2 days ago vs ago`() {
        // "in 2 days" — plural (default singular is 1 only).
        assertEquals("in 2 days",
            DateUtils.format("2026-05-29", DateUtils.Style.Relative, today = today))
        assertEquals("2 days ago",
            DateUtils.format("2026-05-25", DateUtils.Style.Relative, today = today))
    }

    @Test fun `relative uses N weeks for 7 to 56 days out`() {
        assertEquals("in 1 week",
            DateUtils.format("2026-06-03", DateUtils.Style.Relative, today = today))
        assertEquals("in 4 weeks",
            DateUtils.format("2026-06-24", DateUtils.Style.Relative, today = today))
        assertEquals("1 week ago",
            DateUtils.format("2026-05-20", DateUtils.Style.Relative, today = today))
    }

    @Test fun `relative falls back to compact for far dates`() {
        // 60 days out — beyond the 8-week threshold.
        assertEquals("26-07-2026",
            DateUtils.format("2026-07-26", DateUtils.Style.Relative, today = today))
    }

    // ── Malformed input — defensive fallback ──────────────────────────────

    @Test fun `malformed input returns verbatim`() {
        assertEquals("not-a-date",
            DateUtils.format("not-a-date", DateUtils.Style.Compact, today = today))
        assertEquals("",
            DateUtils.format("", DateUtils.Style.Compact, today = today))
    }

    @Test fun `malformed compact pattern falls back to default`() {
        // Pattern with unmatched bracket triggers IllegalArgumentException
        // in DateTimeFormatter; safeFormatter falls back to default.
        assertEquals("25-05-2026",
            DateUtils.format("2026-05-25", DateUtils.Style.Compact,
                compactPattern = "[bad", today = today))
    }

    // ── Back-compat: formatToDisplay ──────────────────────────────────────

    @Test fun `formatToDisplay back-compat uses default compact pattern`() {
        assertEquals("25-05-2026", DateUtils.formatToDisplay("2026-05-25"))
    }

    @Test fun `formatToDisplay malformed input returns verbatim`() {
        assertEquals("not-a-date", DateUtils.formatToDisplay("not-a-date"))
    }
}
