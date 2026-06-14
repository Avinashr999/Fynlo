package app.fynlo.logic

import app.fynlo.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 3.2.59 — OrphanTransactionsScanner unit tests. Pure helper, no Android
 * deps; runs in the regular `*DataIntegrity*` filter.
 */
class OrphanTransactionsScannerDataIntegrityTest {

    private fun expense(id: String, from: String, amount: Double = 100.0) = Transaction(
        id = id, date = "01-01-2026", type = "Expense", amount = amount,
        fromAcct = from, category = "Food",
    )

    private fun income(id: String, to: String, amount: Double = 100.0) = Transaction(
        id = id, date = "01-01-2026", type = "Income", amount = amount,
        toAcct = to, category = "Salary",
    )

    private fun transfer(id: String, from: String, to: String) = Transaction(
        id = id, date = "01-01-2026", type = "Transfer", amount = 100.0,
        fromAcct = from, toAcct = to, category = "Transfer",
    )

    @Test
    fun expenseWithMatchingAccountIsNotOrphan() {
        val result = OrphanTransactionsScanner.scan(
            listOf(expense("t1", "HDFC Bank")),
            listOf("HDFC Bank"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun expenseWithUnknownAccountIsOrphan() {
        // The exact bug the user hit: typed "hdfc" while account is "HDFC Bank".
        val result = OrphanTransactionsScanner.scan(
            listOf(expense("t1", "hdfc", amount = 24000.0)),
            listOf("HDFC Bank"),
        )
        assertEquals(1, result.size)
        assertEquals("hdfc", result[0].typedName)
        assertEquals(OrphanTransactionsScanner.Side.FROM, result[0].side)
        assertEquals(24000.0, result[0].transaction.amount, 0.0)
    }

    @Test
    fun caseInsensitiveMatchPreventsFalsePositive() {
        // The DAO query is case-sensitive (SQLite default for TEXT) so an
        // actual lowercase typed value still breaks balances — but the
        // scanner reports based on logical equality, not the SQL semantics.
        // We don't want to flag "HDFC" as orphan when an account "hdfc"
        // exists; the canonical column is the source of truth.
        val result = OrphanTransactionsScanner.scan(
            listOf(expense("t1", "HDFC")),
            listOf("hdfc"),
        )
        assertTrue("Case-insensitive equality should clear the orphan flag.", result.isEmpty())
    }

    @Test
    fun trimWhitespaceMatchPreventsFalsePositive() {
        val result = OrphanTransactionsScanner.scan(
            listOf(expense("t1", " HDFC Bank ")),
            listOf("HDFC Bank"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun incomeWithUnknownToAccountIsOrphan() {
        val result = OrphanTransactionsScanner.scan(
            listOf(income("t1", "icic")),
            listOf("ICICI Bank"),
        )
        assertEquals(1, result.size)
        assertEquals(OrphanTransactionsScanner.Side.TO, result[0].side)
    }

    @Test
    fun transferWithBothSidesUnknownReturnsTwoOrphans() {
        // Same transaction id, both sides invalid → two distinct rows in
        // the orphan list so the UI can let the user fix each side
        // independently.
        val result = OrphanTransactionsScanner.scan(
            listOf(transfer("t1", "src-bad", "dst-bad")),
            listOf("HDFC Bank", "ICICI Bank"),
        )
        assertEquals(2, result.size)
        assertTrue(result.any { it.side == OrphanTransactionsScanner.Side.FROM })
        assertTrue(result.any { it.side == OrphanTransactionsScanner.Side.TO })
    }

    @Test
    fun transferWithOneSideValidReturnsOneOrphan() {
        val result = OrphanTransactionsScanner.scan(
            listOf(transfer("t1", "HDFC Bank", "dst-bad")),
            listOf("HDFC Bank"),
        )
        assertEquals(1, result.size)
        assertEquals("dst-bad", result[0].typedName)
        assertEquals(OrphanTransactionsScanner.Side.TO, result[0].side)
    }

    @Test
    fun blankAccountFieldIsSkipped() {
        // Income with blank fromAcct is structurally normal (income side
        // is toAcct). Don't flag the blank field.
        val result = OrphanTransactionsScanner.scan(
            listOf(income("t1", "HDFC Bank")),
            listOf("HDFC Bank"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyAccountListMakesAllAccountedTransactionsOrphan() {
        // Defensive: fresh install with no accounts yet; any transaction
        // that names one is orphan by definition.
        val result = OrphanTransactionsScanner.scan(
            listOf(expense("t1", "HDFC"), income("t2", "ICICI")),
            emptyList(),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun unknownTransactionTypeIsSkipped() {
        // Defensive: legacy / corrupt rows with unexpected `type` values
        // shouldn't crash the scanner — just skip them. They'll surface
        // via the C03a TransactionValidator gate instead.
        val weird = Transaction(
            id = "t1", date = "01-01-2026", type = "Unknown", amount = 100.0,
            fromAcct = "Anywhere", category = "Misc",
        )
        val result = OrphanTransactionsScanner.scan(listOf(weird), listOf("HDFC"))
        assertTrue(result.isEmpty())
    }
}
