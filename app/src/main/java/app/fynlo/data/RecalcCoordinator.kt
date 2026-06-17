package app.fynlo.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C02 — auto-recalc plumbing (UX_AUDIT §C02).
 *
 * Wraps `FinanceRepository.recalculateAllBalances()` with a single concern:
 * **every recalc updates `UserPreferences.lastRecalcAt`**, so the UI and the
 * launch-time debouncer can reason about freshness from one source of truth.
 *
 * Three entry points:
 *
 *  - [runAndStamp] — unconditional. Used by `FynloApplication`'s post-init
 *    coroutine after the daily-debounce check, and by `FinanceViewModel`
 *    before every export (per `RELEASE_PROTOCOL §0` + `UX_AUDIT §C02`'s
 *    "exports must invoke recalc as a pre-step").
 *  - [runIfStaleOnLaunch] — convenience for `FynloApplication.onCreate`.
 *    Reads `lastRecalcAt`, runs only if [shouldRecalcOnLaunch] says yes.
 *  - [shouldRecalcOnLaunch] (companion) — the pure predicate behind the
 *    daily debounce. Public for unit-testing without a Context.
 *
 * Why a separate class and not a method on `FinanceRepository`:
 * `FinanceRepository` is `Context`-free by construction (DAO + DB + remote
 * + sync, nothing Android-specific). Stamping `lastRecalcAt` requires a
 * `Context` for the DataStore. Keeping that coupling out of the repository
 * preserves its testability and KMP-portability hedge.
 */
@Singleton
class RecalcCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: FinanceRepository,
) {

    /**
     * Runs `recalculateAllBalances()` and stamps `lastRecalcAt` with the
     * current epoch millis on success. **Returns the stamped time** so
     * callers (e.g., export-renderers that need the timestamp in their
     * header) don't have to re-read the DataStore right after writing it.
     *
     * Idempotent — safe to call from multiple call sites (launch coroutine
     * + each export) without coordination, because the underlying
     * `recalculateAllBalances()` is itself idempotent post-C01 (see
     * `decisions/2026-05-26-c01-fix-strategy.md`).
     */
    suspend fun runAndStamp(): Long {
        repository.recalculateAllBalances()
        val now = System.currentTimeMillis()
        UserPreferences.setLastRecalcAt(context, now)
        return now
    }

    /**
     * Launch-time debounced auto-recalc. Reads the persisted
     * `lastRecalcAt` and runs `runAndStamp()` only if [shouldRecalcOnLaunch]
     * is true. Intended to be fire-and-forget from `FynloApplication`'s
     * post-init coroutine; exceptions are swallowed (logged at the caller)
     * to avoid taking down the app's startup path.
     */
    suspend fun runIfStaleOnLaunch(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val last = UserPreferences.lastRecalcAt(context).first()
        if (shouldRecalcOnLaunch(last, now, zone)) {
            runAndStamp()
        }
    }

    companion object {
        /**
         * Daily-debounce predicate: returns `true` iff `lastRecalcAt` falls
         * strictly before the start of the calendar day containing `now`
         * (in the given [zone]). Pure function — no Context, no I/O —
         * exposed on the companion so unit tests can exercise the edge
         * cases (zero / yesterday-late / today-early / today-twice)
         * without Robolectric.
         */
        fun shouldRecalcOnLaunch(lastRecalcAt: Long, now: Long, zone: ZoneId): Boolean {
            val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val todayStartMillis = nowDate.atStartOfDay(zone).toInstant().toEpochMilli()
            return lastRecalcAt < todayStartMillis
        }
    }
}
