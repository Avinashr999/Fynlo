package app.fynlo.ui

import app.fynlo.data.model.Transaction
import app.fynlo.logic.TransactionOrdering
import app.fynlo.logic.isGeneratedJournalEntry

/** A read-only preview of History; generated accounting duplicates stay in full History. */
fun dashboardRecentActivity(rows: List<Transaction>): List<Transaction> =
    TransactionOrdering.newestFirst(rows.filterNot { it.isGeneratedJournalEntry() }).take(3)
