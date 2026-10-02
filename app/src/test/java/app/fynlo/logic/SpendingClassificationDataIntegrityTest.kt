package app.fynlo.logic

import app.fynlo.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpendingClassificationDataIntegrityTest {
    private fun txn(
        category: String = "Food",
        amount: Double = 100.25,
        type: String = "Expense",
        date: String = "2026-09-26",
        tags: String = "",
    ) = Transaction(
        id = "$type-$category-$date", date = date, type = type, amount = amount,
        fromAcct = "Family Cash", fromAcctId = "family", toAcct = "", category = category,
        ref = "linked-record", tags = tags,
    )

    @Test
    fun `lending and investment funding are not monthly spending`() {
        val rows = listOf(
            txn("Lending", 2_960_000.0), txn("Investment", 100_000.0),
            txn("Bills", 11_881.0), txn("Food", 2_400.0), txn("Shopping", 83_860.25),
        )
        val expenses = rows.filter { it.isSpendingExpense() }
        assertEquals(listOf("Bills", "Food", "Shopping"), expenses.map { it.category })
        assertEquals(98_141.25, expenses.sumOf { it.amount }, 0.001)
        assertEquals(expenses, rows.filter { it.isOperatingCashEntry() && it.type == "Expense" })
    }

    @Test
    fun `all known financing categories excluded regardless of case or legacy missing links`() {
        listOf("Debt Received", "Debt Repayment", "Lending", "Loan Recovery",
            "Loan Repayment", "Investment", "Investment Returns", "Balance Correction", "Transfer", "Account Transfer")
            .forEach { category ->
                listOf("Expense", "Income").forEach { type ->
                    val row = txn(" ${category.lowercase()} ", type = type).copy(ref = "")
                    assertFalse(category, row.isOperatingCashEntry())
                    assertFalse(category, row.isSpendingExpense())
                }
            }
    }

    @Test
    fun `ordinary categories are preserved without guessing from names or links`() {
        listOf("Food", "Bills", "Investment research subscription", "to kalyani", "adjusted amount", "")
            .forEach { assertTrue(it, txn(it).isSpendingExpense()) }
        assertTrue(txn("Salary", type = "Income").isOperatingCashEntry())
        assertFalse(txn("Salary", type = "Income").isSpendingExpense())
        assertTrue(txn("Food", type = "eXpEnSe").isSpendingExpense())
    }

    @Test
    fun `transfers and info rows never count even with an ordinary category`() {
        listOf("Transfer", "Info", "").forEach { type ->
            assertFalse(txn(type = type).isOperatingCashEntry())
            assertFalse(txn(type = type).isSpendingExpense())
        }
    }

    @Test
    fun `journal copies do not duplicate cash expenses and standalone interest stays visible`() {
        val cash = txn("Interest Expense", 500.0)
        val journal = cash.copy(id = "journal", tags = "audit, JOURNAL_ONLY ,legacy")
        assertTrue(cash.isSpendingExpense())
        assertFalse(journal.isOperatingCashEntry())
        assertFalse(txn("Bad Debt", tags = "journal_only").isSpendingExpense())
        assertEquals(500.0, listOf(cash, journal).filter { it.isSpendingExpense() }.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `selected month comparisons and category sums use the same spending set`() {
        val rows = listOf(
            txn("Food", 10.25, date = "2026-09-01"), txn("Food", 20.50),
            txn("Lending", 1_000_000.0), txn("Investment", 100_000.0),
            txn("Food", 5.75, date = "2026-08-31"),
            txn("Lending", 500_000.0, date = "2026-08-01"),
        )
        val spending = rows.filter { it.isSpendingExpense() }
        val september = spending.filter { it.date.startsWith("2026-09") }
        val august = spending.filter { it.date.startsWith("2026-08") }
        assertEquals(30.75, september.sumOf { it.amount }, 0.001)
        assertEquals(5.75, august.sumOf { it.amount }, 0.001)
        assertEquals(mapOf("Food" to 30.75), september.groupBy { it.category }.mapValues { it.value.sumOf { row -> row.amount } })
    }

    @Test
    fun `report classification does not change ledger rows or account cash movement`() {
        val rows = listOf(txn("Lending", 100_000.0), txn("Investment", 50_000.0), txn("Bills", 25.75))
        val before = rows.map { it.copy() }
        repeat(3) { rows.filter { it.isSpendingExpense() }; rows.filter { it.isOperatingCashEntry() } }
        assertEquals(before, rows)
        assertEquals(3, rows.size)
        assertEquals(150_025.75, rows.sumOf { it.amount }, 0.001)
        assertEquals(49_974.25, 200_000.0 - rows.sumOf { it.amount }, 0.001)
        assertEquals(25.75, rows.filter { it.isSpendingExpense() }.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `spending category suggestions exclude financing and journals`() {
        val date = LocalDate.now().toString()
        val rows = listOf(txn("Lending", date = date), txn("Investment", date = date),
            txn("Bills", date = date), txn("Food", date = date, tags = "journal_only"))
        assertEquals(listOf("Bills"), FlowRuleEngine.topCategories(rows))
    }
}
