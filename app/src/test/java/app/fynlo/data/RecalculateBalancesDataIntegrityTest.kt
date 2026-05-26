package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C01 — Recalculate Balances data integrity
 * (UX_AUDIT_2026-05-25.md §C01,
 *  decisions/2026-05-26-c01-fix-strategy.md).
 *
 * Was the regression test for the runtime destruction bug; is now the
 * standing data-integrity gate that proves `recalculateAllBalances()` is
 * a pure derive-from-truth pass. The destructive DAO queries
 * (`recalculateBorrowerPaid` / `recalculateDebtPaid` — UPDATE borrowers
 * SET paid = paidPrincipal + paidInterest) have been removed from the
 * orchestrator (`FinanceRepository.recalculateAllBalances()`); the test
 * exercises the EXACT new sequence and asserts `paid` survives every
 * variant of the legacy → current → empty data shape.
 *
 * Matches both `*Recalculate*` and `*DataIntegrity*` Gradle test filters
 * (INF04). Re-enabled in `.github/workflows/checks.yml` as part of the
 * Sprint 1 PR that landed this fix.
 *
 * ── HOW THIS TEST WORKS ──────────────────────────────────────────────────
 * Spins up an in-memory `FynloDatabase` via Robolectric and exercises the
 * exact DAO sequence from `FinanceRepository.recalculateAllBalances()`.
 * The in-memory builder skips migrations (it creates the schema at the
 * latest version), so the v15→v16 backfill is simulated by manually
 * inserting the synthetic Payment row the migration would have written.
 *
 * `@Config(application = android.app.Application::class)` overrides the
 * manifest's `@HiltAndroidApp` class so Firebase initialisation doesn't
 * run during the unit test (no google-services credentials in the JVM).
 *
 * `@Config(sdk = [34])` pins Robolectric to a framework level it has
 * bundled jars for. `compileSdk = 36` is fine at compile time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecalculateBalancesDataIntegrityTest {

    private lateinit var db: FynloDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FynloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Exact DAO sequence from `FinanceRepository.recalculateAllBalances()`. */
    private suspend fun recalculateAllBalances() {
        db.dao().backfillBorrowerSourceAccount()
        db.dao().rebuildBorrowerPaidFromPayments()
        db.dao().rebuildDebtPaidFromDebtPayments()
    }

    /**
     * The original C01 fixture: a borrower with `paid = ₹50,000`,
     * `paidPrincipal = 0`, `paidInterest = 0`, and (after the v15→v16
     * backfill migration) one synthetic Payment row pinning that history
     * into the source-of-truth table. Recalc must derive `paid = 50000`
     * back into the borrower row.
     */
    @Test
    fun `recalculateAllBalances preserves paid for legacy borrower after v16 backfill (C01)`() = runBlocking {
        val legacy = Borrower(
            id = "c01-fixture-legacy-borrower",
            name = "C01 Test Borrower",
            amount = 100_000.0,
            rate = 12.0,
            date = "2024-01-01",
            paid = 50_000.0,
            paidPrincipal = 0.0,
            paidInterest = 0.0,
        )
        db.dao().insertBorrower(legacy)

        // Simulate the v15 → v16 backfill migration (FynloDatabase.kt
        // MIGRATION_15_16): one synthetic Payment row per legacy borrower,
        // amount = previous cumulative `paid`, principal = amount,
        // interest = 0, dated borrowers.date, type = "Legacy backfill".
        db.dao().insertPayment(
            Payment(
                id = "legacy-bf-${legacy.id}",
                loanId = legacy.id,
                name = legacy.name,
                date = legacy.date,
                type = "Legacy backfill",
                amount = legacy.paid,
                principal = legacy.paid,
                interest = 0.0,
                notes = "Imported from legacy schema; actual repayment date unknown",
            )
        )

        recalculateAllBalances()

        val after = db.dao().getBorrowerById(legacy.id)
            ?: error("borrower row vanished after recalculate")

        assertEquals(
            "C01 invariant: paid must equal SUM(payments) after recalc " +
                "(legacy borrower with backfilled Payment).",
            50_000.0,
            after.paid,
            0.0,
        )
        // The migration also seeds paidPrincipal = paid; the rebuild then
        // sums principal from payments (= paid for the backfill row).
        assertEquals(50_000.0, after.paidPrincipal, 0.0)
        assertEquals(0.0, after.paidInterest, 0.0)
    }

    /**
     * Current-schema borrower with the principal/interest split kept in
     * sync at write time (i.e., a `Payment` row was inserted by
     * `insertPaymentWithDest` when the user collected the repayment).
     * Recalc must reproduce the same values from the payments table.
     */
    @Test
    fun `recalculateAllBalances is consistent for current-schema borrower with split Payment row`() = runBlocking {
        val current = Borrower(
            id = "c01-fixture-current-borrower",
            name = "C01 Test Borrower (current schema)",
            amount = 100_000.0,
            rate = 12.0,
            date = "2024-01-01",
            paid = 50_000.0,
            paidPrincipal = 30_000.0,
            paidInterest = 20_000.0,
        )
        db.dao().insertBorrower(current)
        db.dao().insertPayment(
            Payment(
                id = "p-current-1",
                loanId = current.id,
                name = current.name,
                date = "2024-06-01",
                type = "Both",
                amount = 50_000.0,
                principal = 30_000.0,
                interest = 20_000.0,
            )
        )

        recalculateAllBalances()

        val after = db.dao().getBorrowerById(current.id)
            ?: error("borrower row vanished after recalculate")

        assertEquals(50_000.0, after.paid, 0.0)
        assertEquals(30_000.0, after.paidPrincipal, 0.0)
        assertEquals(20_000.0, after.paidInterest, 0.0)
    }

    /**
     * Brand-new borrower with no payments at all. This is the case the
     * old `WHERE EXISTS (payments)` gate was protecting against; after
     * the C01 rewrite the gate is gone and the rebuild query uses
     * `COALESCE(SUM(...), 0)` so `paid` lands at 0 (not NULL) for a row
     * with zero repayments. Documents that the COALESCE handles the
     * empty case correctly.
     */
    @Test
    fun `recalculateAllBalances leaves paid=0 for new borrower with no payments`() = runBlocking {
        val fresh = Borrower(
            id = "c01-fixture-new-borrower",
            name = "C01 Test Borrower (no repayments yet)",
            amount = 100_000.0,
            rate = 12.0,
            date = "2024-01-01",
            paid = 0.0,
            paidPrincipal = 0.0,
            paidInterest = 0.0,
        )
        db.dao().insertBorrower(fresh)

        recalculateAllBalances()

        val after = db.dao().getBorrowerById(fresh.id)
            ?: error("borrower row vanished after recalculate")

        assertEquals(0.0, after.paid, 0.0)
        assertEquals(0.0, after.paidPrincipal, 0.0)
        assertEquals(0.0, after.paidInterest, 0.0)
    }
}
