package app.fynlo.logic

import app.fynlo.data.model.Transaction

/**
 * C03b Stage #1b-2 (3.2.88) — pure display-side resolution from
 * `fromAcctId` / `toAcctId` to the *current* `Account.name`. Mirrors
 * `Transaction.resolveAccountIdsWith` from Stage #1a but in the other
 * direction: id → current name.
 *
 * Why this exists:
 *   - Stage #1a stamped each transaction with the owning account's id.
 *   - Stage #1b-1 made the balance writes id-keyed so a rename can't
 *     orphan a balance update.
 *   - This stage makes the **display** id-keyed too: rename "HDFC Bank"
 *     → "HDFC Salary" and every historical transaction in the list
 *     shows the new name immediately. No recompute, no migration —
 *     the display layer just looks up the id in a precomputed map.
 *
 * Decision rule (per direction):
 *   1. If the transaction has a non-empty id AND the id is in the
 *      caller's `idToName` map → return the current name (rename
 *      reflected).
 *   2. Otherwise return the stored name. Catches legacy orphan rows
 *      (id = "" because the v22→v23 backfill couldn't resolve), the
 *      pre-Stage-1a writes that pre-date the id mirror entirely, and
 *      the "account was hard-deleted while a transaction still
 *      references it" edge case (id is set but the lookup misses).
 *
 * Callers are expected to build `idToName` once per render:
 *   ```
 *   val idToName = accounts.associate { it.id to it.name }
 *   ```
 *
 * Pure function, no Android deps → covered by
 * `TransactionAccountDisplayDataIntegrityTest`.
 */
fun Transaction.displayFromAcct(idToName: Map<String, String>): String =
    if (fromAcctId.isNotEmpty()) idToName[fromAcctId] ?: fromAcct
    else fromAcct

fun Transaction.displayToAcct(idToName: Map<String, String>): String =
    if (toAcctId.isNotEmpty()) idToName[toAcctId] ?: toAcct
    else toAcct

/**
 * C03b Stage #1b-2 (3.2.88) — id-aware filter predicate. Replaces the
 * "transaction.fromAcct == accountName" pattern used by AccountStatementScreen
 * and any future filter chips. Matches on the immutable id when both
 * sides have one; falls back to name equality for legacy rows whose id
 * mirror is empty (orphans, pre-Stage-1a writes).
 *
 * Pass `accountId = ""` to disable the id-side match (legacy-only).
 * Pass `accountName = ""` to disable the name-side match (id-only).
 */
fun Transaction.matchesAccount(accountId: String, accountName: String): Boolean {
    val idMatch = accountId.isNotEmpty() &&
        (fromAcctId == accountId || toAcctId == accountId)
    if (idMatch) return true
    // Fallback to name match for rows whose id mirror never got
    // populated (pre-Stage-1a writes, or v22→v23 backfill orphans).
    // Guarded on the id side being empty so a transaction whose id is
    // set but doesn't match the target account doesn't accidentally
    // match on a coincidental name collision.
    val nameMatch = accountName.isNotEmpty() &&
        ((fromAcctId.isEmpty() && fromAcct == accountName) ||
         (toAcctId.isEmpty()   && toAcct   == accountName))
    return nameMatch
}
