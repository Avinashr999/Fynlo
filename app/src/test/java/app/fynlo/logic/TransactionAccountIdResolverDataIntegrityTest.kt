package app.fynlo.logic

import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * C03b Stage #1a (3.2.86) — pure-function gate for the
 * `Transaction.resolveAccountIdsWith` resolver. Locks in the decision rule
 * surfaced in the helper's KDoc so future refactors (alias map, fuzzy
 * match, etc.) can't regress the four invariants the caller layer
 * depends on:
 *
 *   1. Already-populated ids survive untouched (no re-resolve on edit).
 *   2. Blank names produce blank ids (income has no `fromAcct`; expense
 *      no `toAcct`; legacy bare rows have neither).
 *   3. Resolvable names get their matching id.
 *   4. Orphan names (no match) leave the id as `""` — the migration and
 *      the resolver must both surface orphans to `OrphanTransactionsScanner`
 *      instead of inventing an id.
 *
 * The fifth case — idempotence — is covered by the `assertSame` checks:
 * when no field changes, the resolver must return `this` rather than a
 * fresh `copy(...)`. This is a defensive optimization but the assertion
 * also documents the contract: callers can use referential equality to
 * decide whether to re-persist.
 *
 * Matches the `*DataIntegrity*` Gradle filter — runs in the same gate as
 * `RecalculateBalancesDataIntegrityTest` and friends.
 */
class TransactionAccountIdResolverDataIntegrityTest {

    private fun txn(
        from: String = "",
        to: String = "",
        fromId: String = "",
        toId: String = "",
    ) = Transaction(
        id = "t-1",
        date = "2026-05-28",
        type = if (from.isNotEmpty() && to.isNotEmpty()) "Transfer"
               else if (from.isNotEmpty()) "Expense" else "Income",
        amount = 100.0,
        fromAcct = from,
        toAcct = to,
        fromAcctId = fromId,
        toAcctId = toId,
        category = "Food",
    )

    /** Standard lookup: "Cash" → "acc-cash", "HDFC" → "acc-hdfc", everything else → null. */
    private val lookup = { name: String ->
        when (name) {
            "Cash" -> "acc-cash"
            "HDFC" -> "acc-hdfc"
            else   -> null
        }
    }

    @Test
    fun `resolvable expense populates fromAcctId from name lookup`() {
        val out = txn(from = "Cash").resolveAccountIdsWith(lookup)
        assertEquals("acc-cash", out.fromAcctId)
        assertEquals("", out.toAcctId)
    }

    @Test
    fun `resolvable income populates toAcctId from name lookup`() {
        val out = txn(to = "HDFC").resolveAccountIdsWith(lookup)
        assertEquals("", out.fromAcctId)
        assertEquals("acc-hdfc", out.toAcctId)
    }

    @Test
    fun `resolvable transfer populates both ids in lockstep`() {
        val out = txn(from = "HDFC", to = "Cash").resolveAccountIdsWith(lookup)
        assertEquals("acc-hdfc", out.fromAcctId)
        assertEquals("acc-cash", out.toAcctId)
    }

    @Test
    fun `orphan name leaves id as empty string`() {
        val out = txn(from = "Vanished Account").resolveAccountIdsWith(lookup)
        assertEquals(
            "Orphan must surface as empty id; the resolver must NOT invent an id, " +
                "so OrphanTransactionsScanner can still flag it for repair.",
            "", out.fromAcctId,
        )
    }

    @Test
    fun `blank fromAcct stays as blank fromAcctId`() {
        val out = txn(to = "HDFC").resolveAccountIdsWith(lookup)
        assertEquals("", out.fromAcctId)
    }

    @Test
    fun `blank toAcct stays as blank toAcctId`() {
        val out = txn(from = "Cash").resolveAccountIdsWith(lookup)
        assertEquals("", out.toAcctId)
    }

    @Test
    fun `existing populated id is never overwritten by the resolver`() {
        // Simulates an edit: the row was resolved earlier (fromAcctId already set
        // to acc-old-cash), then the user typed a different name in the UI. The
        // resolver must respect the persisted id — only Stage #1b's rename UI
        // is allowed to mutate the id deliberately.
        val out = txn(from = "Cash", fromId = "acc-old-cash").resolveAccountIdsWith(lookup)
        assertEquals(
            "Already-populated id must survive a fresh resolve. Re-resolving on " +
                "every edit would mask deliberate account moves done outside the UI.",
            "acc-old-cash", out.fromAcctId,
        )
    }

