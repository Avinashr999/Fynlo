package app.fynlo.logic

import app.fynlo.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C03b Stage #1b-2 (3.2.88) — display-resolver gate. Locks in the
 * "id wins, name falls back" contract that drives the screen-side
 * flip in TransactionHistoryScreen, SpendScreen,
 * AccountStatementScreen, and MoneyFlowScreen.
 *
 * The two display extensions and the filter predicate share the same
 * decision rule but flip it: display *reads* the current name (rename
 * reflected); the predicate *matches* on the immutable id (rename-safe
 * filter).
 */
class TransactionAccountDisplayDataIntegrityTest {

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

    private val idToName = mapOf(
        "acc-hdfc" to "HDFC Salary",       // freshly renamed from "HDFC Bank"
        "acc-cash" to "Personal Cash",
    )

    // ── displayFromAcct / displayToAcct ──────────────────────────────────

    @Test
    fun `display returns the current name when id resolves`() {
        // Transaction was written when the account was still called "HDFC Bank";
        // the account has since been renamed to "HDFC Salary".
        val t = txn(from = "HDFC Bank", fromId = "acc-hdfc")
        assertEquals(
            "Rename must reflect immediately in the list; the stored name is " +
                "now stale and the resolved one is authoritative.",
            "HDFC Salary", t.displayFromAcct(idToName),
        )
    }

    @Test
    fun `display falls back to stored name when id is empty (legacy or orphan row)`() {
        val t = txn(from = "HDFC Bank", fromId = "")
        assertEquals(
            "Pre-Stage-1a rows have no id mirror; the stored name is the only display anchor.",
            "HDFC Bank", t.displayFromAcct(idToName),
        )
    }

    @Test
    fun `display falls back to stored name when id is set but lookup misses`() {
        // Account was hard-deleted while a transaction still references it
        // (the resolver doesn't run on Account.delete; that's by design — the
        // 3.2.59 orphan-repair tool owns these rows).
        val t = txn(from = "Closed Card", fromId = "acc-vanished")
        assertEquals(
            "An id whose account no longer exists must display the stored " +
                "name so the row stays legible until the user runs orphan repair.",
            "Closed Card", t.displayFromAcct(idToName),
        )
    }

    @Test
    fun `display returns blank when both fromAcct and fromAcctId are blank (income row)`() {
        val t = txn(to = "HDFC Bank", toId = "acc-hdfc")
        assertEquals("", t.displayFromAcct(idToName))
        assertEquals("HDFC Salary", t.displayToAcct(idToName))
    }

    @Test
    fun `display handles transfer rows on both sides independently`() {
        val t = txn(
            from = "HDFC Bank", fromId = "acc-hdfc",
            to = "Cash in Hand", toId = "acc-cash",
        )
        assertEquals("HDFC Salary",   t.displayFromAcct(idToName))
        assertEquals("Personal Cash", t.displayToAcct(idToName))
    }

    @Test
    fun `display map is empty — every row falls through to stored name`() {
        val empty = emptyMap<String, String>()
        val t = txn(from = "HDFC Bank", fromId = "acc-hdfc")
        assertEquals(
            "Pre-render — accounts not yet loaded. Stored name must still display.",
            "HDFC Bank", t.displayFromAcct(empty),
        )
    }

    // ── matchesAccount ──────────────────────────────────────────────────

    @Test
    fun `matchesAccount via id — rename-safe filter`() {
        // User opens AccountStatementScreen for the *renamed* account.
        // The screen is keyed on the new name and the immutable id.
        val t = txn(from = "HDFC Bank", fromId = "acc-hdfc")
        assertTrue(
            "Transaction was recorded under 'HDFC Bank'; the account is now " +
                "'HDFC Salary'. Filter by id must still include this row.",
            t.matchesAccount(accountId = "acc-hdfc", accountName = "HDFC Salary"),
        )
    }

    @Test
    fun `matchesAccount via name — legacy orphan row still surfaces`() {
        // Pre-Stage-1a row whose id mirror is empty.
        val t = txn(from = "HDFC Bank", fromId = "")
        assertTrue(
            "Legacy row with empty id matches via stored-name fallback.",
            t.matchesAccount(accountId = "acc-hdfc", accountName = "HDFC Bank"),
        )
    }

    @Test
    fun `matchesAccount — id is set but doesn't match the target account, name happens to collide`() {
        // Pathological edge: user has TWO accounts with similar names.
        // Transaction's id points at account B but its stored name happens
        // to match account A's name (e.g., A was renamed FROM the same
        // string). Filter by A must NOT include this row — id is authoritative.
        val t = txn(from = "HDFC", fromId = "acc-other")
        assertFalse(
            "Id is the authoritative key; coincidental name collision " +
                "must not pull the row into the wrong account's statement.",
            t.matchesAccount(accountId = "acc-hdfc", accountName = "HDFC"),
        )
    }

    @Test
    fun `matchesAccount returns false when neither side matches`() {
        val t = txn(from = "Cash", fromId = "acc-cash")
        assertFalse(t.matchesAccount(accountId = "acc-hdfc", accountName = "HDFC Salary"))
    }

    @Test
    fun `matchesAccount works for toAcct side (income or transfer in)`() {
        val t = txn(to = "HDFC Bank", toId = "acc-hdfc")
        assertTrue(t.matchesAccount(accountId = "acc-hdfc", accountName = "HDFC Salary"))
    }

    @Test
    fun `matchesAccount works for transfer rows where the target is the toAcct side`() {
        val t = txn(
            from = "Cash",      fromId = "acc-cash",
            to   = "HDFC Bank", toId   = "acc-hdfc",
        )
        assertTrue(t.matchesAccount(accountId = "acc-hdfc", accountName = "HDFC Salary"))
    }

    @Test
    fun `matchesAccount with empty accountId disables id-side match`() {
        val t = txn(from = "HDFC Bank", fromId = "acc-hdfc")
        // No id supplied → only name fallback applies. Row's id is set, so
        // the fallback short-circuits and returns false.
        assertFalse(
            "Caller passed empty id; row's id is set so the name fallback " +
                "is suppressed (avoids the coincidental-collision pitfall).",
            t.matchesAccount(accountId = "", accountName = "HDFC Bank"),
        )
    }
}
