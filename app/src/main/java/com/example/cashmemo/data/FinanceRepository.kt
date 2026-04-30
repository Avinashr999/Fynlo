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
    val allBorrowers: Flow<List<Borrower>> = dao.getAllBorrowers()
    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val allAccounts: Flow<List<Account>> = dao.getAllAccounts()
    val allInvestments: Flow<List<Investment>> = dao.getAllInvestments()
    val allDebts: Flow<List<Debt>> = dao.getAllDebts()
    val allPeople: Flow<List<Person>> = dao.getAllPeople()
    val allBudgets: Flow<List<Budget>> = dao.getAllBudgets()
    val allGoals: Flow<List<Goal>> = dao.getAllGoals()

    suspend fun insertTransaction(transaction: Transaction) {
        db.withTransaction {
            dao.insertTransaction(transaction)
            
            when (transaction.type.lowercase()) {
                "expense" -> {
                    dao.updateAccountBalance(transaction.fromAcct, -transaction.amount)
                }
                "income" -> {
                    dao.updateAccountBalance(transaction.toAcct, transaction.amount)
                }
                "transfer" -> {
                    dao.updateAccountBalance(transaction.fromAcct, -transaction.amount)
                    dao.updateAccountBalance(transaction.toAcct, transaction.amount)
                }
            }
        }
    }
    suspend fun insertBorrower(borrower: Borrower) {
        // Assume default if no source provided (legacy support)
        insertBorrowerWithSource(borrower, "Cash in Hand")
    }

    suspend fun insertBorrowerWithSource(borrower: Borrower, sourceAccount: String) {
        db.withTransaction {
            dao.insertBorrower(borrower)
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            
            // Log this in history too
            val txn = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = borrower.date,
                type = "Expense",
                amount = borrower.amount,
                category = "Lending",
                desc = "Lent money to ${borrower.name}",
                notes = borrower.notes,
                fromAcct = sourceAccount
            )
            dao.insertTransaction(txn)
        }
    }

    suspend fun insertAccount(account: Account) = dao.insertAccount(account)

    suspend fun insertInvestmentWithSource(investment: Investment, sourceAccount: String) {
        db.withTransaction {
            dao.insertInvestment(investment)
            dao.updateAccountBalance(sourceAccount, -investment.invested)
            
            // Log in history
            val txn = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = investment.date,
                type = "Expense",
                amount = investment.invested,
                category = "Investment",
                desc = "Invested in ${investment.name}",
                notes = investment.notes,
                fromAcct = sourceAccount
            )
            dao.insertTransaction(txn)
        }
    }

    suspend fun insertDebtWithDestination(debt: Debt, destinationAccount: String) {
        db.withTransaction {
            dao.insertDebt(debt)
            dao.updateAccountBalance(destinationAccount, debt.amount)
            
            // Log in history
            val txn = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = debt.date,
                type = "Income",
                amount = debt.amount,
                category = "Debt",
                desc = "Loan from ${debt.name}",
                notes = debt.notes,
                toAcct = destinationAccount
            )
            dao.insertTransaction(txn)
        }
    }

    suspend fun insertPaymentWithDest(payment: Payment, destinationAccount: String) {
        db.withTransaction {
            dao.insertPayment(payment)
            // When a borrower pays you back, your cash increases
            dao.updateAccountBalance(destinationAccount, payment.amount)
            
            // REDUCE OUTSTANDING: Update the borrower's 'paid' field
            dao.updateBorrowerPaidAmount(payment.loanId, payment.amount)
            
            val txn = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = payment.date,
                type = "Income",
                amount = payment.amount,
                category = "Loan Repayment",
                desc = "Received from ${payment.name}",
                notes = payment.notes,
                toAcct = destinationAccount
            )
            dao.insertTransaction(txn)
        }
    }

    suspend fun insertDebtPaymentWithSource(payment: DebtPayment, sourceAccount: String) {
        db.withTransaction {
            dao.insertDebtPayment(payment)
            
            // REDUCE ACCOUNT BALANCE
            dao.updateAccountBalance(sourceAccount, -payment.amount)
            
            // REDUCE OUTSTANDING DEBT
            dao.updateDebtPaidAmount(payment.debtId, payment.amount)
            
            val txn = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = payment.date,
                type = "Expense",
                amount = payment.amount,
                category = "Debt Repayment",
                desc = "Paid for ${payment.name}",
                notes = payment.notes,
                fromAcct = sourceAccount
            )
            dao.insertTransaction(txn)
        }
    }

    // --- Data Export & Restore Logic ---
    suspend fun getAllDataAsJson(): String {
        val allData = BackupData(
            accounts = dao.getAllAccounts().first(),
            transactions = dao.getAllTransactions().first(),
            borrowers = dao.getAllBorrowers().first(),
            investments = dao.getAllInvestments().first(),
            debts = dao.getAllDebts().first(),
            people = dao.getAllPeople().first()
        )
        return Json.encodeToString(allData)
    }

    suspend fun restoreDataFromJson(json: String) {
        db.withTransaction {
            val data = Json.decodeFromString<BackupData>(json)
            
            // Clear current data
            dao.deleteAllAccounts()
            dao.deleteAllTransactions()
            dao.deleteAllBorrowers()
            dao.deleteAllInvestments()
            dao.deleteAllDebts()
            dao.deleteAllPeople()
            
            // Restore from backup
            data.accounts.forEach { dao.insertAccount(it) }
            data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach { dao.insertBorrower(it) }
            data.investments.forEach { dao.insertInvestment(it) }
            data.debts.forEach { dao.insertDebt(it) }
            data.people.forEach { dao.insertPerson(it) }
        }
    }

    // --- Safety Logics: Handling Deletions to avoid financial leaks ---

    suspend fun deleteTransaction(transaction: Transaction) {
        db.withTransaction {
            // Revert the balance changes before deleting the record
            when (transaction.type.lowercase()) {
                "expense" -> dao.updateAccountBalance(transaction.fromAcct, transaction.amount)
                "income" -> dao.updateAccountBalance(transaction.toAcct, -transaction.amount)
                "transfer" -> {
                    dao.updateAccountBalance(transaction.fromAcct, transaction.amount)
                    dao.updateAccountBalance(transaction.toAcct, -transaction.amount)
                }
            }
            // Now safe to delete
            // (Assuming you add a deleteTransaction to DAO)
        }
    }

    suspend fun deleteBorrower(borrower: Borrower) {
        db.withTransaction {
            // Revert: If we delete a borrower, we assume the money lent was never lent? 
            // Better logic: Mark as "Closed" rather than delete. 
            // But if force delete, we should restore the cash used to lend it.
            dao.updateAccountBalance("Cash in Hand", borrower.amount)
            dao.deleteBorrower(borrower)
        }
    }

    suspend fun deleteDebt(debt: Debt) {
        db.withTransaction {
            // When you delete a debt, we assume the disbursement was undone
            dao.updateAccountBalance("HDFC Bank", -debt.amount)
            dao.deleteDebt(debt)
        }
    }

    suspend fun insertPerson(person: Person) = dao.insertPerson(person)
    suspend fun deletePerson(person: Person) = dao.deletePerson(person)

    suspend fun insertBudget(budget: Budget) = dao.insertBudget(budget)
    suspend fun deleteBudget(budget: Budget) = dao.deleteBudget(budget)

    suspend fun insertGoal(goal: Goal) = dao.insertGoal(goal)
    suspend fun deleteGoal(goal: Goal) = dao.deleteGoal(goal)

    suspend fun deleteInvestment(investment: Investment) {
        db.withTransaction {
            // Revert: If we delete an investment, we restore the cash used to buy it
            dao.updateAccountBalance("HDFC Bank", investment.invested)
            dao.deleteInvestment(investment)
        }
    }
    
    fun getPaymentsForLoan(loanId: String) = dao.getPaymentsForLoan(loanId)
}