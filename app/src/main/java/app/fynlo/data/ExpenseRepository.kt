package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class ExpenseRepository constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val allBudgets: Flow<List<Budget>> = dao.getAllBudgets()
    val allGoals: Flow<List<Goal>> = dao.getAllGoals()

    suspend fun insertTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        val t = transaction.copy(updatedAt = System.currentTimeMillis())
        dao.insertTransaction(t)
    }

    suspend fun deleteTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        dao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteTransactionById(id)
    }

    suspend fun getTransactionsByRef(ref: String): List<Transaction> = withContext(Dispatchers.IO) {
        dao.getTransactionsByRef(ref)
    }

    suspend fun getTransactionsByDesc(desc: String): List<Transaction> = withContext(Dispatchers.IO) {
        dao.getTransactionsByDesc(desc)
    }

    suspend fun getTransactionById(id: String): Transaction? = withContext(Dispatchers.IO) {
        dao.getTransactionById(id)
    }

    suspend fun getAllTransactionsList(): List<Transaction> = withContext(Dispatchers.IO) {
        dao.getAllTransactionsList()
    }

    suspend fun insertBudget(budget: Budget) = withContext(Dispatchers.IO) {
        val b = budget.copy(updatedAt = System.currentTimeMillis())
        dao.insertBudget(b)
    }

    suspend fun deleteBudget(budget: Budget) = withContext(Dispatchers.IO) {
        dao.deleteBudget(budget)
    }

    suspend fun insertGoal(goal: Goal) = withContext(Dispatchers.IO) {
        val g = goal.copy(updatedAt = System.currentTimeMillis())
        dao.insertGoal(g)
    }

    suspend fun deleteGoal(goal: Goal) = withContext(Dispatchers.IO) {
        dao.deleteGoal(goal)
    }

    fun getAllRecurringTransactions(): Flow<List<RecurringTransaction>> =
        dao.getAllRecurringTransactions()

    suspend fun insertRecurringTransaction(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        dao.insertRecurringTransaction(r)
    }

    suspend fun deleteRecurringTransaction(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        dao.deleteRecurringTransaction(r)
    }

    suspend fun updateRecurringLastRun(id: String, date: String) = withContext(Dispatchers.IO) {
        dao.updateRecurringLastRun(id, date)
    }
}
