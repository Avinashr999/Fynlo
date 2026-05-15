package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.BudgetRepository
import app.fynlo.data.ExpenseRepository
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Goal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for budgets and savings goals.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {

    val budgets: StateFlow<List<Budget>> =
        budgetRepo.allBudgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val goals: StateFlow<List<Goal>> =
        budgetRepo.allGoals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addBudget(budget: Budget) {
        viewModelScope.launch(Dispatchers.IO) { budgetRepo.insertBudget(budget) }
    }

    fun updateBudget(budget: Budget) {
        viewModelScope.launch(Dispatchers.IO) { budgetRepo.updateBudget(budget) }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch(Dispatchers.IO) { budgetRepo.deleteBudget(budget) }
    }

    fun addGoal(goal: Goal) {
        viewModelScope.launch(Dispatchers.IO) { budgetRepo.insertGoal(goal) }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch(Dispatchers.IO) { budgetRepo.updateGoal(goal) }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch(Dispatchers.IO) { budgetRepo.deleteGoal(goal) }
    }
}
