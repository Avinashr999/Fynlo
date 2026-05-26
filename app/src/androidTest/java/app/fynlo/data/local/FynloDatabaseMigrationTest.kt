package app.fynlo.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented migration tests for `FynloDatabase` — the Sprint 1 release
 * gate for C01 (`RELEASE_PROTOCOL.md §8`, third bullet:
 * *"Schema migration tested: existing users upgrading from 3.2.1 retain
 * `paid` values"*).
 *
 * The `MigrationTestHelper` reads schema JSON files exported by Room KSP
 * (`app/schemas/app.fynlo.data.local.FynloDatabase/<version>.json`) and
 * lets us spin up a database at any prior schema version, insert fixture
 * data using raw SQL, run the migration, and inspect the result against
 * real SQLite — no Robolectric, no mocks.
 *
 * ── HOW TO RUN ───────────────────────────────────────────────────────────
 *   1. Connect an Android device or start an emulator (any API ≥ 26).
 *   2. From the repo root:
 *        ./gradlew :app:connectedProdDebugAndroidTest \
 *          -Pandroid.testInstrumentationRunnerArguments.class=app.fynlo.data.local.FynloDatabaseMigrationTest
 *   3. Or run individual tests from Android Studio (gutter ▶ icon).
 *
 * These tests do NOT run in `:app:testProdDebugUnitTest` (the JVM gate
 * that already runs `RecalculateBalancesDataIntegrityTest`). The classic
 * `MigrationTestHelper` requires the Android instrumentation harness,
 * which Robolectric does not fully simulate for Room 2.8+'s migration
 * pipeline. CI integration via Firebase Test Lab (or a self-hosted
 * runner with an emulator) is a separate follow-up.
 *
 * Until CI runs these, the release-procedure smoke test
 * (`RELEASE_PROTOCOL.md §3.5`) must run them locally before tagging
 * 3.2.2 — one tick on the engineering pre-release checklist in
 * `release_notes/3.2.2.md`.
 */
@RunWith(AndroidJUnit4::class)
class FynloDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "fynlo-migration-test.db"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FynloDatabase::class.java,
    )

    /**
     * C01 backfill (the most important case): a v15 borrower with
     * `paid = 50000`, `paidPrincipal = 0`, `paidInterest = 0`, and NO rows
     * in `payments`. After migration to v16, one synthetic `Payment` row
     * must exist for this borrower with `amount = principal = paid`,
     * `interest = 0`, `type = "Legacy backfill"`, `date = loanDate`, and
     * the matching `notes` text. The borrower's `paidPrincipal` is also
     * seeded equal to `paid` so the invariant `paid == paidPrincipal +
     * paidInterest` holds without waiting for the next recalc.
     */
    @Test
    @Throws(IOException::class)
    fun migrate15to16_backfillsLegacyBorrowerWithPaidIntoPaymentsTable() {
        val borrowerId = "c01-migration-test-legacy"
        val loanDate = "2024-01-01"
        val paid = 50_000.0

        // Step 1 — create database at v15 with a single legacy borrower row.
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO borrowers
                    (id, name, phone, address, guarantor,
                     amount, rate, date, due, tenure, type,
                     paid, paidPrincipal, paidInterest,
                     status, defaultDate, frozenInterest,
                     sourceAccount, notes, projectId,
                     updatedAt, createdAt)
                VALUES
                    (?, 'C01 Migration Test Borrower', '', '', '',
                     100000.0, 12.0, ?, '', 0, 'Simple Interest',
                     ?, 0.0, 0.0,
                     'Active', '', 0.0,
                     '', '', 'personal',
                     0, 0)
                """.trimIndent(),
                arrayOf<Any>(borrowerId, loanDate, paid),
            )
        }

        // Step 2 — run the v15 → v16 migration and validate the resulting
        // schema matches Room's expectation for v16.
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            /* validateDroppedTables = */ true,
            MIGRATION_15_16,
        )

        // Step 3a — exactly one Payment row exists for this borrower, with
        // the backfill shape.
        db.query(
            """
            SELECT amount, principal, interest, type, date, loanId, notes, projectId
            FROM payments
            WHERE loanId = ?
            """.trimIndent(),
            arrayOf(borrowerId),
        ).use { c ->
            assertTrue(
                "Migration must insert a synthetic Payment row for the legacy borrower " +
                    "(see decisions/2026-05-26-c01-fix-strategy.md and FynloDatabase.MIGRATION_15_16).",
                c.moveToFirst(),
            )
            assertEquals("amount", paid, c.getDouble(0), 0.0)
            assertEquals("principal", paid, c.getDouble(1), 0.0)
            assertEquals("interest", 0.0, c.getDouble(2), 0.0)
            assertEquals("type", "Legacy backfill", c.getString(3))
            assertEquals("date", loanDate, c.getString(4))
            assertEquals("loanId", borrowerId, c.getString(5))
            assertEquals(
                "notes",
                "Imported from legacy schema; actual repayment date unknown",
                c.getString(6),
            )
            assertEquals("projectId", "personal", c.getString(7))
            assertFalse(
                "Migration must insert EXACTLY ONE Payment row per legacy borrower, not multiple.",
                c.moveToNext(),
            )
        }

        // Step 3b — the borrower's split fields are now consistent
        // (paid == paidPrincipal + paidInterest).
        db.query(
            """
            SELECT paid, paidPrincipal, paidInterest
            FROM borrowers
            WHERE id = ?
            """.trimIndent(),
            arrayOf(borrowerId),
        ).use { c ->
            assertTrue(c.moveToFirst())
            val rowPaid = c.getDouble(0)
            val rowPrincipal = c.getDouble(1)
            val rowInterest = c.getDouble(2)
            assertEquals(
                "Cumulative `paid` must be preserved verbatim.",
                paid, rowPaid, 0.0,
            )
            assertEquals(
                "Migration must seed paidPrincipal = paid for legacy rows so the " +
                    "invariant paid == paidPrincipal + paidInterest holds.",
                paid, rowPrincipal, 0.0,
            )
            assertEquals(0.0, rowInterest, 0.0)
        }
    }

    /**
     * Boundary case: a borrower with `paid > 0` that ALREADY has matching
     * `payments` rows from earlier writes (the current-schema shape) is
     * **not** touched by the migration — the backfill INSERT uses
     * `WHERE NOT EXISTS (payments WHERE loanId = b.id)`, so duplicate
     * synthetic rows must not appear.
     */
    @Test
    @Throws(IOException::class)
    fun migrate15to16_leavesBorrowerWithExistingPaymentRowsUnchanged() {
        val borrowerId = "c01-migration-test-current"
        val realPaymentId = "real-payment-1"
        val paid = 50_000.0

        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO borrowers
                    (id, name, phone, address, guarantor,
                     amount, rate, date, due, tenure, type,
                     paid, paidPrincipal, paidInterest,
                     status, defaultDate, frozenInterest,
                     sourceAccount, notes, projectId,
                     updatedAt, createdAt)
                VALUES
                    (?, 'Current Schema Borrower', '', '', '',
                     100000.0, 12.0, '2024-01-01', '', 0, 'Simple Interest',
                     ?, 30000.0, 20000.0,
                     'Active', '', 0.0,
                     '', '', 'personal',
                     0, 0)
                """.trimIndent(),
                arrayOf<Any>(borrowerId, paid),
            )
            db.execSQL(
                """
                INSERT INTO payments
                    (id, loanId, name, date, type, amount, principal, interest,
                     mode, notes, projectId, updatedAt, createdAt)
                VALUES
                    (?, ?, 'Current Schema Borrower', '2024-06-01', 'Both',
                     50000.0, 30000.0, 20000.0,
                     '', '', 'personal', 0, 0)
                """.trimIndent(),
                arrayOf<Any>(realPaymentId, borrowerId),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            true,
            MIGRATION_15_16,
        )

        // Exactly the original Payment row is present — no backfill row.
        db.query(
            "SELECT id, type FROM payments WHERE loanId = ?",
            arrayOf(borrowerId),
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(realPaymentId, c.getString(0))
            assertEquals(
                "type must remain 'Both' — the migration must not overwrite real payments.",
                "Both",
                c.getString(1),
            )
            assertFalse(
                "Migration must not insert a 'Legacy backfill' row alongside an existing Payment.",
                c.moveToNext(),
            )
        }
    }

    /**
     * Debts twin of the borrower backfill: a v15 debt with `paid > 0` and
     * no `debt_payments` rows must get one synthetic DebtPayment row, same
     * shape as the borrower case.
     */
    @Test
    @Throws(IOException::class)
    fun migrate15to16_backfillsLegacyDebtWithPaidIntoDebtPaymentsTable() {
        val debtId = "c01-migration-test-debt"
        val loanDate = "2024-02-01"
        val paid = 25_000.0

        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO debts
                    (id, name, phone, type,
                     amount, rate, date, due, tenure, intType,
                     paid, paidPrincipal, paidInterest,
                     status, collateral, notes, projectId,
                     updatedAt, createdAt)
                VALUES
                    (?, 'C01 Migration Test Debt', '', 'Friend / Family',
                     50000.0, 12.0, ?, '', 0, 'Simple Interest',
                     ?, 0.0, 0.0,
                     'Active', '', '', 'personal',
                     0, 0)
                """.trimIndent(),
                arrayOf<Any>(debtId, loanDate, paid),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            true,
            MIGRATION_15_16,
        )

        db.query(
            """
            SELECT amount, principal, interest, type, date, debtId, notes
            FROM debt_payments
            WHERE debtId = ?
            """.trimIndent(),
            arrayOf(debtId),
        ).use { c ->
            assertTrue(
                "Migration must insert a synthetic DebtPayment row for the legacy debt.",
                c.moveToFirst(),
            )
            assertEquals(paid, c.getDouble(0), 0.0)
            assertEquals(paid, c.getDouble(1), 0.0)
            assertEquals(0.0, c.getDouble(2), 0.0)
            assertEquals("Legacy backfill", c.getString(3))
            assertEquals(loanDate, c.getString(4))
            assertEquals(debtId, c.getString(5))
            assertEquals(
                "Imported from legacy schema; actual repayment date unknown",
                c.getString(6),
            )
            assertFalse(c.moveToNext())
        }
    }

    /**
     * Brand-new borrower with `paid = 0` must remain untouched by the
     * migration — no Payment row, no field changes. Documents that the
     * `WHERE b.paid > 0` guard works.
     */
    @Test
    @Throws(IOException::class)
    fun migrate15to16_leavesBorrowerWithZeroPaidUntouched() {
        val borrowerId = "c01-migration-test-fresh"

        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO borrowers
                    (id, name, phone, address, guarantor,
                     amount, rate, date, due, tenure, type,
                     paid, paidPrincipal, paidInterest,
                     status, defaultDate, frozenInterest,
                     sourceAccount, notes, projectId,
                     updatedAt, createdAt)
                VALUES
                    (?, 'Fresh Borrower', '', '', '',
                     100000.0, 12.0, '2024-01-01', '', 0, 'Simple Interest',
                     0.0, 0.0, 0.0,
                     'Active', '', 0.0,
                     '', '', 'personal',
                     0, 0)
                """.trimIndent(),
                arrayOf<Any>(borrowerId),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            true,
            MIGRATION_15_16,
        )

        db.query(
            "SELECT COUNT(*) FROM payments WHERE loanId = ?",
            arrayOf(borrowerId),
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "Migration must NOT insert a backfill row for borrowers with paid = 0.",
                0, c.getInt(0),
            )
        }
    }

    /**
     * Full Room re-open after migration: validates that the migrated
     * database actually opens cleanly with the full `FynloDatabase` schema
     * declarations, all migrations registered. Catches problems where the
     * migration leaves the schema in a state that doesn't match the v16
     * entities (would surface as Room schema-validation crash).
     */
    @Test
    @Throws(IOException::class)
    fun migrate15to16_resultingDatabaseOpensCleanlyWithFullRoom() {
        helper.createDatabase(TEST_DB, 15).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FynloDatabase::class.java, TEST_DB)
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                MIGRATION_14_15, MIGRATION_15_16,
            )
            .build()
        try {
            // A trivial query exercises Room's schema validation against
            // the post-migration database — if entities and SQL diverge,
            // this throws.
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        // MigrationTestHelper auto-cleans the file between tests, but we
        // close anything left open defensively.
    }
}
