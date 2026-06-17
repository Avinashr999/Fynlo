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
     * migration leaves the schema in a state that doesn't match the
     * current entities (would surface as a Room schema-validation crash).
     *
     * Test migrates `v15 → CURRENT` so every migration in the chain runs,
     * not just the one being added in this PR.
     */
    @Test
    @Throws(IOException::class)
    fun migrate15toCurrent_resultingDatabaseOpensCleanlyWithFullRoom() {
        helper.createDatabase(TEST_DB, 15).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, FynloDatabase::class.java, TEST_DB)
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26,
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

    @Test
    @Throws(IOException::class)
    fun migrate25to26_createsDeletedRemoteDocsTombstoneTable() {
        helper.createDatabase(TEST_DB, 25).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            MIGRATION_25_26,
        )

        db.execSQL(
            """
            INSERT INTO deleted_remote_docs (`collection`, id, deletedAt)
            VALUES ('investments', 'inv-1', 123456789)
            """.trimIndent(),
        )

        db.query(
            """
            SELECT deletedAt
            FROM deleted_remote_docs
            WHERE `collection` = ? AND id = ?
            """.trimIndent(),
            arrayOf("investments", "inv-1"),
        ).use { c ->
            assertTrue("v26 tombstone row must be readable after migration.", c.moveToFirst())
            assertEquals(123456789L, c.getLong(0))
            assertFalse(c.moveToNext())
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // C03a Stage 2 — v16 → v17 migration tests (UX_AUDIT §C03 stage 3a).
    // ─────────────────────────────────────────────────────────────────────

    /**
     * The four entities missed by the v14→v15 `createdAt` rollout must all
     * have the column added by `MIGRATION_16_17`. Backfill rules:
     *   - flow_templates / investment_valuations / recurring_transactions
     *     → `createdAt := updatedAt` (best-available proxy).
     *   - net_worth_snapshots → `createdAt := strftime(date) * 1000`
     *     because this entity has no `updatedAt` column.
     */
    @Test
    @Throws(IOException::class)
    fun migrate16to17_addsCreatedAtToTheFourMissedEntitiesAndBackfillsFromUpdatedAt() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            // flow_templates: has updatedAt — should backfill.
            db.execSQL("""
                INSERT INTO flow_templates (id, name, eventType, category, fromAccount, toAccount, projectId, updatedAt)
                VALUES ('ft1', 'Monthly Rent', 'Spent', 'Housing', 'HDFC', '', 'personal', 1700000000000)
            """.trimIndent())

            // investment_valuations: has updatedAt — should backfill.
            db.execSQL("""
                INSERT INTO investment_valuations (id, investmentId, date, value, notes, updatedAt)
                VALUES ('iv1', 'inv-A', '2026-01-01', 50000.0, '', 1700000001000)
            """.trimIndent())

            // recurring_transactions: has updatedAt — should backfill.
            db.execSQL("""
                INSERT INTO recurring_transactions
                    (id, name, type, amount, category, fromAcct, toAcct, frequency, dayOfMonth, notes, isActive, lastRun, projectId, updatedAt)
                VALUES ('rt1', 'Monthly Salary', 'Income', 60000.0, 'Salary', '', 'HDFC', 'Monthly', 1, '', 1, '', 'personal', 1700000002000)
            """.trimIndent())

            // net_worth_snapshots: no updatedAt — should backfill from `date`.
            db.execSQL("""
                INSERT INTO net_worth_snapshots (date, netWorth, totalAssets, totalLiabilities, projectId)
                VALUES ('2026-01-15', 100000.0, 500000.0, 400000.0, 'personal')
            """.trimIndent())
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        // Three rows backfilled from updatedAt — non-zero.
        for ((table, key, expectedCreatedAt) in listOf(
            Triple("flow_templates",         "ft1", 1700000000000L),
            Triple("investment_valuations",  "iv1", 1700000001000L),
            Triple("recurring_transactions", "rt1", 1700000002000L),
        )) {
            db.query("SELECT createdAt FROM $table WHERE id = ?", arrayOf(key)).use { c ->
                assertTrue("Row $table.$key must still exist after migration.", c.moveToFirst())
                assertEquals(
                    "createdAt on $table must backfill from updatedAt.",
                    expectedCreatedAt, c.getLong(0),
                )
            }
        }

        // net_worth_snapshots row: createdAt = strftime('%s', '2026-01-15') * 1000
        // 2026-01-15 00:00:00 UTC = epoch sec 1768435200 → ms 1768435200000.
        db.query(
            "SELECT createdAt FROM net_worth_snapshots WHERE date = ?",
            arrayOf("2026-01-15"),
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1768435200000L, c.getLong(0))
        }
    }

    /**
     * `investment_valuations` was the only scoped sub-entity missing
     * `projectId`. The migration adds the column and backfills from the
     * parent investment's `projectId` so multi-project users keep their
     * valuations correctly scoped.
     */
    @Test
    @Throws(IOException::class)
    fun migrate16to17_backfillsInvestmentValuationProjectIdFromParentInvestment() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            // Parent investment scoped to a non-default project. Column
            // list mirrors the Investment entity's full @Entity shape so
            // the INSERT is independent of default-value drift.
            db.execSQL("""
                INSERT INTO investments
                    (id, name, type, subtype, invested, currentVal, date, maturityDate, rate, realized, withdrawn, notes, projectId, updatedAt, fundingSource, sourceType, linkedDebtId, createdAt)
                VALUES ('inv-side', 'Side Project Investment', 'Stocks', '', 10000.0, 12000.0, '2025-09-01', '', 0.0, 0.0, 0.0, '', 'side-project', 1700000000000, '', '', '', 1700000000000)
            """.trimIndent())
            // Valuation children — should inherit the parent's projectId.
            db.execSQL("""
                INSERT INTO investment_valuations (id, investmentId, date, value, notes, updatedAt)
                VALUES ('iv-side-1', 'inv-side', '2025-12-01', 11000.0, '', 1700000003000),
                       ('iv-side-2', 'inv-side', '2026-03-01', 12000.0, '', 1700000004000)
            """.trimIndent())
            // Orphan valuation (parent doesn't exist) — should fall back to 'personal'.
            db.execSQL("""
                INSERT INTO investment_valuations (id, investmentId, date, value, notes, updatedAt)
                VALUES ('iv-orphan', 'inv-vanished', '2026-04-01', 999.0, '', 1700000005000)
            """.trimIndent())
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        db.query("SELECT id, projectId FROM investment_valuations ORDER BY id").use { c ->
            val seen = mutableMapOf<String, String>()
            while (c.moveToNext()) seen[c.getString(0)] = c.getString(1)
            assertEquals("side-project", seen["iv-side-1"])
            assertEquals("side-project", seen["iv-side-2"])
            assertEquals("Orphan must fall back to 'personal' rather than failing.", "personal", seen["iv-orphan"])
        }
    }

    /**
     * C03a item #5: historical rows with `category` = the literal "Expense"
     * / "Income" / "Transfer" must be rewritten to "Uncategorized" by the
     * one-shot UPDATE in `MIGRATION_16_17`. Legitimate categories must
     * pass through unchanged.
     */
    @Test
    @Throws(IOException::class)
    fun migrate16to17_rewritesForbiddenLegacyCategoryLiteralsToUncategorized() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            // Three bad rows — one for each forbidden literal.
            db.execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAcct, toAcct, category, subcat, person, desc, ref, notes, tags, projectId, updatedAt, createdAt)
                VALUES
                    ('bad-expense',  '2026-05-20', 'Expense',  2000.0, 'Cash', '',     'Expense',  '', '', 'Misc',  '', '', '', 'personal', 1700000000000, 1700000000000),
                    ('bad-income',   '2026-05-21', 'Income',    500.0, '',     'Cash', 'Income',   '', '', 'Gift',  '', '', '', 'personal', 1700000000001, 1700000000001),
                    ('bad-transfer', '2026-05-22', 'Transfer', 1000.0, 'A',    'B',    'Transfer', '', '', 'Move',  '', '', '', 'personal', 1700000000002, 1700000000002)
            """.trimIndent())
            // A legitimate row that must NOT be touched.
            db.execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAcct, toAcct, category, subcat, person, desc, ref, notes, tags, projectId, updatedAt, createdAt)
                VALUES ('good', '2026-05-23', 'Expense', 100.0, 'Cash', '', 'Food', '', '', 'Dinner', '', '', '', 'personal', 1700000000003, 1700000000003)
            """.trimIndent())
            // A row whose category just CONTAINS one of the forbidden words but isn't an exact match.
            db.execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAcct, toAcct, category, subcat, person, desc, ref, notes, tags, projectId, updatedAt, createdAt)
                VALUES ('borderline', '2026-05-24', 'Expense', 50.0, 'Cash', '', 'Income Tax', '', '', 'Q1', '', '', '', 'personal', 1700000000004, 1700000000004)
            """.trimIndent())
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        val rows = mutableMapOf<String, String>()
        db.query("SELECT id, category FROM transactions").use { c ->
            while (c.moveToNext()) rows[c.getString(0)] = c.getString(1)
        }
        assertEquals("bad-expense must be rewritten.",  "Uncategorized", rows["bad-expense"])
        assertEquals("bad-income must be rewritten.",   "Uncategorized", rows["bad-income"])
        assertEquals("bad-transfer must be rewritten.", "Uncategorized", rows["bad-transfer"])
        assertEquals("Legitimate category must pass through unchanged.", "Food", rows["good"])
        assertEquals(
            "Substring match must NOT trigger — exact-match only on the three literals.",
            "Income Tax", rows["borderline"],
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // C22 (3.2.55) — v17 → v18 migration tests.
    //   Adds `iconKey` (default "star") and `linkedAccount` (default "")
    //   to the `goals` table. All pre-3.2.55 goals must backfill cleanly.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Pre-existing goal rows must surface with `iconKey = "star"` and
     * `linkedAccount = ""` after migrating to v18. No data motion; default
     * values come from the ALTER TABLE clause.
     */
    @Test
    @Throws(IOException::class)
    fun migrate17to18_backfillsGoalIconKeyAndLinkedAccountWithDefaults() {
        helper.createDatabase(TEST_DB, 17).apply {
            // The `goals` schema at v17 already has every column except
            // iconKey/linkedAccount. Insert a representative row.
            execSQL("""
                INSERT INTO goals (id, name, targetAmount, savedAmount, deadline,
                                   notes, projectId, updatedAt, createdAt)
                VALUES ('g1', 'New Car', 500000.0, 100000.0, '2026-12-31',
                        '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)

        db.query("SELECT iconKey, linkedAccount FROM goals WHERE id = 'g1'").use { c ->
            assertTrue("Row must survive migration.", c.moveToFirst())
            assertEquals("Default iconKey must be 'star'.", "star", c.getString(0))
            assertEquals("Default linkedAccount must be empty.", "", c.getString(1))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // C22 (3.2.56) — v18 → v19 migration tests.
    //   Adds `description` (default "") to the `projects` table. All
    //   pre-3.2.56 projects must backfill to empty description.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate18to19_backfillsProjectDescriptionWithEmptyDefault() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL("""
                INSERT INTO projects (id, name, icon, color, currency, createdAt, updatedAt)
                VALUES ('p1', 'Side Business', 'business', '#10b981', 'USD',
                        '2025-01-01', 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)

        db.query("SELECT description FROM projects WHERE id = 'p1'").use { c ->
            assertTrue("Row must survive migration.", c.moveToFirst())
            assertEquals("Default description must be empty.", "", c.getString(0))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // C22 (3.2.57) — v19 → v20 migration tests.
    //   Adds `alertThresholdPct` (default 80) to the `budgets` table.
    //   Existing rows must backfill to 80 so the NEAR LIMIT state fires at
    //   the same ratio (0.8) as before the migration.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate19to20_backfillsBudgetAlertThresholdToEightyPercent() {
        helper.createDatabase(TEST_DB, 19).apply {
            execSQL("""
                INSERT INTO budgets (category, limitAmount, period, projectId, updatedAt, createdAt)
                VALUES ('Food', 10000.0, 'Monthly', 'personal',
                        1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

        db.query("SELECT alertThresholdPct FROM budgets WHERE category = 'Food'").use { c ->
            assertTrue("Row must survive migration.", c.moveToFirst())
            assertEquals("Default threshold must be 80 to preserve pre-3.2.57 NEAR LIMIT behaviour.",
                80, c.getInt(0))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3.2.75 — v20 → v21 migration tests.
    //   Renames "Cash in Hand" → "Personal Cash" across every column where
    //   the string appears as an account key. Non-matching rows pass
    //   through unchanged.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate20to21_renamesCashInHandAcrossAccountsAndTransactions() {
        helper.createDatabase(TEST_DB, 20).apply {
            execSQL("""
                INSERT INTO accounts (id, name, type, balance, icon, color, notes, projectId, updatedAt, createdAt)
                VALUES ('a-cash', 'Cash in Hand', 'Cash', 1000.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000),
                       ('a-bank', 'HDFC Bank',    'Bank', 5000.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAcct, toAcct, category,
                                          subcat, person, desc, ref, notes, tags, projectId,
                                          updatedAt, createdAt)
                VALUES
                  ('t-out',  '2026-05-28', 'Expense', 100.0, 'Cash in Hand', '',             'Food',     '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000),
                  ('t-in',   '2026-05-28', 'Income',  500.0, '',             'Cash in Hand', 'Salary',   '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000),
                  ('t-keep', '2026-05-28', 'Expense', 200.0, 'HDFC Bank',    '',             'Food',     '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)

        db.query("SELECT name FROM accounts WHERE id = 'a-cash'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Personal Cash", c.getString(0))
        }
        db.query("SELECT name FROM accounts WHERE id = 'a-bank'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("HDFC Bank should be unchanged.", "HDFC Bank", c.getString(0))
        }
        db.query("SELECT fromAcct, toAcct FROM transactions WHERE id = 't-out'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Personal Cash", c.getString(0))
            assertEquals("", c.getString(1))
        }
        db.query("SELECT fromAcct, toAcct FROM transactions WHERE id = 't-in'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals("Personal Cash", c.getString(1))
        }
        db.query("SELECT fromAcct FROM transactions WHERE id = 't-keep'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("HDFC Bank", c.getString(0))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // C03b Stage #1a (3.2.86) — v22 → v23 migration tests.
    //   Adds `fromAcctId` / `toAcctId` (TEXT NOT NULL DEFAULT '') to the
    //   `transactions` table and backfills each non-empty name from the
    //   corresponding `accounts.id`. Rows whose `fromAcct` / `toAcct` does
    //   not match any current account leave the id at '' (orphans surface
    //   to the 3.2.59 repair tool — the migration must not invent ids).
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate22to23_addsAccountIdColumnsAndBackfillsFromAccountNames() {
        helper.createDatabase(TEST_DB, 22).apply {
            // Two accounts with known ids.
            execSQL("""
                INSERT INTO accounts (id, name, type, balance, icon, color, notes, projectId, updatedAt, createdAt)
                VALUES
                  ('acc-cash', 'Personal Cash', 'Cash', 1000.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000),
                  ('acc-hdfc', 'HDFC Bank',     'Bank', 5000.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            // Mixed transactions covering every shape:
            //   - resolvable expense (fromAcct only)
            //   - resolvable income  (toAcct only)
            //   - resolvable transfer (both)
            //   - orphan expense (fromAcct points at vanished account)
            //   - blank both (legacy income row with no toAcct — leaves '')
            execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAcct, toAcct, category,
                                          subcat, person, desc, ref, notes, tags, projectId,
                                          updatedAt, createdAt)
                VALUES
                  ('t-exp',      '2026-05-28', 'Expense',  100.0, 'Personal Cash', '',              'Food',   '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000),
                  ('t-inc',      '2026-05-28', 'Income',   500.0, '',              'HDFC Bank',     'Salary', '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000),
                  ('t-xfer',     '2026-05-28', 'Transfer', 200.0, 'HDFC Bank',     'Personal Cash', 'Transfer', '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000),
                  ('t-orphan',   '2026-05-28', 'Expense',   50.0, 'Vanished Acct', '',              'Misc',   '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000),
                  ('t-no-acct',  '2026-05-28', 'Income',    10.0, '',              '',              'Salary', '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 23, true, MIGRATION_22_23)

        // Resolvable rows: id mirror matches the source account id.
        db.query("SELECT fromAcctId, toAcctId FROM transactions WHERE id = ?",
                 arrayOf("t-exp")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("acc-cash", c.getString(0))
            assertEquals("",         c.getString(1))
        }
        db.query("SELECT fromAcctId, toAcctId FROM transactions WHERE id = ?",
                 arrayOf("t-inc")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("",          c.getString(0))
            assertEquals("acc-hdfc",  c.getString(1))
        }
        db.query("SELECT fromAcctId, toAcctId FROM transactions WHERE id = ?",
                 arrayOf("t-xfer")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("acc-hdfc",  c.getString(0))
            assertEquals("acc-cash",  c.getString(1))
        }
        // Orphan: fromAcct has no matching Account row → id stays '' (the
        // migration must NOT invent an id; orphans surface to the existing
        // 3.2.59 repair tool).
        db.query("SELECT fromAcctId, toAcctId FROM transactions WHERE id = ?",
                 arrayOf("t-orphan")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Orphan row must surface with empty id, not a guessed one.",
                "", c.getString(0))
            assertEquals("", c.getString(1))
        }
        // Both-blank: nothing to backfill.
        db.query("SELECT fromAcctId, toAcctId FROM transactions WHERE id = ?",
                 arrayOf("t-no-acct")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals("", c.getString(1))
        }
    }

    /**
     * Idempotence: re-running the migration must be a no-op for rows whose
     * id was already populated by a previous run. The migration's UPDATE
     * uses `WHERE fromAcctId = ''` to guard exactly this case.
     */
    @Test
    @Throws(IOException::class)
    fun migrate22to23_doesNotOverwriteAlreadyPopulatedIds() {
        helper.createDatabase(TEST_DB, 22).apply {
            execSQL("""
                INSERT INTO accounts (id, name, type, balance, icon, color, notes, projectId, updatedAt, createdAt)
                VALUES ('acc-renamed', 'New Name', 'Bank', 100.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            // A row whose fromAcct still says "Old Name" but whose ID column
            // ... well, the column doesn't exist at v22 yet. So we can only
            // assert the WHERE-guard intent indirectly. Instead, just check
            // that running the migration once then re-applying the same
            // UPDATE leaves the field untouched (which is what the guard
            // does in practice). We run the migration, then verify a
            // second pass of the same UPDATE on the v23 schema is a no-op.
            execSQL("""
                INSERT INTO transactions (id, date, type, amount, fromAcct, toAcct, category,
                                          subcat, person, desc, ref, notes, tags, projectId,
                                          updatedAt, createdAt)
                VALUES ('t-renamed', '2026-05-28', 'Expense', 100.0, 'Old Name', '',
                        'Misc', '', '', '', '', '', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 23, true, MIGRATION_22_23)

        // 'Old Name' has no matching account → fromAcctId stays empty.
        db.query("SELECT fromAcctId FROM transactions WHERE id = 't-renamed'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
        }

        // Manually set the id (simulating Stage #1b's resolver or a manual repair).
        db.execSQL("UPDATE transactions SET fromAcctId = 'acc-renamed' WHERE id = 't-renamed'")

        // Re-applying the v22→v23 backfill SQL must NOT overwrite the
        // already-populated id (the WHERE fromAcctId = '' guard).
        db.execSQL("""
            UPDATE `transactions`
            SET    `fromAcctId` = COALESCE(
                       (SELECT `id` FROM `accounts` WHERE `accounts`.`name` = `transactions`.`fromAcct`),
                       ''
                   )
            WHERE  `fromAcct` != '' AND `fromAcctId` = ''
        """.trimIndent())
        db.query("SELECT fromAcctId FROM transactions WHERE id = 't-renamed'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Already-populated id must survive a repeated backfill pass.",
                "acc-renamed", c.getString(0))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // C03b Stage #1c (3.2.89) — v23 → v24 migration tests.
    //   Adds `fromAcctId` / `toAcctId` (TEXT NOT NULL DEFAULT '') to the
    //   `recurring_transactions` table and backfills by joining on name —
    //   mirrors `MIGRATION_22_23` exactly. RecurringWorker uses the
    //   stored id at fire time for rename-safe balance updates.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate23to24_addsAccountIdColumnsAndBackfillsRecurringTemplatesFromAccountNames() {
        helper.createDatabase(TEST_DB, 23).apply {
            execSQL("""
                INSERT INTO accounts (id, name, type, balance, icon, color, notes, projectId, updatedAt, createdAt)
                VALUES
                  ('acc-hdfc', 'HDFC Bank',     'Bank', 5000.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000),
                  ('acc-cash', 'Personal Cash', 'Cash', 1000.0, '', '#3b82f6', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            // Three recurring templates covering the cases the resolver
            // and the worker care about:
            //   - resolvable expense  → fromAcct only
            //   - resolvable income   → toAcct only
            //   - orphan expense      → fromAcct points at vanished account
            execSQL("""
                INSERT INTO recurring_transactions
                    (id, name, type, amount, category, fromAcct, toAcct, frequency,
                     dayOfMonth, notes, isActive, lastRun, projectId, updatedAt, createdAt)
                VALUES
                    ('rt-rent',   'Monthly Rent',   'Expense', 10000.0, 'Rent',   'HDFC Bank',     '',          'Monthly', 1, '', 1, '', 'personal', 1700000000000, 1700000000000),
                    ('rt-salary', 'Monthly Salary', 'Income',  60000.0, 'Salary', '',              'HDFC Bank', 'Monthly', 1, '', 1, '', 'personal', 1700000000000, 1700000000000),
                    ('rt-orphan', 'Old Card EMI',   'Expense',  5000.0, 'EMI',    'Vanished Card', '',          'Monthly', 5, '', 1, '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 24, true, MIGRATION_23_24)

        db.query("SELECT fromAcctId, toAcctId FROM recurring_transactions WHERE id = ?",
                 arrayOf("rt-rent")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("acc-hdfc", c.getString(0))
            assertEquals("",         c.getString(1))
        }
        db.query("SELECT fromAcctId, toAcctId FROM recurring_transactions WHERE id = ?",
                 arrayOf("rt-salary")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("",         c.getString(0))
            assertEquals("acc-hdfc", c.getString(1))
        }
        db.query("SELECT fromAcctId, toAcctId FROM recurring_transactions WHERE id = ?",
                 arrayOf("rt-orphan")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Orphan template — id stays empty (worker falls back to name).",
                "", c.getString(0))
            assertEquals("", c.getString(1))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // C03b Stage #3 (3.2.90) — v24 → v25 migration tests.
    //   Adds `peopleId` (TEXT NOT NULL DEFAULT '') to borrowers + debts.
    //   Three-step backfill: link to existing Person rows by phone,
    //   create new Person rows for unmatched non-empty phones (deduped
    //   across borrowers + debts), then re-link. Empty-phone rows stay
    //   peopleId = '' for the user to link manually.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate24to25_linksBorrowerAndDebtToExistingPersonByPhone() {
        helper.createDatabase(TEST_DB, 24).apply {
            // Person already created via the People screen, with phone.
            execSQL("""
                INSERT INTO people (id, name, phone, type, notes, projectId, updatedAt, createdAt)
                VALUES ('P-ravi', 'Ravi Kumar', '9876543210', 'Individual', '', 'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            // Borrower with the same phone — implicit association.
            execSQL("""
                INSERT INTO borrowers
                    (id, name, phone, address, guarantor, amount, rate, date, due, tenure, type,
                     paid, paidPrincipal, paidInterest, status, defaultDate, frozenInterest,
                     sourceAccount, notes, projectId, updatedAt, createdAt)
                VALUES ('b-ravi', 'Ravi', '9876543210', '', '', 50000.0, 12.0, '2024-01-01', '', 0,
                        'Simple Interest', 0.0, 0.0, 0.0, 'Active', '', 0.0, '', '',
                        'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            // Debt with the same phone — same Person.
            execSQL("""
                INSERT INTO debts
                    (id, name, phone, type, amount, rate, date, due, tenure, intType,
                     paid, paidPrincipal, paidInterest, status, collateral, notes, projectId,
                     updatedAt, createdAt)
                VALUES ('d-ravi', 'Ravi (cash advance)', '9876543210', 'Friend / Family',
                        20000.0, 0.0, '2024-02-01', '', 0, 'Simple Interest',
                        0.0, 0.0, 0.0, 'Active', '', '', 'personal',
                        1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 25, true, MIGRATION_24_25)

        db.query("SELECT peopleId FROM borrowers WHERE id = 'b-ravi'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Borrower must link to the existing Person row.",
                "P-ravi", c.getString(0))
        }
        db.query("SELECT peopleId FROM debts WHERE id = 'd-ravi'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Debt with the same phone must link to the same Person.",
                "P-ravi", c.getString(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate24to25_createsNewPersonForUnmatchedPhoneAndLinksBothBorrowerAndDebt() {
        helper.createDatabase(TEST_DB, 24).apply {
            // No pre-existing Person.
            execSQL("""
                INSERT INTO borrowers
                    (id, name, phone, address, guarantor, amount, rate, date, due, tenure, type,
                     paid, paidPrincipal, paidInterest, status, defaultDate, frozenInterest,
                     sourceAccount, notes, projectId, updatedAt, createdAt)
                VALUES ('b-new', 'Suresh', '9112233445', '', '', 25000.0, 18.0, '2024-03-01', '', 0,
                        'Compound Interest', 0.0, 0.0, 0.0, 'Active', '', 0.0, '', '',
                        'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            // Debt with the SAME phone — must dedup to one Person.
            execSQL("""
                INSERT INTO debts
                    (id, name, phone, type, amount, rate, date, due, tenure, intType,
                     paid, paidPrincipal, paidInterest, status, collateral, notes, projectId,
                     updatedAt, createdAt)
                VALUES ('d-new', 'Suresh', '9112233445', 'Friend / Family',
                        5000.0, 0.0, '2024-04-01', '', 0, 'Simple Interest',
                        0.0, 0.0, 0.0, 'Active', '', '', 'personal',
                        1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 25, true, MIGRATION_24_25)

        // Exactly ONE Person was created for the phone.
        db.query("SELECT COUNT(*) FROM people WHERE phone = '9112233445'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "Migration must dedup borrowers + debts with the same phone into ONE Person.",
                1, c.getInt(0),
            )
        }
        // Both borrower and debt link to that same Person.
        var borrowerPersonId = ""
        var debtPersonId     = ""
        db.query("SELECT peopleId FROM borrowers WHERE id = 'b-new'").use { c ->
            assertTrue(c.moveToFirst())
            borrowerPersonId = c.getString(0)
        }
        db.query("SELECT peopleId FROM debts WHERE id = 'd-new'").use { c ->
            assertTrue(c.moveToFirst())
            debtPersonId = c.getString(0)
        }
        assertEquals("Borrower and debt must point at the same Person id.",
            borrowerPersonId, debtPersonId)
        assertTrue("Created Person id must be non-empty.", borrowerPersonId.isNotEmpty())
    }

    @Test
    @Throws(IOException::class)
    fun migrate24to25_leavesEmptyPhoneBorrowerUnlinked() {
        helper.createDatabase(TEST_DB, 24).apply {
            execSQL("""
                INSERT INTO borrowers
                    (id, name, phone, address, guarantor, amount, rate, date, due, tenure, type,
                     paid, paidPrincipal, paidInterest, status, defaultDate, frozenInterest,
                     sourceAccount, notes, projectId, updatedAt, createdAt)
                VALUES ('b-anon', 'Anonymous', '', '', '', 1000.0, 0.0, '2024-05-01', '', 0,
                        'Simple Interest', 0.0, 0.0, 0.0, 'Active', '', 0.0, '', '',
                        'personal', 1700000000000, 1700000000000)
            """.trimIndent())
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 25, true, MIGRATION_24_25)

        db.query("SELECT peopleId FROM borrowers WHERE id = 'b-anon'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "Empty-phone borrowers must stay unlinked — dedup on name alone is unsafe.",
                "", c.getString(0),
            )
        }
        db.query("SELECT COUNT(*) FROM people").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("No Person row should be created for empty-phone rows.",
                0, c.getInt(0))
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        // MigrationTestHelper auto-cleans the file between tests, but we
        // close anything left open defensively.
    }
}
