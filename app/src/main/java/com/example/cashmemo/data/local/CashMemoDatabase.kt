package com.example.cashmemo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cashmemo.data.model.*

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
        Project::class      // new in v2.0
    ],
    version = 4,
    exportSchema = false
)
abstract class CashMemoDatabase : RoomDatabase() {
    abstract fun dao(): CashMemoDao
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
