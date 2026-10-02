package app.fynlo.logic

import app.fynlo.data.model.Transaction
import java.util.Locale

private val nonOperatingCategories = setOf(
    "debt received", "debt repayment", "lending", "loan recovery", "loan repayment",
    "investment", "investment returns", "balance correction", "transfer", "account transfer",
)

/**
 * Reporting only: legacy Expense/Income types also describe financing cash movement.
 * Never use this filter to replay account balances or remove transaction history.
 * Investment returns retain the existing reports' separate treatment; journal-only
 * interest/write-offs remain in their dedicated accounting breakdowns.
 */
fun Transaction.isOperatingCashEntry(): Boolean =
    (type.equals("expense", ignoreCase = true) || type.equals("income", ignoreCase = true)) &&
        !isGeneratedJournalEntry() &&
        category.trim().lowercase(Locale.ROOT) !in nonOperatingCategories

fun Transaction.isSpendingExpense(): Boolean =
    type.equals("expense", ignoreCase = true) && isOperatingCashEntry()
