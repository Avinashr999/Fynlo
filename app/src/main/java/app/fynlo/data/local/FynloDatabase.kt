package app.fynlo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.fynlo.data.model.*
import app.fynlo.data.model.FlowTemplate

@Database(
    entities = [
        Borrower::class,
        Payment::class,
        Account::class,
        Transaction::class,
        Investment::class,
        Debt::class,
        DebtPayment::class,
        Person::class,
        Budget::class,
        Goal::class,
        Project::class,
        FlowTemplate::class, // new in v2.0 Phase 4
        RecurringTransaction::class,
        NetWorthSnapshot::class,
        InvestmentValuation::class
    ],
    version = 16,
    exportSchema = true
)
abstract class FynloDatabase : RoomDatabase() {
    abstract fun dao(): FynloDao
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `investment_valuations` (
                `id` TEXT NOT NULL, 
                `investmentId` TEXT NOT NULL, 
                `date` TEXT NOT NULL, 
                `value` REAL NOT NULL, 
                `notes` TEXT NOT NULL, 
                `updatedAt` INTEGER NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

/**
 * Migration 8 → 9
 * Adds funding-source tracking columns to investments table.
 * All existing investments default to empty strings — no data loss.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `investments` ADD COLUMN `fundingSource` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `investments` ADD COLUMN `sourceType`    TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `investments` ADD COLUMN `linkedDebtId`  TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Migration 3 → 4
 * Adds `projectId` and `updatedAt` to all 10 existing tables.
 * Creates the new `projects` table.
 * Safe for existing users — all rows default to projectId = "personal".
 */

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Borrower: split paid into paidPrincipal + paidInterest, add defaulter fields
        db.execSQL("ALTER TABLE `borrowers` ADD COLUMN `paidPrincipal`  REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `borrowers` ADD COLUMN `paidInterest`   REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `borrowers` ADD COLUMN `defaultDate`    TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `borrowers` ADD COLUMN `frozenInterest` REAL NOT NULL DEFAULT 0.0")
        // Debt: split paid into paidPrincipal + paidInterest
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `paidPrincipal` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `paidInterest`  REAL NOT NULL DEFAULT 0.0")
        // Seed paidPrincipal = paid for all existing borrowers/debts
        // This preserves old behaviour: existing paid amount treated as all-principal
        db.execSQL("UPDATE `borrowers` SET `paidPrincipal` = `paid`")
        db.execSQL("UPDATE `debts`     SET `paidPrincipal` = `paid`")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fix double-counted paid field caused by a bug where both
        // updateBorrowerPaidPrincipal AND updateBorrowerPaidAmount were called.
        // Recompute paid = paidPrincipal + paidInterest for all borrowers and debts.
        // Safe: migration 9→10 already seeded paidPrincipal = paid for old records,
        // so this formula is always correct.
        db.execSQL("UPDATE `borrowers` SET `paid` = `paidPrincipal` + `paidInterest`")
        db.execSQL("UPDATE `debts`     SET `paid` = `paidPrincipal` + `paidInterest`")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Migration 10→11 had a destructive flaw: it ran SET paid = paidPrincipal + paidInterest
        // but for hand loans paid via the old single-amount dialog (after v10 install),
        // paidPrincipal was 0 and the migration wiped the payment entirely.
        //
        // Fix: first re-seed paidPrincipal = paid for any record where
        // split fields are both 0 but paid > 0 (old-style payment).
        // Then recalculate paid = paidPrincipal + paidInterest.
        db.execSQL("""
            UPDATE `borrowers`
            SET    `paidPrincipal` = `paid`
            WHERE  `paidPrincipal` = 0 AND `paidInterest` = 0 AND `paid` > 0
        """.trimIndent())
        db.execSQL("""
            UPDATE `debts`
            SET    `paidPrincipal` = `paid`
            WHERE  `paidPrincipal` = 0 AND `paidInterest` = 0 AND `paid` > 0
        """.trimIndent())
        db.execSQL("UPDATE `borrowers` SET `paid` = `paidPrincipal` + `paidInterest`")
        db.execSQL("UPDATE `debts`     SET `paid` = `paidPrincipal` + `paidInterest`")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Migrations 10→11 and 11→12 had a flaw: they recomputed paid from paidPrincipal
        // but for payments recorded via the old single-amount dialog (principal=0, interest=0),
        // paidPrincipal was never updated — so those payments were silently wiped.
        //
        // Nuclear fix: rebuild paid, paidPrincipal, paidInterest directly from the
        // payments table (the true source of record), not from the denormalized fields.

        // Rebuild borrower paid amounts from payments table
        db.execSQL("""
            UPDATE borrowers SET
              paid         = COALESCE((SELECT SUM(amount)   FROM payments WHERE loanId = borrowers.id), 0),
              paidPrincipal= COALESCE((SELECT SUM(CASE WHEN principal > 0 THEN principal ELSE amount END)
                                       FROM payments WHERE loanId = borrowers.id), 0),
              paidInterest = COALESCE((SELECT SUM(interest) FROM payments WHERE loanId = borrowers.id), 0)
        """.trimIndent())

        // Rebuild debt paid amounts from debt_payments table
        db.execSQL("""
            UPDATE debts SET
              paid         = COALESCE((SELECT SUM(amount)   FROM debt_payments WHERE debtId = debts.id), 0),
              paidPrincipal= COALESCE((SELECT SUM(CASE WHEN principal > 0 THEN principal ELSE amount END)
                                       FROM debt_payments WHERE debtId = debts.id), 0),
              paidInterest = COALESCE((SELECT SUM(interest) FROM debt_payments WHERE debtId = debts.id), 0)
        """.trimIndent())
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE borrowers ADD COLUMN sourceAccount TEXT NOT NULL DEFAULT ''")
        // Backfill from linked expense transactions (lend transactions use fromAcct)
        db.execSQL("""
            UPDATE borrowers SET sourceAccount = COALESCE(
                (SELECT fromAcct FROM transactions
                 WHERE ref = borrowers.id AND type = 'Expense' AND category = 'Lending'
                 ORDER BY updatedAt DESC LIMIT 1), '')
        """.trimIndent())
    }
}

