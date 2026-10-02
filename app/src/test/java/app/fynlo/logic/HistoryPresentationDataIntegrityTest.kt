package app.fynlo.logic

import app.fynlo.data.model.Account
import app.fynlo.data.model.Transaction
import app.fynlo.ui.screens.buildBalanceImpactsByTransaction
import org.junit.Assert.*
import org.junit.Test

class HistoryPresentationDataIntegrityTest {
    private fun row(id: String = "t", type: String = "Expense", amount: Double = 100.25) = Transaction(
        id = id, date = "2026-09-26", type = type, amount = amount,
        fromAcct = "Old bank", fromAcctId = "bank", category = "Bills",
    )

    @Test fun `cash totals retain financing but exclude journal and info amounts`() {
        val rows = listOf(row(amount = 1_000_000.0).copy(category = "Lending"),
            row("income", "Income", 500.0), row("transfer", "Transfer", 50.75),
            row("info", "Info", 1000.0), row("journal", amount = 200.0).copy(tags = "journal_only"))
        assertEquals(HistoryCashTotals(500.0, 1_000_000.0, 50.75), historyCashTotals(rows))
        assertEquals(5, rows.filter { HistoryFilter.ALL.matches(it) }.size)
    }

    @Test fun `filters distinguish transfers corrections and directional cash movement`() {
        val transfer = row(type = "Transfer")
        val correction = row().copy(category = "Balance Correction")
        assertTrue(HistoryFilter.TRANSFERS.matches(transfer))
        assertFalse(HistoryFilter.MONEY_OUT.matches(transfer))
        assertTrue(HistoryFilter.CORRECTIONS.matches(correction))
        assertTrue(HistoryFilter.MONEY_OUT.matches(correction))
        assertFalse(HistoryFilter.MONEY_OUT.matches(correction.copy(tags = "audit, JOURNAL_ONLY")))
    }

    @Test fun `search finds renamed source and destination accounts person notes and amount`() {
        val entry = row().copy(toAcct = "Old cash", toAcctId = "cash", person = "RB", notes = "August interest", desc = "Payment received")
        val names = mapOf("bank" to "HDFC", "cash" to "Family Cash")
        listOf(" hdfc ", "family cash", "rb", "August", "Payment", "100.25", "2026-09")
            .forEach { assertTrue(it, entry.matchesHistoryQuery(it, names)) }
        assertFalse(entry.matchesHistoryQuery("does not exist", names))
        assertTrue(entry.matchesHistoryQuery(" ", names))
        assertTrue(entry.matchesHistoryQuery("Old cash", emptyMap()))
    }

    @Test fun `journal only expense cannot change reconstructed before balances`() {
        val account = Account("bank", "HDFC", "Bank", 850.0)
        val payment = row(amount = 150.0).copy(category = "Debt Repayment", createdAt = 1L)
        val journal = row("interest", amount = 50.0).copy(tags = "journal_only", createdAt = 2L)
        val result = buildBalanceImpactsByTransaction(listOf(payment, journal), listOf(account))
        assertFalse(result.containsKey(journal.id))
        val impact = result.getValue(payment.id).single()
        assertEquals(1000.0, impact.before, 0.001)
        assertEquals(850.0, impact.after, 0.001)
        assertEquals(-150.0, impact.delta, 0.001)
        assertEquals(850.0, account.balance, 0.001)
    }

    @Test fun `transfer details contain separate exact before after balances for both accounts`() {
        val accounts = listOf(Account("bank", "HDFC", "Bank", 899.75), Account("cash", "Family Cash", "Cash", 300.25))
        val transfer = row(type = "Transfer").copy(toAcct = "Old cash", toAcctId = "cash")
        val impacts = buildBalanceImpactsByTransaction(listOf(transfer), accounts).getValue(transfer.id)
        assertEquals(2, impacts.size)
        val from = impacts.single { it.accountName == "HDFC" }
        val to = impacts.single { it.accountName == "Family Cash" }
        assertEquals(1000.0, from.before, 0.001)
        assertEquals(200.0, to.before, 0.001)
        assertEquals(0.0, impacts.sumOf { it.delta }, 0.001)
    }

    @Test fun `search and filters do not change amounts or ledger rows`() {
        val original = listOf(row(), row("two", "Transfer", 500.75))
        val before = original.map { it.copy() }
        HistoryFilter.entries.forEach { filter -> original.filter { filter.matches(it) && it.matchesHistoryQuery("bank", emptyMap()) } }
        historyCashTotals(original)
        assertEquals(before, original)
    }
}
