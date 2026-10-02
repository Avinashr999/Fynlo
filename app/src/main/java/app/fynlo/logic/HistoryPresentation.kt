package app.fynlo.logic

import app.fynlo.data.model.Transaction

enum class HistoryFilter(val label: String) {
    ALL("All activity"), MONEY_IN("Money in"), MONEY_OUT("Money out"),
    TRANSFERS("Transfers"), CORRECTIONS("Corrections");

    fun matches(txn: Transaction): Boolean = when (this) {
        ALL -> true
        MONEY_IN -> !txn.isGeneratedJournalEntry() && txn.type.equals("Income", true)
        MONEY_OUT -> !txn.isGeneratedJournalEntry() && txn.type.equals("Expense", true)
        TRANSFERS -> !txn.isGeneratedJournalEntry() && txn.type.equals("Transfer", true)
        CORRECTIONS -> txn.category.trim().equals("Balance Correction", true)
    }
}

fun Transaction.matchesHistoryQuery(query: String, accountNames: Map<String, String>): Boolean {
    val text = query.trim()
    return text.isEmpty() || listOf(
        category, subcat, person, desc, notes, date, amount.toString(),
        displayFromAcct(accountNames), displayToAcct(accountNames),
    ).any { it.contains(text, ignoreCase = true) }
}

data class HistoryCashTotals(val moneyIn: Double, val moneyOut: Double, val transfers: Double)

fun historyCashTotals(rows: List<Transaction>): HistoryCashTotals = HistoryCashTotals(
    moneyIn = rows.filter { HistoryFilter.MONEY_IN.matches(it) }.sumOf { it.amount },
    moneyOut = rows.filter { HistoryFilter.MONEY_OUT.matches(it) }.sumOf { it.amount },
    transfers = rows.filter { HistoryFilter.TRANSFERS.matches(it) }.sumOf { it.amount },
)