// C01 fix — Sprint 1 (UX_AUDIT §C01, decisions/2026-05-26-c01-fix-strategy.md).
// For every borrower/debt where `paid > 0` but no rows in payments/debt_payments
// reference it (the legacy data shape, where partial repayment lived only on the
// cumulative `paid` field), insert ONE synthetic Payment row dated `loanDate`
// with `principal = paid, interest = 0, type = "Legacy backfill"`. After this
// migration, `payments`/`debt_payments` are the authoritative repayment history
// for every borrower/debt and `recalculateAllBalances()` can safely derive
// `paid` from SUM(payments) without losing legacy data.
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 1. Borrowers → payments
        db.execSQL("""
            INSERT INTO payments
                (id, loanId, name, date, type, amount, principal, interest, mode, notes, projectId, updatedAt, createdAt)
            SELECT
                'legacy-bf-' || b.id,
                b.id,
                b.name,
                b.date,
                'Legacy backfill',
                b.paid,
                b.paid,
                0,
                '',
                'Imported from legacy schema; actual repayment date unknown',
                b.projectId,
                $now,
                $now
            FROM borrowers b
            WHERE b.paid > 0
              AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.loanId = b.id)
        """.trimIndent())

        // 2. Debts → debt_payments (same shape)
        db.execSQL("""
            INSERT INTO debt_payments
                (id, debtId, name, date, type, amount, principal, interest, mode, notes, projectId, updatedAt, createdAt)
            SELECT
                'legacy-bf-' || d.id,
                d.id,
                d.name,
                d.date,
                'Legacy backfill',
                d.paid,
                d.paid,
                0,
                '',
                'Imported from legacy schema; actual repayment date unknown',
                d.projectId,
                $now,
                $now
            FROM debts d
            WHERE d.paid > 0
              AND NOT EXISTS (SELECT 1 FROM debt_payments dp WHERE dp.debtId = d.id)
        """.trimIndent())

        // 3. Seed the split fields so `paid == paidPrincipal + paidInterest`
        //    holds immediately after this migration — same fix pattern as
        //    migration 11→12, but scoped only to the rows we just backfilled.
        db.execSQL("""
            UPDATE borrowers
            SET paidPrincipal = paid
            WHERE paid > 0 AND paidPrincipal = 0 AND paidInterest = 0
        """.trimIndent())
        db.execSQL("""
            UPDATE debts
            SET paidPrincipal = paid
            WHERE paid > 0 AND paidPrincipal = 0 AND paidInterest = 0
        """.trimIndent())
    }
}

