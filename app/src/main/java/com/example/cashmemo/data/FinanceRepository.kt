package com.example.cashmemo.data

import com.example.cashmemo.data.local.CashMemoDao
import com.example.cashmemo.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import com.example.cashmemo.data.local.CashMemoDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FinanceRepository(private val dao: CashMemoDao, private val db: CashMemoDatabase) {

    // ─── Raw flows (unfiltered — ViewModel applies project filter) ────────────
    val allBorrowers: Flow<List<Borrower>>       = dao.getAllBorrowers()
    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val allAccounts: Flow<List<Account>>         = dao.getAllAccounts()
    val allInvestments: Flow<List<Investment>>   = dao.getAllInvestments()
    val allDebts: Flow<List<Debt>>               = dao.getAllDebts()
    val allPeople: Flow<List<Person>>            = dao.getAllPeople()
    val allBudgets: Flow<List<Budget>>           = dao.getAllBudgets()
    val allGoals: Flow<List<Goal>>               = dao.getAllGoals()
    val allProjects: Flow<List<Project>>         = dao.getAllProjects()

    // ─── Projects ─────────────────────────────────────────────────────────────

    suspend fun insertProject(project: Project) =
        dao.insertProject(project.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteProject(project: Project) = dao.deleteProject(project)

    // ─── Transactions ─────────────────────────────────────────────────────────

    suspend fun insertTransaction(transaction: Transaction) {
        db.withTransaction {
            dao.insertTransaction(transaction.copy(updatedAt = System.currentTimeMillis()))
            when (transaction.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(transaction.fromAcct, -transaction.amount)
                "income"   -> dao.updateAccountBalance(transaction.toAcct, transaction.amount)
                "transfer" -> {
                    dao.updateAccountBalance(transaction.fromAcct, -transaction.amount)
                    dao.updateAccountBalance(transaction.toAcct, transaction.amount)
                }
            }
        }
    }

    // ─── Lending ──────────────────────────────────────────────────────────────

    suspend fun insertBorrower(borrower: Borrower) =
        insertBorrowerWithSource(borrower, "Cash in Hand")

    suspend fun insertBorrowerWithSource(
        borrower: Borrower,
        sourceAccount: String,
        projectId: String = borrower.projectId
    ) {
        db.withTransaction {
            dao.insertBorrower(borrower.copy(projectId = projectId, updatedAt = System.currentTimeMillis()))
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            dao.insertTransaction(
                Transaction(
                    id        = java.util.UUID.randomUUID().toString(),
                    date      = borrower.date,
                    type      = "Expense",
                    amount    = borrower.amount,
                    category  = "Lending",
                    desc      = "Lent money to ${borrower.name}",
                    notes     = borrower.notes,
                    fromAcct  = sourceAccount,
                    projectId = projectId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── Accounts ─────────────────────────────────────────────────────────────

    suspend fun insertAccount(account: Account) =
        dao.insertAccount(account.copy(updatedAt = System.currentTimeMillis()))

    // ─── Investments ──────────────────────────────────────────────────────────

    suspend fun insertInvestmentWithSource(
        investment: Investment,
        sourceAccount: String,
        projectId: String = investment.projectId
    ) {
        db.withTransaction {
            dao.insertInvestment(investment.copy(projectId = projectId, updatedAt = System.currentTimeMillis()))
            dao.updateAccountBalance(sourceAccount, -investment.invested)
            dao.insertTransaction(
                Transaction(
                    id        = java.util.UUID.randomUUID().toString(),
                    date      = investment.date,
                    type      = "Expense",
                    amount    = investment.invested,
                    category  = "Investment",
                    desc      = "Invested in ${investment.name}",
                    notes     = investment.notes,
                    fromAcct  = sourceAccount,
                    projectId = projectId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── Debts ────────────────────────────────────────────────────────────────

    suspend fun insertDebtWithDestination(
        debt: Debt,
        destinationAccount: String,
        projectId: String = debt.projectId
    ) {
        db.withTransaction {
            dao.insertDebt(debt.copy(projectId = projectId, updatedAt = System.currentTimeMillis()))
            dao.updateAccountBalance(destinationAccount, debt.amount)
            dao.insertTransaction(
                Transaction(
                    id        = java.util.UUID.randomUUID().toString(),
                    date      = debt.date,
                    type      = "Income",
                    amount    = debt.amount,
                    category  = "Debt",
                    desc      = "Loan from ${debt.name}",
                    notes     = debt.notes,
                    toAcct    = destinationAccount,
                    projectId = projectId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── Loan Repayments ──────────────────────────────────────────────────────

    suspend fun insertPaymentWithDest(
        payment: Payment,
        destinationAccount: String,
        projectId: String = payment.projectId
    ) {
        db.withTransaction {
            dao.insertPayment(payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis()))
            dao.updateAccountBalance(destinationAccount, payment.amount)
            dao.updateBorrowerPaidAmount(payment.loanId, payment.amount)
            dao.insertTransaction(
                Transaction(
                    id        = java.util.UUID.randomUUID().toString(),
                    date      = payment.date,
                    type      = "Income",
                    amount    = payment.amount,
                    category  = "Loan Repayment",
                    desc      = "Received from ${payment.name}",
                    notes     = payment.notes,
                    toAcct    = destinationAccount,
                    projectId = projectId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun insertDebtPaymentWithSource(
        payment: DebtPayment,
        sourceAccount: String,
        projectId: String = payment.projectId
    ) {
        db.withTransaction {
            dao.insertDebtPayment(payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis()))
            dao.updateAccountBalance(sourceAccount, -payment.amount)
            dao.updateDebtPaidAmount(payment.debtId, payment.amount)
            dao.insertTransaction(
                Transaction(
                    id        = java.util.UUID.randomUUID().toString(),
                    date      = payment.date,
                    type      = "Expense",
                    amount    = payment.amount,
                    category  = "Debt Repayment",
                    desc      = "Paid for ${payment.name}",
                    notes     = payment.notes,
                    fromAcct  = sourceAccount,
                    projectId = projectId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ─── People / Budget / Goal ───────────────────────────────────────────────

    suspend fun insertPerson(person: Person) =
        dao.insertPerson(person.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deletePerson(person: Person)   = dao.deletePerson(person)

    suspend fun insertBudget(budget: Budget) =
        dao.insertBudget(budget.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteBudget(budget: Budget)   = dao.deleteBudget(budget)

    suspend fun insertGoal(goal: Goal) =
        dao.insertGoal(goal.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteGoal(goal: Goal)         = dao.deleteGoal(goal)

    // ─── Delete with balance revert ───────────────────────────────────────────

    suspend fun deleteTransaction(transaction: Transaction) {
        db.withTransaction {
            when (transaction.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                "income"   -> dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                "transfer" -> {
                    dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                    dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                }
            }
            dao.deleteTransaction(transaction)
        }
    }

    suspend fun deleteBorrower(borrower: Borrower) {
        db.withTransaction {
            dao.updateAccountBalance("Cash in Hand", borrower.amount)
            dao.deleteBorrower(borrower)
        }
    }

    suspend fun deleteDebt(debt: Debt) {
        db.withTransaction {
            dao.updateAccountBalance("HDFC Bank", -debt.amount)
            dao.deleteDebt(debt)
        }
    }

    suspend fun deleteInvestment(investment: Investment) {
        db.withTransaction {
            dao.updateAccountBalance("HDFC Bank", investment.invested)
            dao.deleteInvestment(investment)
        }
    }

    // ─── Payments ─────────────────────────────────────────────────────────────

    fun getPaymentsForLoan(loanId: String) = dao.getPaymentsForLoan(loanId)

    // ─── Backup / Restore ─────────────────────────────────────────────────────

    suspend fun getAllDataAsJson(): String {
        val allData = BackupData(
            accounts     = dao.getAllAccounts().first(),
            transactions = dao.getAllTransactions().first(),
            borrowers    = dao.getAllBorrowers().first(),
            investments  = dao.getAllInvestments().first(),
            debts        = dao.getAllDebts().first(),
            people       = dao.getAllPeople().first(),
            projects     = dao.getAllProjects().first()
        )
        return Json.encodeToString(allData)
    }

    suspend fun restoreDataFromJson(json: String) {
        db.withTransaction {
            val data = Json.decodeFromString<BackupData>(json)
            dao.deleteAllAccounts()
            dao.deleteAllTransactions()
            dao.deleteAllBorrowers()
            dao.deleteAllInvestments()
            dao.deleteAllDebts()
            dao.deleteAllPeople()
            dao.deleteAllProjects()
            data.accounts.forEach     { dao.insertAccount(it) }
            data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach    { dao.insertBorrower(it) }
            data.investments.forEach  { dao.insertInvestment(it) }
            data.debts.forEach        { dao.insertDebt(it) }
            data.people.forEach       { dao.insertPerson(it) }
            data.projects.forEach     { dao.insertProject(it) }
        }
    }
}
