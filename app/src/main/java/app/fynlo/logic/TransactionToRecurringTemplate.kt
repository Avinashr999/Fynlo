package app.fynlo.logic

import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * C13 #5 (3.2.81) — convert a one-time [Transaction] into a Monthly
 * [RecurringTransaction] template fired on the same day-of-month as the
 * original transaction's date. Used when the Add Transaction dialog's
 * "Repeat monthly?" toggle is ON: the call site inserts the regular
 * transaction AND this template via [FinanceViewModel.addRecurringTransaction].
 *
 * `dayOfMonth` is clamped to 1..31; `RecurringWorker`'s last-day clamp at
 * run time handles shorter months (Feb 28 / Apr 30) automatically.
 */
fun toRecurringTemplate(txn: Transaction): RecurringTransaction {
    val day = runCatching {
        // Transaction.date is ISO `yyyy-MM-dd` after validator scrubbing.
        LocalDate.parse(txn.date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).dayOfMonth
    }.getOrDefault(LocalDate.now().dayOfMonth).coerceIn(1, 31)

    return RecurringTransaction(
        id         = Ids.newId(),
        name       = txn.desc.ifBlank { txn.category.ifBlank { "Recurring ${txn.type.lowercase()}" } },
        type       = txn.type,
        amount     = txn.amount,
        category   = txn.category,
        fromAcct   = txn.fromAcct,
        toAcct     = txn.toAcct,
        frequency  = "Monthly",
        dayOfMonth = day,
        notes      = txn.notes,
        isActive   = true,
        projectId  = txn.projectId,
        updatedAt  = System.currentTimeMillis(),
        createdAt  = System.currentTimeMillis(),
    )
}
