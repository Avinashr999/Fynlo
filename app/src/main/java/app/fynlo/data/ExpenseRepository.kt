package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Goal
import app.fynlo.data.model.Transaction
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for expense tracking, budgets, and goals.
 * Owns: Transactions, Budgets, Goals
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val allBudgets:      Flow<List<Budget>>      = dao.getAllBudgets()
    val allGoals:        Flow<List<Goal>>         = dao.getAllGoals()

    // ── Transactions ──────────────────────────────────────────────────────────
    suspend fun insertTransaction(tx: Transaction) = withContext(Dispatchers.IO) {
        dao.insertTransaction(tx)
        firestore.upsertTransaction(tx)
    }

    suspend fun updateTransaction(old: Transaction, new: Transaction) = withContext(Dispatchers.IO) {
        dao.updateTransaction(old, new)
        firestore.upsertTransaction(new)
    }

    suspend fun deleteTransaction(tx: Transaction) = withContext(Dispatchers.IO) {
        dao.deleteTransaction(tx.id)
        firestore.deleteTransaction(tx.id)
    }

    // ── Budgets ───────────────────────────────────────────────────────────────
    suspend fun upsertBudget(budget: Budget) = withContext(Dispatchers.IO) {
        dao.insertBudget(budget)
        firestore.upsertBudget(budget)
    }

    suspend fun deleteBudget(budget: Budget) = withContext(Dispatchers.IO) {
        dao.deleteBudget(budget.id)
        firestore.deleteBudget(budget.id)
    }

    // ── Goals ─────────────────────────────────────────────────────────────────
    suspend fun upsertGoal(goal: Goal) = withContext(Dispatchers.IO) {
        dao.insertGoal(goal)
        firestore.upsertGoal(goal)
    }

    suspend fun deleteGoal(goal: Goal) = withContext(Dispatchers.IO) {
        dao.deleteGoal(goal.id)
        firestore.deleteGoal(goal.id)
    }
}