    @Test
    fun `idempotent — already-resolved row returns the same instance`() {
        val resolved = txn(from = "Cash", to = "HDFC").resolveAccountIdsWith(lookup)
        val second = resolved.resolveAccountIdsWith(lookup)
        assertSame(
            "Calling the resolver on an already-resolved row must return `this` " +
                "(referential equality) — callers use this to skip persistence " +
                "when nothing changed.",
            resolved, second,
        )
    }

    @Test
    fun `idempotent — fully blank row returns the same instance`() {
        // A bare row with neither name set (legacy, or income with empty toAcct
        // for some reason) must round-trip without an unnecessary copy().
        val bare = txn()
        val out = bare.resolveAccountIdsWith(lookup)
        assertSame(bare, out)
    }

    @Test
    fun `lookup is only called for non-blank names — never invoked with empty string`() {
        var calls = 0
        val spy: (String) -> String? = { name ->
            calls++
            check(name.isNotEmpty()) { "Resolver must skip the lookup for blank names." }
            lookup(name)
        }
        // Income with toAcct only — lookup runs once (toAcct), not twice.
        txn(to = "Cash").resolveAccountIdsWith(spy)
        assertEquals(1, calls)

        // Bare row — lookup never runs.
        calls = 0
        txn().resolveAccountIdsWith(spy)
        assertEquals(0, calls)
    }

    @Test
    fun `partial resolve — only the unresolved side gets a fresh lookup`() {
        // fromAcctId is already set; toAcctId is empty and resolvable.
        // The resolver should only touch the toAcctId side.
        val out = txn(from = "Cash", to = "HDFC", fromId = "acc-cash-keep")
            .resolveAccountIdsWith(lookup)
        assertEquals(
            "Pre-set fromAcctId must survive verbatim while the toAcctId side fills in.",
            "acc-cash-keep", out.fromAcctId,
        )
        assertEquals("acc-hdfc", out.toAcctId)
    }

    // ── C03b Stage #1c (3.2.89) — RecurringTransaction sibling resolver ──
    //
    // Same decision rule applied to the recurring template entity. The
    // worker reads back the stored id every time it auto-fires, so
    // populating it correctly on the write path is the whole job.

    private fun recurring(
        from: String = "",
        to: String = "",
        fromId: String = "",
        toId: String = "",
    ) = RecurringTransaction(
        id = "r-1",
        name = "Monthly Rent",
        type = if (from.isNotEmpty() && to.isNotEmpty()) "Transfer"
               else if (from.isNotEmpty()) "Expense" else "Income",
        amount = 10000.0,
        category = "Rent",
        fromAcct = from,
        toAcct = to,
        fromAcctId = fromId,
        toAcctId = toId,
    )

    @Test
    fun `recurring — resolvable expense template populates fromAcctId`() {
        val out = recurring(from = "Cash").resolveAccountIdsWith(lookup)
        assertEquals("acc-cash", out.fromAcctId)
        assertEquals("", out.toAcctId)
    }

    @Test
    fun `recurring — already-resolved template is referentially equal`() {
        val r = recurring(from = "Cash", fromId = "acc-cash")
        assertSame(r, r.resolveAccountIdsWith(lookup))
    }

    @Test
    fun `recurring — orphan template surfaces with empty id (worker falls back to name)`() {
        val out = recurring(from = "Vanished Card").resolveAccountIdsWith(lookup)
        assertEquals(
            "Orphan templates must keep id empty so RecurringWorker can " +
                "route through the legacy name-keyed UPDATE for them.",
            "", out.fromAcctId,
        )
    }

    @Test
    fun `recurring — populated id survives, the user-renamed-then-re-edited safety case`() {
        // Template was created with fromAcct="HDFC Bank" → fromAcctId=acc-hdfc.
        // User then renamed the account to "HDFC Salary" but the template's
        // stored fromAcct still says "HDFC Bank" because RecurringScreen
        // hasn't been re-saved. The resolver MUST keep the original id —
        // re-resolving the name would either fail (no current account
        // named "HDFC Bank") or, worse, resolve to a different account
        // if the user created a new "HDFC Bank" since the rename.
        val stale = recurring(from = "HDFC Bank", fromId = "acc-hdfc")
        val resolved = stale.resolveAccountIdsWith { name ->
            // Name lookup would NOT find the stale "HDFC Bank" — but we
            // never get here because the id short-circuits the resolver.
            when (name) {
                "HDFC Salary"   -> "acc-hdfc"
                "HDFC Bank"     -> null
                else            -> null
            }
        }
        assertEquals(
            "Stored id is authoritative — survives stale stored names.",
            "acc-hdfc", resolved.fromAcctId,
        )
    }
}
