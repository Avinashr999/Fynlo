package app.fynlo.delegate

import app.fynlo.data.model.Transaction
import app.fynlo.data.model.FlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.fynlo.logic.Ids
import app.fynlo.data.Categories

class TransactionDelegate(
    private val ctx: ViewModelContext,
    private val transactionsFlow: StateFlow<List<Transaction>>
) {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTransactions: StateFlow<List<Transaction>> =
        combine(transactionsFlow, _searchQuery) { trans, query ->
            if (query.isEmpty()) trans
            else trans.filter {
                it.category.contains(query, ignoreCase = true) ||
                it.desc.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true) ||
                it.date.contains(query)
            }
        }.stateIn(ctx.scope, SharingStarted.Eagerly, emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun addTransaction(transaction: Transaction) {
        ctx.runMoneyAction { ctx.repository.insertTransaction(transaction.copy(projectId = ctx.currentProjectId())) }
    }

    fun updateTransaction(transaction: Transaction) {
        ctx.runMoneyAction { ctx.repository.insertTransaction(transaction.copy(projectId = ctx.currentProjectId())) }
    }
    
    fun editTransaction(old: Transaction, new: Transaction) {
        ctx.runMoneyAction { ctx.repository.editTransaction(old, new) }
    }

    fun deleteTransaction(transaction: Transaction) {
        ctx.runMoneyAction { ctx.repository.deleteTransaction(transaction) }
    }

    fun deleteTransactions(transactions: List<Transaction>) {
        ctx.runMoneyAction { transactions.forEach { ctx.repository.deleteTransaction(it) } }
    }
    
    fun executeFlow(result: FlowResult) {
        ctx.scope.launch(Dispatchers.IO) {
            val id = Ids.newId()
            when (result.eventType) {
                "Received" -> {
                    val t = Transaction(
                        id = id,
                        date = result.date,
                        type = "Income",
                        amount = result.amount,
                        toAcct = result.toAccount,
                        category = result.category,
                        desc = result.description,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    ctx.repository.insertTransaction(t)
                }
                "Spent" -> {
                    val t = Transaction(
                        id = id,
                        date = result.date,
                        type = "Expense",
                        amount = result.amount,
                        fromAcct = result.fromAccount,
                        category = result.category,
                        desc = result.description,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    ctx.repository.insertTransaction(t)
                }
                "Moved" -> {
                    val t = Transaction(
                        id = id,
                        date = result.date,
                        type = "Transfer",
                        amount = result.amount,
                        fromAcct = result.fromAccount,
                        toAcct = result.toAccount,
                        category = Categories.ACCOUNT_TRANSFER,
                        desc = result.description.ifBlank { "Transfer: ${result.fromAccount} -> ${result.toAccount}" },
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    ctx.repository.insertTransaction(t)
                }
                "Lent" -> {
                    val borrower = app.fynlo.data.model.Borrower(
                        id = id,
                        name = result.personName,
                        phone = result.personPhone,
                        amount = result.amount,
                        rate = 0.0,
                        date = result.date,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    ctx.repository.insertBorrowerWithSource(borrower, result.fromAccount, result.projectId)
                }
                "Borrowed" -> {
                    val debt = app.fynlo.data.model.Debt(
                        id = id,
                        name = result.personName,
                        phone = result.personPhone,
                        amount = result.amount,
                        rate = 0.0,
                        date = result.date,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    ctx.repository.insertDebtWithDestination(debt, result.toAccount, result.projectId)
                }
            }
        }
    }
}
