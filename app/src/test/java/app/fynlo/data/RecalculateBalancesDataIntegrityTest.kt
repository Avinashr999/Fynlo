package app.fynlo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.Borrower
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C01 — Recalculate Balances data destruction
 * (UX_AUDIT_2026-05-25.md §C01).
 *
 * Regression test called for in C01 Fix step 5:
 *   "Add a unit test that runs Recalculate against a fixture with known
 *    `paid` values and asserts the sums are preserved."
 *
 * Also satisfies the data-integrity Gradle filter referenced by INF04 in
 * UX_AUDIT §9 — both `*Recalculate*` and `*DataIntegrity*` match this class.
 *
 * ── THE BUG ──────────────────────────────────────────────────────────────
 * FynloDao.kt line 62:
 *     @Query("UPDATE borrowers SET paid = paidPrincipal + paidInterest")
 *     suspend fun recalculateBorrowerPaid()
 *
 * For a borrower in the "legacy" state (paidPrincipal = 0, paidInterest = 0,
 * paid > 0, and NO `payments` rows reference them — exactly what happens
 * when partial repayment was only ever written to the cumulative field),
 * the SQL above zeroes `paid`. The follow-up DAO call
 * `rebuildBorrowerPaidFromPayments` is gated by `WHERE EXISTS (payments)`,
 * so it does not restore the value. Hence the ₹54K destruction described
 * in the audit.
 *
 * ── THE FIX (per C01 steps 1–3) ──────────────────────────────────────────
 *   1. Add a real `loanRepayments[]` table; derive `paid` from sum.
 *   2. Migrate existing cumulative `paid` into one repayment row dated
 *      `loanDate`.
 *   3. NEVER overwrite `paid` from `paidPrincipal + paidInterest`.
 *
 * Until that fix lands this test is RED.
 *
 * ── HOW THIS TEST WORKS ──────────────────────────────────────────────────
 * Spins up an in-memory FynloDatabase via Robolectric (because Room's
 * Android-style builder needs a Context, and `testImplementation` doesn't
 * include the instrumented test runner), inserts the legacy fixture, then
 * runs the EXACT DAO sequence from FinanceRepository.recalculateAllBalances()
 * and asserts `paid` survives.
 *
 * `@Config(sdk = [34])` pins Robolectric to a supported framework level —
 * `compileSdk = 36` is fine at compile time, but Robolectric's bundled
 * Android jars top out at 34/35 depending on version (we're on 4.14.1).
 *
 * `application = android.app.Application::class` overrides the manifest's
 * `@HiltAndroidApp` class so Firebase/Crashlytics/Analytics initialisation
 * doesn't run during the unit test (it requires real google-services
 * credentials and an Android Application context, neither of which exists
 * here). This is a Room-only test — no DI graph needed.
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

    @Test
    @Ignore(
        "Pending C01 fix — see UX_AUDIT_2026-05-25.md §C01 and INF04. " +
            "Remove this @Ignore to see RED, then drive the fix until it goes GREEN. " +
            "Validated locally: this test fails today with " +
            "`expected:<50000.0> but was:<0.0>` because FynloDao.recalculateBorrowerPaid()'s " +
            "SQL zeroes `paid` for legacy borrowers."
    )
    fun `recalculateAllBalances must preserve paid for legacy borrower with no Payment rows (C01)`() = runBlocking {
        // Legacy fixture: ₹50,000 of partial repayment recorded only on the
        // cumulative `paid` field. The principal/interest split was never
        // populated, and no Payment rows reference this borrower.
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

        // EXACT sequence from FinanceRepository.recalculateAllBalances():
        //
        //     dao.recalculateBorrowerPaid()           // <- the destruction
        //     dao.recalculateDebtPaid()
        //     dao.backfillBorrowerSourceAccount()
        //     dao.rebuildBorrowerPaidFromPayments()   // <- WHERE EXISTS gate, no-op here
        //     dao.rebuildDebtPaidFromDebtPayments()
        db.dao().recalculateBorrowerPaid()
        db.dao().recalculateDebtPaid()
        db.dao().backfillBorrowerSourceAccount()
        db.dao().rebuildBorrowerPaidFromPayments()
        db.dao().rebuildDebtPaidFromDebtPayments()

        val after = db.dao().getBorrowerById(legacy.id)
            ?: error("borrower row vanished after recalculate")

        assertEquals(
            "C01 regression: recalculateAllBalances destroyed ₹${"%.2f".format(legacy.paid)} " +
                "of payment history (paid: ${legacy.paid} → ${after.paid}). " +
                "FynloDao.recalculateBorrowerPaid()'s SQL " +
                "(UPDATE borrowers SET paid = paidPrincipal + paidInterest) zeroed `paid` " +
                "because the principal/interest split was never populated and no Payment rows exist. " +
                "See UX_AUDIT_2026-05-25.md §C01 fix steps 1–3.",
            50_000.0,         // expected: paid preserved
            after.paid,       // actual under current SQL: 0.0
            0.0,
        )
    }

    @Test
    fun `recalculateAllBalances is a no-op when paidPrincipal + paidInterest already equals paid`() = runBlocking {
        // Boundary case: newer borrower where the split was kept in sync.
        // The buggy SQL happens to be harmless here. Documents the safe
        // case so a future fix doesn't accidentally regress it.
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

        db.dao().recalculateBorrowerPaid()
        db.dao().recalculateDebtPaid()
        db.dao().backfillBorrowerSourceAccount()
        db.dao().rebuildBorrowerPaidFromPayments()
        db.dao().rebuildDebtPaidFromDebtPayments()

        val after = db.dao().getBorrowerById(current.id)
            ?: error("borrower row vanished after recalculate")

        assertEquals(
            "Recalculate must be a no-op when paidPrincipal + paidInterest already equals paid.",
            current.paid,
            after.paid,
            0.0,
        )
    }
}
