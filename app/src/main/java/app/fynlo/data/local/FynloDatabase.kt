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
        RecurringTransaction::class, // new in v2.4
        NetWorthSnapshot::class        // new in v2.5
    ],
    version = 8,
    exportSchema = false
)
abstract class FynloDatabase : RoomDatabase() {
    abstract fun dao(): FynloDao
}

/**
 * Migration 3 → 4
 * Adds `projectId` and `updatedAt` to all 10 existing tables.
 * Creates the new `projects` table.
 * Safe for existing users — all rows default to projectId = "personal".
 */
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

/**
 * Migration 7 → 8
 * Adds funding source tracking to the investments table.
 * Every existing investment gets empty strings — safe, no data loss.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `investments` ADD COLUMN `fundingSource` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `investments` ADD COLUMN `sourceType`    TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `investments` ADD COLUMN `linkedDebtId`  TEXT NOT NULL DEFAULT ''")
    }
}
