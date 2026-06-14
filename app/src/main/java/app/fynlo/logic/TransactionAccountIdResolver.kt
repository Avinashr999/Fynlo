package app.fynlo.logic

import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction

/**
 * C03b Stage #1a (3.2.86) — pure resolver that mirrors a transaction's
 * `fromAcct` / `toAcct` *name* fields into the additive `fromAcctId` /
 * `toAcctId` *id* fields. Stage #1b will flip reads to consume the ids and
 * drop the names.
 *
 * Why a top-level helper:
 *   - The resolver runs in multiple write paths (FinanceRepository's manual
 *     CRUD, RecurringWorker's auto-fire, BankStatementImport's CSV ingest,
 *     SyncManager pull-side reconciliation in Stage #1b). Centralising the
 *     decision rule means a future refinement (alias map, fuzzy match,
 *     orphan-flag side-effect) lands in one place.
 *   - Pure function, no coroutines, no Android deps → fully testable in
 *     `TransactionAccountIdResolverDataIntegrityTest` without Robolectric.
 *
 * Decision rule (per field):
 *   1. If an id is already set, keep it (the persisted id is authoritative
 *      once the row has been resolved; re-resolving on every edit would
 *      mask deliberate account moves done outside the UI).
 *   2. If the name is blank, the id stays blank (income transactions have
 *      no `fromAcct`; expenses no `toAcct`; legacy bare rows have neither).
 *   3. Otherwise look up the name → return whatever `idByName` returns;
 *      orphans (no matching account) leave the id as `""`. The orphan
 *      surfaces to `OrphanTransactionsScanner` exactly as it would have
 *      before this stage shipped.
 *
 * Idempotent — calling twice with the same lookup returns an equal
 * Transaction. The `==` short-circuit at the return prevents unnecessary
 * allocations when nothing changes.
 */
fun Transaction.resolveAccountIdsWith(idByName: (String) -> String?): Transaction {
    val resolvedFrom = when {
        fromAcctId.isNotEmpty() -> fromAcctId
        fromAcct.isEmpty()      -> ""
        else                    -> idByName(fromAcct).orEmpty()
    }
    val resolvedTo = when {
        toAcctId.isNotEmpty()   -> toAcctId
        toAcct.isEmpty()        -> ""
        else                    -> idByName(toAcct).orEmpty()
    }
    return if (resolvedFrom == fromAcctId && resolvedTo == toAcctId) this
           else copy(fromAcctId = resolvedFrom, toAcctId = resolvedTo)
}

/**
 * C03b Stage #1c (3.2.89) — sibling resolver for `RecurringTransaction`.
 * Same decision rule as the [Transaction] variant: id wins when set,
 * resolves via lookup when not, falls back to empty (orphan) when the
 * name doesn't match. Lets `RecurringWorker` fire each month with
 * id-keyed balance updates regardless of any account renames the user
 * has done since the template was created.
 */
fun RecurringTransaction.resolveAccountIdsWith(idByName: (String) -> String?): RecurringTransaction {
    val resolvedFrom = when {
        fromAcctId.isNotEmpty() -> fromAcctId
        fromAcct.isEmpty()      -> ""
        else                    -> idByName(fromAcct).orEmpty()
    }
    val resolvedTo = when {
        toAcctId.isNotEmpty()   -> toAcctId
        toAcct.isEmpty()        -> ""
        else                    -> idByName(toAcct).orEmpty()
    }
    return if (resolvedFrom == fromAcctId && resolvedTo == toAcctId) this
           else copy(fromAcctId = resolvedFrom, toAcctId = resolvedTo)
}