// #05 audit columns: add createdAt to every user-facing table and backfill
// existing rows from updatedAt (best available proxy for "when created").
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf(
            "accounts", "transactions", "borrowers", "debts", "investments",
            "payments", "debt_payments", "people", "budgets", "goals"
        )
        tables.forEach { t ->
            db.execSQL("ALTER TABLE $t ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $t SET createdAt = updatedAt WHERE createdAt = 0")
        }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf(
            "accounts", "borrowers", "transactions", "payments",
            "investments", "debts", "debt_payments",
            "people", "budgets", "goals"
        )
        tables.forEach { table ->
            db.execSQL(
                "ALTER TABLE `$table` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT 'personal'"
            )
            db.execSQL(
                "ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
            )
        }

        // Create the new projects table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `projects` (
                `id`        TEXT NOT NULL,
                `name`      TEXT NOT NULL,
                `icon`      TEXT NOT NULL DEFAULT 'business',
                `color`     TEXT NOT NULL DEFAULT '#3b82f6',
                `currency`  TEXT NOT NULL DEFAULT 'INR',
                `createdAt` TEXT NOT NULL DEFAULT '',
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // Insert the default "Personal" project so the app works on first launch
        db.execSQL(
            """
            INSERT OR IGNORE INTO projects (id, name, icon, color, currency, createdAt, updatedAt)
            VALUES ('personal', 'Personal', 'person', '#3b82f6', 'INR', '', 0)
            """.trimIndent()
        )
    }
}


/**
 * Migration 4 -> 5
 * Creates the flow_templates table for saved wizard templates.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `flow_templates` (
                `id`          TEXT NOT NULL,
                `name`        TEXT NOT NULL,
                `eventType`   TEXT NOT NULL,
                `category`    TEXT NOT NULL DEFAULT '',
                `fromAccount` TEXT NOT NULL DEFAULT '',
                `toAccount`   TEXT NOT NULL DEFAULT '',
                `projectId`   TEXT NOT NULL DEFAULT 'personal',
                `updatedAt`   INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

/**
 * Migration 5 -> 6
 * Creates the recurring_transactions table (optional feature).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `recurring_transactions` (
                `id`         TEXT NOT NULL,
                `name`       TEXT NOT NULL DEFAULT '',
                `type`       TEXT NOT NULL DEFAULT 'Expense',
                `amount`     REAL NOT NULL DEFAULT 0.0,
                `category`   TEXT NOT NULL DEFAULT '',
                `fromAcct`   TEXT NOT NULL DEFAULT '',
                `toAcct`     TEXT NOT NULL DEFAULT '',
                `frequency`  TEXT NOT NULL DEFAULT 'Monthly',
                `dayOfMonth` INTEGER NOT NULL DEFAULT 1,
                `notes`      TEXT NOT NULL DEFAULT '',
                `isActive`   INTEGER NOT NULL DEFAULT 1,
                `lastRun`    TEXT NOT NULL DEFAULT '',
                `projectId`  TEXT NOT NULL DEFAULT 'personal',
                `updatedAt`  INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}


val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `net_worth_snapshots` (
                `date`             TEXT NOT NULL,
                `netWorth`         REAL NOT NULL DEFAULT 0.0,
                `totalAssets`      REAL NOT NULL DEFAULT 0.0,
                `totalLiabilities` REAL NOT NULL DEFAULT 0.0,
                `projectId`        TEXT NOT NULL DEFAULT 'personal',
                PRIMARY KEY(`date`)
            )
        """.trimIndent())
    }
}
