package app.fynlo.data

import android.util.Log
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

    private inline fun <T> recordOnFail(op: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        Log.e("ExpenseRepo", "$op failed: ${e.message}", e)
        FirebaseCrashlytics.getInstance().apply {
            log("ExpenseRepository.$op failed")
            recordException(e)
        }
        throw e
    }

    suspend fun insertTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        recordOnFail("insertTransaction") {
            val t = transaction.copy(updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            Analytics.transactionAdded(type = t.type, category = t.category)
        }
    }

    suspend fun deleteTransaction(transaction: Transaction) = withContext(Dispatchers.IO) {
        recordOnFail("deleteTransaction") { dao.deleteTransaction(transaction) }
    }

    suspend fun deleteTransactionById(id: String) = withContext(Dispatchers.IO) {
        recordOnFail("deleteTransactionById") { dao.deleteTransactionById(id) }
    }

    suspend fun getTransactionsByRef(ref: String): List<Transaction> = withContext(Dispatchers.IO) {
        recordOnFail("getTransactionsByRef") { dao.getTransactionsByRef(ref) }
    }

    suspend fun getTransactionsByDesc(desc: String): List<Transaction> = withContext(Dispatchers.IO) {
        recordOnFail("getTransactionsByDesc") { dao.getTransactionsByDesc(desc) }
    }

    suspend fun getTransactionById(id: String): Transaction? = withContext(Dispatchers.IO) {
        recordOnFail("getTransactionById") { dao.getTransactionById(id) }
    }

    suspend fun getAllTransactionsList(): List<Transaction> = withContext(Dispatchers.IO) {
        recordOnFail("getAllTransactionsList") { dao.getAllTransactionsList() }
    }

    suspend fun insertBudget(budget: Budget) = withContext(Dispatchers.IO) {
        recordOnFail("insertBudget") {
            val b = budget.copy(updatedAt = System.currentTimeMillis())
            dao.insertBudget(b)
        }
    }

    suspend fun deleteBudget(budget: Budget) = withContext(Dispatchers.IO) {
        recordOnFail("deleteBudget") { dao.deleteBudget(budget) }
    }

    suspend fun insertGoal(goal: Goal) = withContext(Dispatchers.IO) {
        recordOnFail("insertGoal") {
            val g = goal.copy(updatedAt = System.currentTimeMillis())
            dao.insertGoal(g)
        }
    }

    suspend fun deleteGoal(goal: Goal) = withContext(Dispatchers.IO) {
        recordOnFail("deleteGoal") { dao.deleteGoal(goal) }
    }

    fun getAllRecurringTransactions(): Flow<List<RecurringTransaction>> =
        dao.getAllRecurringTransactions()

    suspend fun insertRecurringTransaction(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        recordOnFail("insertRecurringTransaction") { dao.insertRecurringTransaction(r) }
    }

    suspend fun deleteRecurringTransaction(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        recordOnFail("deleteRecurringTransaction") { dao.deleteRecurringTransaction(r) }
    }

    suspend fun updateRecurringLastRun(id: String, date: String) = withContext(Dispatchers.IO) {
        recordOnFail("updateRecurringLastRun") { dao.updateRecurringLastRun(id, date) }
    }
}
