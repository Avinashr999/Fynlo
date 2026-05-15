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
 * Single-responsibility repository for budgets and savings goals.
 * Owns: Budgets, Goals
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allBudgets: Flow<List<Budget>> = dao.getAllBudgets()
    val allGoals:   Flow<List<Goal>>   = dao.getAllGoals()

    suspend fun insertBudget(budget: Budget) = withContext(Dispatchers.IO) {
        dao.insertBudget(budget)
        firestore.upsertBudget(budget)
    }

    suspend fun updateBudget(budget: Budget) = withContext(Dispatchers.IO) {
        dao.updateBudget(budget)
        firestore.upsertBudget(budget)
    }

    suspend fun deleteBudget(budget: Budget) = withContext(Dispatchers.IO) {
        dao.deleteBudget(budget.id)
        firestore.deleteBudget(budget.id)
    }

    suspend fun insertGoal(goal: Goal) = withContext(Dispatchers.IO) {
        dao.insertGoal(goal)
        firestore.upsertGoal(goal)
    }

    suspend fun updateGoal(goal: Goal) = withContext(Dispatchers.IO) {
        dao.updateGoal(goal)
        firestore.upsertGoal(goal)
    }

    suspend fun deleteGoal(goal: Goal) = withContext(Dispatchers.IO) {
        dao.deleteGoal(goal.id)
        firestore.deleteGoal(goal.id)
    }

    /** Spending per category for the given project in the current month */
    suspend fun getSpendingByCategory(
        projectId: String,
        transactions: List<Transaction>
    ): Map<String, Double> = withContext(Dispatchers.IO) {
        transactions
            .filter { it.type.equals("expense", ignoreCase = true) && it.projectId == projectId }
            .groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
    }
}
