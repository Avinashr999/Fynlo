package app.fynlo.logic

import java.util.UUID

/**
 * C03b Stage #4 (3.2.91) — single centralized source of entity IDs.
 *
 * The audit's complaint in §C03b was "UUID format isn't standardised":
 *   - Most insert paths used `UUID.randomUUID().toString()` (39 inline
 *     call sites across 17 files at survey time) — RFC 4122 v4 strings.
 *   - PeopleScreen used a custom `"P-${timestamp}-${hashSuffix}"` scheme
 *     for new Person rows — non-UUID, vulnerable to collision under
 *     fast inserts (timestamp resolution is milliseconds).
 *   - `MIGRATION_24_25` used SQLite-native `'P-' || lower(hex(randomblob(8)))`
 *     for new Person rows it created during dedup — 16-char hex with a
 *     `P-` prefix, distinct from both above.
 *
 * This helper unifies the Kotlin side. Every entity create path —
 * Transaction, Borrower, Debt, Account, Investment, Goal, Project,
 * Person, RecurringTransaction, Payment, DebtPayment, FlowTemplate —
 * now calls `Ids.newId()` and gets back the same shape (RFC 4122 v4).
 *
 * The SQL migration's `'P-' || lower(hex(randomblob(8)))` is kept as-is
 * because it only fires once per user on the v24→v25 upgrade, and
 * SQLite has no built-in UUID generator. The resulting two-format
 * coexistence is bounded: only Person rows created by that one
 * migration carry the `P-` prefix; every Person row created by the
 * Kotlin path (PeopleScreen, FinanceRepository.findOrCreatePersonId)
 * is a UUID. Legacy `P-001`-style ids from pre-Stage-3 are also
 * untouched — they're opaque FK targets, renaming them would cascade.
 *
 * ## Load-bearing sentinels (never call `Ids.newId()` to generate these)
 *
 *   - `"personal"` — the default Project id. Every entity's
 *     `projectId` field defaults to this string. Hard-coded in
 *     migrations (e.g. `INSERT INTO projects VALUES ('personal', ...)`)
 *     and as the default scope when no project is explicitly chosen.
 *     Treat as an immutable constant of the schema.
 *
 * Pure helper, no Android deps → covered by `IdsDataIntegrityTest`.
 */
object Ids {

    /**
     * Returns a fresh RFC 4122 v4 UUID string (lowercase, hyphenated).
     * Example: `"f47ac10b-58cc-4372-a567-0e02b2c3d479"`.
     *
     * Use for every new entity insert path. Don't generate ids by any
     * other route — collision risk + format drift are exactly what
     * Stage #4 closes.
     */
    fun newId(): String = UUID.randomUUID().toString()
}
