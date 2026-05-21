package app.fynlo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val borrowers: List<Borrower>,
    val investments: List<Investment>,
    val debts: List<Debt>,
    val people: List<Person>,
    val projects: List<Project> = emptyList(),              // added in v2.0
    // added in v3.1 — without these a restore loses payment history and
    // recomputes wrong loan/debt balances.
    val payments: List<Payment> = emptyList(),
    val debtPayments: List<DebtPayment> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val recurringTransactions: List<RecurringTransaction> = emptyList()
)
