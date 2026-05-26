package app.fynlo.data.model

import kotlinx.serialization.Serializable

/**
 * Backup JSON root. Encodes/decodes via kotlinx-serialization.
 *
 * ── Backup-format versioning (UX_AUDIT §C03 stage 3a — C03a) ───────────
 * `schemaVersion` is the **backup format** version, NOT Room's DB schema
 * version (which lives in `FynloDatabase` and is at v16 as of 2026-05-26).
 *
 *   v1 — pre-C03a; no metadata, no integrity hash. Legitimate legacy
 *        backups produced by 3.2.0 and earlier read with all metadata
 *        defaults (empty strings). Restore is accepted unconditionally
 *        for backwards compatibility.
 *   v2 — post-C03a; metadata (`appVersion`, `exportedAt`, `userId`,
 *        `deviceName`) is populated at export time, and a SHA-256
 *        `contentHash` is computed over the canonical JSON form
 *        (everything except the hash field itself, which is set to "" at
 *        compute time). Restore verifies the hash and refuses to load if
 *        it doesn't match.
 *
 * Forward-compat: a backup with `schemaVersion` > the current app's
 * understanding is rejected by [app.fynlo.data.BackupIntegrity.check] with
 * `UnsupportedVersion` — the user is asked to update the app.
 *
 * **Field order is load-bearing.** kotlinx-serialization encodes in
 * declaration order, and the canonical JSON form used for hash computation
 * depends on that order being stable. Don't reorder existing fields.
 * Append new fields at the bottom of either the metadata block or the
 * data block.
 */
@Serializable
data class BackupData(
    // ── Backup-format metadata (added in v2, C03a 2026-05-26) ────────────
    val schemaVersion: Int = 1,
    val appVersion: String   = "",
    val exportedAt: String   = "",    // ISO-8601 instant in UTC, e.g. "2026-05-26T22:30:00Z"
    val userId: String       = "",    // Firebase auth UID at export time; "" if signed out
    val deviceName: String   = "",    // android.os.Build.MODEL captured at export time
    /**
     * SHA-256 (lower-case hex) over the canonical JSON serialization of
     * this entire object with `contentHash` set to `""`. Empty in v1
     * backups and during hash computation; populated at the end of
     * `getAllDataAsJson()` for v2.
     */
    val contentHash: String  = "",

    // ── Data ─────────────────────────────────────────────────────────────
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val borrowers: List<Borrower> = emptyList(),
    val investments: List<Investment> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val people: List<Person> = emptyList(),
    val projects: List<Project> = emptyList(),              // added in v2.0
    // added in v3.1 — without these a restore loses payment history and
    // recomputes wrong loan/debt balances.
    val payments: List<Payment> = emptyList(),
    val debtPayments: List<DebtPayment> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
)
