package com.example.cashmemo.data

import com.example.cashmemo.data.local.CashMemoDao
import com.example.cashmemo.data.local.CashMemoDatabase
import com.example.cashmemo.data.model.*
import com.example.cashmemo.data.remote.FirestoreRepository
import com.example.cashmemo.data.remote.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FinanceRepository(
    private val dao: CashMemoDao,
    private val db: CashMemoDatabase,
    private val firestore: FirestoreRepository,
    private val syncManager: SyncManager
) {
    val syncStatus: StateFlow<SyncStatus> = syncManager.status
    val allBorrowers: Flow<List<Borrower>>       = dao.getAllBorrowers()
    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val allAccounts: Flow<List<Account>>         = dao.getAllAccounts()
    val allInvestments: Flow<List<Investment>>   = dao.getAllInvestments()
    val allDebts: Flow<List<Debt>>               = dao.getAllDebts()
    val allPeople: Flow<List<Person>>            = dao.getAllPeople()
    val allBudgets: Flow<List<Budget>>           = dao.getAllBudgets()
    val allGoals: Flow<List<Goal>>               = dao.getAllGoals()
    val allProjects: Flow<List<Project>>         = dao.getAllProjects()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private fun sync(block: suspend FirestoreRepository.() -> Unit) {
        ioScope.launch { runCatching { firestore.block() } }
    }
    suspend fun insertProject(project: Project) {
        val p = project.copy(updatedAt = System.currentTimeMillis())
        dao.insertProject(p); sync { setProject(p) }
    }
    suspend fun deleteProject(project: Project) {
        dao.deleteProject(project); sync { deleteProject(project.id) }
    }
    suspend fun insertTransaction(transaction: Transaction) {
        db.withTransaction {
            val t = transaction.copy(updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            when (transaction.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(transaction.fromAcct, -transaction.amount)
                "income"   -> dao.updateAccountBalance(transaction.toAcct,    transaction.amount)
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); dao.updateAccountBalance(transaction.toAcct, transaction.amount) }
            }
            sync { setTransaction(t) }
        }
    }
    suspend fun deleteTransaction(transaction: Transaction) {
        db.withTransaction {
            when (transaction.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                "income"   -> dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, transaction.amount); dao.updateAccountBalance(transaction.toAcct, -transaction.amount) }
            }
            dao.deleteTransaction(transaction)
        }
        sync { deleteTransaction(transaction.id) }
    }
    suspend fun insertBorrower(borrower: Borrower) = insertBorrowerWithSource(borrower, "Cash in Hand")
    suspend fun insertBorrowerWithSource(borrower: Borrower, sourceAccount: String, projectId: String = borrower.projectId) {
        db.withTransaction {
            val b = borrower.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertBorrower(b)
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, category = "Lending", desc = "Lent to ${borrower.name}", notes = borrower.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setBorrower(b); setTransaction(t) }
        }
    }
    suspend fun deleteBorrower(borrower: Borrower) {
        db.withTransaction { dao.updateAccountBalance("Cash in Hand", borrower.amount); dao.deleteBorrower(borrower) }
        sync { deleteBorrower(borrower.id) }
    }
    suspend fun insertAccount(account: Account) {
        val a = account.copy(updatedAt = System.currentTimeMillis())
        dao.insertAccount(a); sync { setAccount(a) }
    }
    suspend fun insertInvestmentWithSource(investment: Investment, sourceAccount: String, projectId: String = investment.projectId) {
        db.withTransaction {
            val i = investment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertInvestment(i)
            dao.updateAccountBalance(sourceAccount, -investment.invested)
            val t = Transaction(java.util.UUID.randomUUID().toString(), investment.date, "Expense", investment.invested, fromAcct = sourceAccount, category = "Investment", desc = "Invested in ${investment.name}", notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setInvestment(i); setTransaction(t) }
        }
    }
    suspend fun deleteInvestment(investment: Investment) {
        db.withTransaction { dao.updateAccountBalance("HDFC Bank", investment.invested); dao.deleteInvestment(investment) }
        sync { deleteInvestment(investment.id) }
    }
    suspend fun insertDebtWithDestination(debt: Debt, destinationAccount: String, projectId: String = debt.projectId) {
        db.withTransaction {
            val d = debt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
            dao.updateAccountBalance(destinationAccount, debt.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), debt.date, "Income", debt.amount, toAcct = destinationAccount, category = "Debt", desc = "Loan from ${debt.name}", notes = debt.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setDebt(d); setTransaction(t) }
        }
    }
    suspend fun deleteDebt(debt: Debt) {
        db.withTransaction { dao.updateAccountBalance("HDFC Bank", -debt.amount); dao.deleteDebt(debt) }
        sync { deleteDebt(debt.id) }
    }
    suspend fun insertPaymentWithDest(payment: Payment, destinationAccount: String, projectId: String = payment.projectId) {
        db.withTransaction {
            val p = payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertPayment(p)
            dao.updateAccountBalance(destinationAccount, payment.amount)
            dao.updateBorrowerPaidAmount(payment.loanId, payment.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), payment.date, "Income", payment.amount, toAcct = destinationAccount, category = "Loan Repayment", desc = "Received from ${payment.name}", notes = payment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setPayment(p); setTransaction(t) }
        }
    }
    suspend fun insertDebtPaymentWithSource(payment: DebtPayment, sourceAccount: String, projectId: String = payment.projectId) {
        db.withTransaction {
            val p = payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebtPayment(p)
            dao.updateAccountBalance(sourceAccount, -payment.amount)
            dao.updateDebtPaidAmount(payment.debtId, payment.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), payment.date, "Expense", payment.amount, fromAcct = sourceAccount, category = "Debt Repayment", desc = "Paid for ${payment.name}", notes = payment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setDebtPayment(p); setTransaction(t) }
        }
    }
    suspend fun insertPerson(person: Person) { val p = person.copy(updatedAt = System.currentTimeMillis()); dao.insertPerson(p); sync { setPerson(p) } }
    suspend fun deletePerson(person: Person) { dao.deletePerson(person); sync { deletePerson(person.id) } }
    suspend fun insertBudget(budget: Budget) { val b = budget.copy(updatedAt = System.currentTimeMillis()); dao.insertBudget(b); sync { setBudget(b) } }
    suspend fun deleteBudget(budget: Budget) { dao.deleteBudget(budget); sync { deleteBudget(budget.category) } }
    suspend fun insertGoal(goal: Goal) { val g = goal.copy(updatedAt = System.currentTimeMillis()); dao.insertGoal(g); sync { setGoal(g) } }
    suspend fun deleteGoal(goal: Goal) { dao.deleteGoal(goal); sync { deleteGoal(goal.id) } }
    fun getPaymentsForLoan(loanId: String) = dao.getPaymentsForLoan(loanId)
    suspend fun getAllDataAsJson(): String {
        val data = BackupData(dao.getAllAccounts().first(), dao.getAllTransactions().first(), dao.getAllBorrowers().first(), dao.getAllInvestments().first(), dao.getAllDebts().first(), dao.getAllPeople().first(), dao.getAllProjects().first())
        return Json.encodeToString(data)
    }
    suspend fun restoreDataFromJson(json: String) {
        db.withTransaction {
            val data = Json.decodeFromString<BackupData>(json)
            dao.deleteAllAccounts(); dao.deleteAllTransactions(); dao.deleteAllBorrowers()
            dao.deleteAllInvestments(); dao.deleteAllDebts(); dao.deleteAllPeople(); dao.deleteAllProjects()
            data.accounts.forEach { dao.insertAccount(it) }; data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach { dao.insertBorrower(it) }; data.investments.forEach { dao.insertInvestment(it) }
            data.debts.forEach { dao.insertDebt(it) }; data.people.forEach { dao.insertPerson(it) }
            data.projects.forEach { dao.insertProject(it) }
        }
    }
}
