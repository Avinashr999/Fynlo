package app.fynlo.logic

import app.fynlo.data.model.Transaction
import java.time.LocalDate

/**
 * One ordering contract for all money history surfaces.
 *
 * The business date decides the ledger day. For rows on the same day, the
 * creation/update timestamp decides the entry order, with id as the final
 * deterministic tie-breaker.
 */
object TransactionOrdering {
    fun newestFirst(transactions: List<Transaction>): List<Transaction> =
        transactions.sortedWith(
            compareByDescending<Transaction> { dateEpochDay(it.date) }
                .thenByDescending { eventMillis(it) }
                .thenByDescending { it.id }
        )

    fun oldestFirst(transactions: List<Transaction>): List<Transaction> =
        transactions.sortedWith(
            compareBy<Transaction> { dateEpochDay(it.date) }
                .thenBy { eventMillis(it) }
                .thenBy { it.id }
        )

    fun eventMillis(transaction: Transaction): Long =
        when {
            transaction.createdAt > 0L -> transaction.createdAt
            transaction.updatedAt > 0L -> transaction.updatedAt
            else -> 0L
        }

    private fun dateEpochDay(value: String): Long =
        runCatching { LocalDate.parse(value).toEpochDay() }.getOrDefault(Long.MIN_VALUE)
}
