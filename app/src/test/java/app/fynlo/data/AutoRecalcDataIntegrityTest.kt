package app.fynlo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * C02 — auto-recalc launch debounce (UX_AUDIT §C02,
 * decisions/2026-05-26-c01-fix-strategy.md cross-reference).
 *
 * Pure-function tests for `RecalcCoordinator.shouldRecalcOnLaunch`. Documents
 * the daily-debounce contract:
 *
 *   *Recalc once per calendar day*. If the last successful recalc was earlier
 *   today (in the user's local zone), the next launch skips. If it was
 *   yesterday or earlier, the next launch runs.
 *
 * Matches both `*Recalculate*` and `*DataIntegrity*` Gradle test filters —
 * the same `checks.yml` data-integrity gate that catches
 * `RecalculateBalancesDataIntegrityTest` picks this up too.
 *
 * Robolectric is intentionally NOT used here. The predicate is a pure
 * function on `RecalcCoordinator.Companion`; coupling it to Android would
 * make the contract harder to read and slower to test. The Context-bound
 * pieces (`runAndStamp` / `runIfStaleOnLaunch`) live in instrumented or
 * Robolectric tests separately.
 */
class AutoRecalcDataIntegrityTest {

    private val zone = ZoneId.of("Asia/Kolkata") // pins TZ so the test is deterministic across CI hosts

    /** Convert (yyyy, M, d, H, m) in the test zone to epoch millis. */
    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `shouldRecalcOnLaunch is TRUE when lastRecalcAt is zero (first ever launch)`() {
        val now = ms(2026, 5, 26, 9, 0)
        assertTrue(
            "A fresh install with no recorded recalc must trigger one on launch.",
            RecalcCoordinator.shouldRecalcOnLaunch(lastRecalcAt = 0L, now = now, zone = zone),
        )
    }

    @Test
    fun `shouldRecalcOnLaunch is TRUE when last recalc was the previous calendar day late at night`() {
        val now  = ms(2026, 5, 26, 0, 5)           // today 00:05
        val last = ms(2026, 5, 25, 23, 59)         // yesterday 23:59 (six minutes ago, but a calendar day apart)
        assertTrue(
            "Calendar-day boundary, not wall-clock duration, is the debounce key.",
            RecalcCoordinator.shouldRecalcOnLaunch(last, now, zone),
        )
    }

    @Test
    fun `shouldRecalcOnLaunch is FALSE when last recalc was earlier today`() {
        val now  = ms(2026, 5, 26, 17, 0)          // today 17:00
        val last = ms(2026, 5, 26, 9, 0)           // today 09:00 — already recalc'd this morning
        assertFalse(
            "Once we recalc today, no further recalc happens until tomorrow.",
            RecalcCoordinator.shouldRecalcOnLaunch(last, now, zone),
        )
    }

    @Test
    fun `shouldRecalcOnLaunch is FALSE at the exact moment we record the recalc`() {
        val now = ms(2026, 5, 26, 9, 0)
        assertFalse(
            "Edge case: lastRecalcAt equal to now — there's no calendar day before today, no recalc needed.",
            RecalcCoordinator.shouldRecalcOnLaunch(now, now, zone),
        )
    }

    @Test
    fun `shouldRecalcOnLaunch is TRUE when a week has passed since last recalc`() {
        val now  = ms(2026, 5, 26, 12, 0)
        val last = ms(2026, 5, 19, 12, 0)
        assertTrue(
            "A long gap (vacation, app uninstall/reinstall) must trigger recalc.",
            RecalcCoordinator.shouldRecalcOnLaunch(last, now, zone),
        )
    }

    @Test
    fun `shouldRecalcOnLaunch flips at local midnight, not at UTC midnight`() {
        // Asia/Kolkata is UTC+5:30. Today's 00:00 IST is yesterday's 18:30 UTC.
        // A recalc stamp from "yesterday 23:00 IST" is `2026-05-25T17:30:00Z` —
        // strictly before today's IST midnight (`2026-05-25T18:30:00Z`), so a
        // launch shortly after midnight IST must trigger recalc, even though
        // less than a UTC day has elapsed.
        val now  = ms(2026, 5, 26, 0, 30)          // today 00:30 IST
        val last = ms(2026, 5, 25, 23, 0)          // yesterday 23:00 IST
        assertTrue(
            "Debounce key is the local-zone calendar day, not a 24h sliding window.",
            RecalcCoordinator.shouldRecalcOnLaunch(last, now, zone),
        )
    }

    @Test
    fun `shouldRecalcOnLaunch is FALSE for two recalcs at opposite ends of the same day`() {
        val now  = ms(2026, 5, 26, 23, 59)
        val last = ms(2026, 5, 26, 0, 1)
        assertFalse(
            "Once we recalc just after midnight, no further recalc happens that calendar day.",
            RecalcCoordinator.shouldRecalcOnLaunch(last, now, zone),
        )
    }

    @Test
    fun `the threshold value is the local-zone start-of-day epoch millis`() {
        // Reproduces the predicate's arithmetic from the outside, so a future
        // refactor that breaks the contract (e.g., switches to a 24h sliding
        // window) is caught here in addition to the cases above.
        val now = ms(2026, 5, 26, 14, 0)
        val todayStartMillis = LocalDate.of(2026, 5, 26).atStartOfDay(zone).toInstant().toEpochMilli()

        assertFalse(
            "lastRecalcAt == todayStart should NOT trigger (already recalc'd today, at midnight).",
            RecalcCoordinator.shouldRecalcOnLaunch(todayStartMillis, now, zone),
        )
        assertTrue(
            "lastRecalcAt == todayStart − 1 ms should trigger (last recalc was the final ms of yesterday).",
            RecalcCoordinator.shouldRecalcOnLaunch(todayStartMillis - 1, now, zone),
        )
    }
}
