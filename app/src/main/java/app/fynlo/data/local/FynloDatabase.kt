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
        InvestmentValuation::class,
        DeletedRemoteDoc::class,
        AuditEvent::class,
        MonthlyClose::class,
        UndoAction::class,
        ProofAttachment::class,
        SyncConflict::class
    ],
    version = 29,
    exportSchema = true
)
abstract class FynloDatabase : RoomDatabase() {
    abstract fun dao(): FynloDao
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `monthly_closes` (
                `id` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `month` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `closedAt` INTEGER NOT NULL,
                `reopenedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `undo_actions` (
                `id` TEXT NOT NULL,
                `action` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `beforeJson` TEXT NOT NULL,
                `afterJson` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `consumedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `proof_attachments` (
                `id` TEXT NOT NULL,
                `ownerType` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `localUri` TEXT NOT NULL,
                `remotePath` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                `id` TEXT NOT NULL,
                `collection` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `fieldSummary` TEXT NOT NULL,
                `localJson` TEXT NOT NULL,
                `remoteJson` TEXT NOT NULL,
                `resolution` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `resolvedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_monthly_closes_project_month` ON `monthly_closes` (`projectId`, `month`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_undo_actions_active` ON `undo_actions` (`projectId`, `expiresAt`, `consumedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_proof_attachments_owner` ON `proof_attachments` (`ownerType`, `ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sync_conflicts_open` ON `sync_conflicts` (`projectId`, `resolution`, `createdAt`)")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `borrowers` ADD COLUMN `interestWaived` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `debts` ADD COLUMN `interestWaived` REAL NOT NULL DEFAULT 0.0")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `audit_events` (
                `id` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `action` TEXT NOT NULL,
                `entityType` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `beforeValue` TEXT NOT NULL,
                `afterValue` TEXT NOT NULL,
                `amountDelta` REAL NOT NULL,
                `accountName` TEXT NOT NULL,
                `projectId` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `actor` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `deleted_remote_docs` (
                `collection` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                PRIMARY KEY(`collection`, `id`)
            )
        """.trimIndent())
    }
}

// C03b Stage #3 (3.2.90) — additive `peopleId` on borrowers + debts.
// Mirrors the v22→v23 / v23→v24 account-id pattern but for the
// borrower/debt → person relationship. Three-step backfill:
//
//   1. ALTER both tables to add the new column.
//   2. Link rows whose `phone` already matches an existing Person to
//      that Person's id. The Person was likely created via the People
//      screen separately from the loan; this captures the implicit
//      association.
//   3. For every (name, phone) pair that's still unlinked AND has a
//      non-empty phone (so we can dedup safely), create one Person row
//      and link every borrower + debt with that phone to it. Empty-phone
//      rows are skipped — we can't dedup safely on name alone (two
//      "Ravi"s in the same project could be distinct people) so they
//      stay with peopleId = '' until the user manually links them via
//      the People screen.
//
// SQLite-portable random ids: `lower(hex(randomblob(8)))` → 16-char hex
// strings prefixed with 'P-' to match the existing `P-001`-style
// scheme. Distinct enough for the small number of rows a single user's
// data has; the resolver-on-write path uses UUIDs for new Person rows
// created post-migration so the two schemes coexist.
//
// Idempotent — re-running is safe because the WHERE clauses guard on
// `peopleId = ''` and `NOT EXISTS` against existing Person rows.
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 1. Add columns.
        db.execSQL("ALTER TABLE `borrowers` ADD COLUMN `peopleId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `debts`     ADD COLUMN `peopleId` TEXT NOT NULL DEFAULT ''")

        // 2. Link rows to existing Person records by phone.
        db.execSQL("""
            UPDATE `borrowers`
            SET    `peopleId` = COALESCE(
                       (SELECT `id` FROM `people` WHERE `phone` = `borrowers`.`phone` AND `phone` != ''),
                       ''
                   )
            WHERE  `phone` != '' AND `peopleId` = ''
        """.trimIndent())
        db.execSQL("""
            UPDATE `debts`
            SET    `peopleId` = COALESCE(
                       (SELECT `id` FROM `people` WHERE `phone` = `debts`.`phone` AND `phone` != ''),
                       ''
                   )
            WHERE  `phone` != '' AND `peopleId` = ''
        """.trimIndent())

        // 3. Create new Person rows for unmatched non-empty phones, then
        //    re-run the link pass. The INSERT...SELECT GROUP BY clause
        //    dedups across borrowers + debts in a single pass — a phone
        //    that appears in both tables ends up with exactly one new
        //    Person, and both rows link to it.
        db.execSQL("""
            INSERT INTO `people` (id, name, phone, type, notes, projectId, updatedAt, createdAt)
            SELECT
                'P-' || lower(hex(randomblob(8))),
                MIN(name),
                phone,
                'Individual',
                '',
                MIN(projectId),
                $now,
                $now
            FROM (
                SELECT name, phone, projectId FROM borrowers WHERE phone != '' AND peopleId = ''
                UNION ALL
                SELECT name, phone, projectId FROM debts     WHERE phone != '' AND peopleId = ''
            ) AS combined
            WHERE NOT EXISTS (SELECT 1 FROM people WHERE people.phone = combined.phone AND people.phone != '')
            GROUP BY phone
        """.trimIndent())

        // 4. Final link pass — now every non-empty phone has a matching
        //    Person row (existing or just-created), so the UPDATE
        //    populates `peopleId` everywhere.
        db.execSQL("""
            UPDATE `borrowers`
            SET    `peopleId` = COALESCE(
                       (SELECT `id` FROM `people` WHERE `phone` = `borrowers`.`phone` AND `phone` != ''),
                       ''
                   )
            WHERE  `phone` != '' AND `peopleId` = ''
        """.trimIndent())
        db.execSQL("""
            UPDATE `debts`
            SET    `peopleId` = COALESCE(
                       (SELECT `id` FROM `people` WHERE `phone` = `debts`.`phone` AND `phone` != ''),
                       ''
                   )
            WHERE  `phone` != '' AND `peopleId` = ''
        """.trimIndent())
    }
}

// C03b Stage #1c (3.2.89) — additive `fromAcctId` / `toAcctId` on
// recurring_transactions. Mirrors `MIGRATION_22_23` (which did the same
// for the transactions table). Same rationale: an account rename can't
// orphan a RecurringWorker auto-fire if the worker keys on the immutable
// id instead of the stored name.
//
// Backfill uses the same name-join trick. Orphans (no matching account
// at migration time) leave the id at `''` — RecurringScreen's create
// path resolves on every save so the id catches up the next time the
// user edits the row, and `applyAccountDelta` in the worker falls
// through to the legacy name-keyed query for any row that stays
// orphaned.
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `fromAcctId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `toAcctId`   TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            UPDATE `recurring_transactions`
            SET    `fromAcctId` = COALESCE(
                       (SELECT `id` FROM `accounts` WHERE `accounts`.`name` = `recurring_transactions`.`fromAcct`),
                       ''
                   )
            WHERE  `fromAcct` != '' AND `fromAcctId` = ''
        """.trimIndent())
        db.execSQL("""
            UPDATE `recurring_transactions`
            SET    `toAcctId`   = COALESCE(
                       (SELECT `id` FROM `accounts` WHERE `accounts`.`name` = `recurring_transactions`.`toAcct`),
                       ''
                   )
            WHERE  `toAcct`   != '' AND `toAcctId`   = ''
        """.trimIndent())
    }
}

// C03b Stage #1a (3.2.86) — additive `fromAcctId` / `toAcctId` on transactions.
// The audit's §C03b complaint is that `Transaction.fromAcct` / `.toAcct` are
// account-name *strings*, which means an account rename silently orphans
// every row referencing the old name (the 3.2.59 bug pattern). Stage #1a is
// the **additive** half of the fix:
//   - Add nullable `fromAcctId` / `toAcctId` columns (TEXT, NOT NULL, default
//     ''). Empty string means "unresolved at write time".
//   - Backfill from accounts by joining on name. Rows whose name doesn't
//     match any current account stay with '' (the migration can't invent an
//     id that doesn't exist; the orphan repair tool from 3.2.59 still
//     covers those manually).
//   - Reads still come from `fromAcct` / `toAcct`. Stage #1b will flip the
//     reads and drop the name columns once `fromAcctId` is proven stable
//     across a release cycle.
//
// Idempotent: re-running the UPDATE is a no-op (`WHERE fromAcctId = ''`).
// Safe for users with renamed accounts because the v20→v21 and v21→v22
// migrations already rebalanced `fromAcct` / `toAcct` to the canonical name
// before this join runs.
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `fromAcctId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `toAcctId`   TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            UPDATE `transactions`
            SET    `fromAcctId` = COALESCE(
                       (SELECT `id` FROM `accounts` WHERE `accounts`.`name` = `transactions`.`fromAcct`),
                       ''
                   )
            WHERE  `fromAcct` != '' AND `fromAcctId` = ''
        """.trimIndent())
        db.execSQL("""
            UPDATE `transactions`
            SET    `toAcctId`   = COALESCE(
                       (SELECT `id` FROM `accounts` WHERE `accounts`.`name` = `transactions`.`toAcct`),
                       ''
                   )
            WHERE  `toAcct`   != '' AND `toAcctId`   = ''
        """.trimIndent())
    }
}

// 3.2.76 — Re-apply the "Cash in Hand" → "Personal Cash" rename AND bump
// `updatedAt` to NOW on every renamed row. The original v20→v21 migration
// renamed the rows but left `updatedAt` untouched. The Firestore listener's
// last-write-wins guard (`remote.updatedAt >= local.updatedAt`) then saw
// cloud's "Cash in Hand" as the same age as local's "Personal Cash" and
// overwrote local back to the old name on the next sync. Bumping
// `updatedAt` makes local strictly newer, so the listener no-ops; the
// post-init `pushAllLocalToFirestore` then carries the new name to cloud.
//
// Idempotent — no rows match if local was never reverted, or has already
// been re-renamed by this migration.
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE `accounts`               SET `name`          = 'Personal Cash', `updatedAt` = $now WHERE `name`          = 'Cash in Hand'")
        db.execSQL("UPDATE `transactions`           SET `fromAcct`      = 'Personal Cash', `updatedAt` = $now WHERE `fromAcct`      = 'Cash in Hand'")
        db.execSQL("UPDATE `transactions`           SET `toAcct`        = 'Personal Cash', `updatedAt` = $now WHERE `toAcct`        = 'Cash in Hand'")
        db.execSQL("UPDATE `borrowers`              SET `sourceAccount` = 'Personal Cash', `updatedAt` = $now WHERE `sourceAccount` = 'Cash in Hand'")
        db.execSQL("UPDATE `recurring_transactions` SET `fromAcct`      = 'Personal Cash', `updatedAt` = $now WHERE `fromAcct`      = 'Cash in Hand'")
        db.execSQL("UPDATE `recurring_transactions` SET `toAcct`        = 'Personal Cash', `updatedAt` = $now WHERE `toAcct`        = 'Cash in Hand'")
    }
}

// 3.2.75 — Account-name rename: "Cash in Hand" → "Personal Cash".
//   Account names are used as string keys for DAO balance lookups
//   (`WHERE name = :name`), so we have to update every column that
//   references "Cash in Hand" in lockstep — otherwise transactions
//   orphan from their account (the 3.2.59 bug pattern).
//
//   Updated columns:
//     - accounts.name
//     - transactions.fromAcct + toAcct
//     - borrowers.sourceAccount
//     - recurring_transactions.fromAcct + toAcct
//
//   No-op for users who already renamed their cash account or don't
//   have one named "Cash in Hand". Idempotent (running twice has no
//   effect because the second pass finds no rows matching the old name).
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `accounts`               SET `name`          = 'Personal Cash' WHERE `name`          = 'Cash in Hand'")
        db.execSQL("UPDATE `transactions`           SET `fromAcct`      = 'Personal Cash' WHERE `fromAcct`      = 'Cash in Hand'")
        db.execSQL("UPDATE `transactions`           SET `toAcct`        = 'Personal Cash' WHERE `toAcct`        = 'Cash in Hand'")
        db.execSQL("UPDATE `borrowers`              SET `sourceAccount` = 'Personal Cash' WHERE `sourceAccount` = 'Cash in Hand'")
        db.execSQL("UPDATE `recurring_transactions` SET `fromAcct`      = 'Personal Cash' WHERE `fromAcct`      = 'Cash in Hand'")
        db.execSQL("UPDATE `recurring_transactions` SET `toAcct`        = 'Personal Cash' WHERE `toAcct`        = 'Cash in Hand'")
    }
}

// C22 (3.2.57) — Per-budget warning threshold.
//   Adds `alertThresholdPct` to the `budgets` table (INTEGER, default 80).
//   Pre-3.2.57 the BudgetCard hardcoded a 0.8 ratio for the NEAR LIMIT
//   state; backfilling all existing rows to 80 preserves that behaviour
//   exactly. Non-breaking; no data motion beyond the default.
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `budgets` ADD COLUMN `alertThresholdPct` INTEGER NOT NULL DEFAULT 80")
    }
}

// C22 (3.2.56) — Projects description.
//   Adds a single column to the `projects` table:
//     - `description` (TEXT, default "") — optional one-line subtitle on the
//       project card. Existing projects backfill to empty.
//   Non-breaking; the column was missing entirely before v19.
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `projects` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
    }
}

// C22 (3.2.55) — Goals icon picker + account link.
// Adds two new columns to the `goals` table:
//   - `iconKey` (TEXT, default "star") — stable icon sentinel rendered by
//     GoalIcons.iconFor(). All existing rows backfill to "star" so pre-3.2.55
//     goals look identical after migration.
//   - `linkedAccount` (TEXT, default "") — optional account name. Empty means
//     "not linked". Goals created via legacy versions stay unlinked.
// Non-breaking: both columns NOT NULL with sensible defaults; no data motion.
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `goals` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'star'")
        db.execSQL("ALTER TABLE `goals` ADD COLUMN `linkedAccount` TEXT NOT NULL DEFAULT ''")
    }
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

// C03a Stage 2 — additive schema fields + legacy category cleanup
// (UX_AUDIT §C03 Stage 3a items #2, #3, #5).
//
// The v14→v15 migration added `createdAt` to the 10 main tables, but four
// auxiliary entities created in earlier migrations were missed:
//   - flow_templates       (v4→v5)
//   - investment_valuations (v7→v8)
//   - net_worth_snapshots  (v6→v7)
//   - recurring_transactions (v5→v6)
// This migration adds `createdAt` to all four. For the three that have an
// `updatedAt` column, `createdAt` is backfilled from it (best-available
// proxy for "when created"). `net_worth_snapshots` has no `updatedAt`, so
// we backfill from the `date` field instead (parsed as `yyyy-MM-dd` via
// SQLite's `strftime`, multiplied to ms).
//
// Item #3: `InvestmentValuation` was the only scoped entity missing
// `projectId`. Added with default `'personal'`; backfilled from the
// parent investment's `projectId` so multi-project users keep their
// valuations correctly scoped.
//
// Item #5: a one-shot cleanup of historical transactions whose `category`
// is the literal `'Expense'` / `'Income'` / `'Transfer'` (a UX bug —
// those are TYPES, not categories — surfaced in the C03 audit). Repointed
// to `'Uncategorized'`. The runtime guard in `TransactionValidator`
// prevents this state from being re-introduced.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── createdAt on the four missed entities ───────────────────────
        db.execSQL("ALTER TABLE flow_templates ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE flow_templates SET createdAt = updatedAt WHERE createdAt = 0")

        db.execSQL("ALTER TABLE investment_valuations ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE investment_valuations SET createdAt = updatedAt WHERE createdAt = 0")

        db.execSQL("ALTER TABLE net_worth_snapshots ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        // net_worth_snapshots has no `updatedAt`; backfill from `date` (yyyy-MM-dd).
        db.execSQL("""
            UPDATE net_worth_snapshots
            SET createdAt = CAST(strftime('%s', date) AS INTEGER) * 1000
            WHERE createdAt = 0
        """.trimIndent())

        db.execSQL("ALTER TABLE recurring_transactions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE recurring_transactions SET createdAt = updatedAt WHERE createdAt = 0")

        // ── projectId on InvestmentValuation ────────────────────────────
        db.execSQL("ALTER TABLE investment_valuations ADD COLUMN projectId TEXT NOT NULL DEFAULT 'personal'")
        // Backfill from the parent investment so multi-project users keep scoping.
        db.execSQL("""
            UPDATE investment_valuations
            SET projectId = COALESCE(
                (SELECT projectId FROM investments WHERE investments.id = investment_valuations.investmentId),
                'personal'
            )
        """.trimIndent())

        // ── C03a item #5: clean up legacy "Expense"/"Income"/"Transfer" category literals ──
        // These were never valid categories — the dropdown bug let users pick
        // them, but Expense/Income/Transfer are TYPES. Repoint to a sentinel
        // so users can pick a real category on next edit. The TransactionValidator
        // guard prevents re-introduction at insert/edit time.
        db.execSQL("""
            UPDATE transactions
            SET category = 'Uncategorized'
            WHERE category IN ('Expense', 'Income', 'Transfer')
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
