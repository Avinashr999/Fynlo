package app.fynlo.logic

import app.fynlo.data.model.*
import java.time.LocalDate
import java.util.UUID

/**
 * Inserts comprehensive dummy data for testing all calculations.
 * Run from Settings screen → "Load Test Data"
 */
object DummyDataSeeder {

    private fun id() = UUID.randomUUID().toString()
    private val today = LocalDate.now()
    private fun daysAgo(n: Long) = today.minusDays(n).toString()

    fun accounts() = listOf(
        Account(id = "acc-cash",    name = "Cash in Hand", balance = 15000.0,  type = "Cash"),
        Account(id = "acc-hdfc",    name = "HDFC Bank",    balance = 85000.0,  type = "Bank"),
        Account(id = "acc-sbi",     name = "SBI Savings",  balance = 35000.0,  type = "Bank"),
        Account(id = "acc-petty",   name = "Petty Cash",   balance = 5000.0,   type = "Cash"),
    )

    fun borrowers() = listOf(
        Borrower(id = id(), name = "Ravi Kumar",   phone = "9876543210",
            amount = 50000.0, rate = 12.0, paid = 10000.0,
            date = daysAgo(400), due = "", type = "Simple Interest",
            notes = "For business expansion", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Borrower(id = id(), name = "Suresh Babu",  phone = "9123456789",
            amount = 25000.0, rate = 18.0, paid = 0.0,
            date = daysAgo(180), due = "", type = "Compound Interest",
            notes = "Medical emergency", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Borrower(id = id(), name = "Lakshmi Devi", phone = "9988776655",
            amount = 15000.0, rate = 10.0, paid = 2000.0,
            date = daysAgo(60), due = "", type = "Reducing Balance",
            notes = "House repairs", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Borrower(id = id(), name = "Mohan Rao",    phone = "9112233445",
            amount = 30000.0, rate = 15.0, paid = 5000.0,
            date = daysAgo(730), due = daysAgo(365), type = "Both",
            notes = "Old loan — overdue", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
    )

    fun debts() = listOf(
        Debt(id = id(), name = "Home Loan EMI",
            amount = 200000.0, rate = 8.5, paid = 30000.0,
            intType = "Reducing Balance",
            date = daysAgo(300), due = daysAgo(-1800),
            notes = "HDFC Home Loan", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Debt(id = id(), name = "Personal Loan",
            amount = 50000.0, rate = 14.0, paid = 5000.0,
            intType = "Simple Interest",
            date = daysAgo(90), due = daysAgo(-270),
            notes = "Bajaj Finance", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Debt(id = id(), name = "Gold Loan",
            amount = 20000.0, rate = 12.0, paid = 2000.0,
            intType = "Both",
            date = daysAgo(500), due = daysAgo(135),
            notes = "SBI Gold Loan — overdue", status = "Active",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
    )

    fun investments() = listOf(
        Investment(id = id(), name = "Gold ETF",        type = "Gold",
            invested = 100000.0, currentVal = 125000.0,
            date = daysAgo(365), notes = "Sovereign Gold Bond",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Investment(id = id(), name = "LIC Policy",      type = "Insurance",
            invested = 50000.0, currentVal = 55000.0,
            date = daysAgo(730), notes = "LIC Endowment Plan",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Investment(id = id(), name = "FD - HDFC",       type = "FD",
            invested = 50000.0, currentVal = 57500.0,
            date = daysAgo(548), notes = "18 month FD @8%",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Investment(id = id(), name = "Mutual Fund SIP", type = "Stocks",
            invested = 10000.0, currentVal = 8500.0,
            date = daysAgo(90), notes = "HDFC Flexi Cap",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
    )

    fun transactions() = listOf(
        Transaction(id = id(), date = daysAgo(30), type = "Income",
            amount = 50000.0, toAcct = "HDFC Bank", fromAcct = "",
            category = "Salary", desc = "Monthly Salary", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(28), type = "Expense",
            amount = 5000.0, fromAcct = "HDFC Bank", toAcct = "",
            category = "Food", desc = "Grocery & Dining", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(25), type = "Expense",
            amount = 3000.0, fromAcct = "Cash in Hand", toAcct = "",
            category = "Fuel", desc = "Petrol & Diesel", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(20), type = "Transfer",
            amount = 10000.0, fromAcct = "HDFC Bank", toAcct = "Cash in Hand",
            category = "Transfer", desc = "ATM Withdrawal", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(15), type = "Expense",
            amount = 8000.0, fromAcct = "SBI Savings", toAcct = "",
            category = "Shopping", desc = "Online Shopping", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(10), type = "Income",
            amount = 15000.0, toAcct = "SBI Savings", fromAcct = "",
            category = "Freelance", desc = "Web Design Project", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(5), type = "Expense",
            amount = 2000.0, fromAcct = "Petty Cash", toAcct = "",
            category = "Expense", desc = "Miscellaneous", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(3), type = "Expense",
            amount = 1500.0, fromAcct = "Cash in Hand", toAcct = "",
            category = "Fuel", desc = "Auto fuel", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
        Transaction(id = id(), date = daysAgo(1), type = "Income",
            amount = 5000.0, toAcct = "Cash in Hand", fromAcct = "",
            category = "Salary", desc = "Part time work", notes = "",
            projectId = "personal", updatedAt = System.currentTimeMillis()),
    )

    fun budgets() = listOf(
        Budget(category = "Food",     limitAmount = 8000.0,  period = "Monthly", projectId = "personal"),
        Budget(category = "Fuel",     limitAmount = 5000.0,  period = "Monthly", projectId = "personal"),
        Budget(category = "Shopping", limitAmount = 10000.0, period = "Monthly", projectId = "personal"),
        Budget(category = "Expense",  limitAmount = 3000.0,  period = "Monthly", projectId = "personal"),
    )
}
