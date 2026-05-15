package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.BudgetRepository
import app.fynlo.data.ExpenseRepository
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for expenses and transactions.
 */
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val budgetRepo: BudgetRepository
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> =
        expenseRepo.allTransactions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<List<Budget>> =
        budgetRepo.allBudgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) { expenseRepo.insertTransaction(transaction) }
    }

    fun editTransaction(old: Transaction, new: Transaction) {
        viewModelScope.launch(Dispatchers.IO) { expenseRepo.updateTransaction(old, new) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) { expenseRepo.deleteTransaction(transaction) }
    }
}
