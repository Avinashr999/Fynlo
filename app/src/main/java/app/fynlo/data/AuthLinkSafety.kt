package app.fynlo.data

import app.fynlo.data.local.FynloDao
import kotlinx.coroutines.flow.first

data class LocalLedgerSummary(
    val accounts: Int,
    val transactions: Int,
    val borrowers: Int,
    val debts: Int,
    val investments: Int,
) {
    val totalRecords: Int
        get() = accounts + transactions + borrowers + debts + investments

    val hasUserData: Boolean
        get() = totalRecords > 0
}

suspend fun FynloDao.localLedgerSummary(): LocalLedgerSummary {
    return LocalLedgerSummary(
        accounts = getAllAccounts().first().size,
        transactions = getAllTransactions().first().size,
        borrowers = getAllBorrowers().first().size,
        debts = getAllDebts().first().size,
        investments = getAllInvestments().first().size,
    )
}
