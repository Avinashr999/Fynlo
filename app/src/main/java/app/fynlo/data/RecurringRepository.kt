package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction
import app.fynlo.data.remote.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility repository for recurring transaction management.
 * Owns: RecurringTransactions and their auto-logging logic.
 */
@Singleton
class RecurringRepository @Inject constructor(
    private val dao: FynloDao,
    private val firestore: FirestoreRepository
) {
    val allRecurring: Flow<List<RecurringTransaction>> = dao.getAllRecurringTransactions()

    suspend fun insert(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        dao.insertRecurringTransaction(r)
        firestore.upsertRecurring(r)
    }

    suspend fun update(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        dao.updateRecurringTransaction(r)
        firestore.upsertRecurring(r)
    }

    suspend fun delete(r: RecurringTransaction) = withContext(Dispatchers.IO) {
        dao.deleteRecurringTransaction(r)
        firestore.upsertRecurring(r.copy(isActive = false))
    }

    /**
     * Auto-logs any due recurring transactions as real transactions.
     * Idempotent — safe to call on every app launch.
     */
    suspend fun processDue(projectId: String): Int = withContext(Dispatchers.IO) {
        val today    = LocalDate.now()
        val fmt      = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val allItems = dao.getAllRecurringTransactionsOnce()
        var logged   = 0

        for (r in allItems.filter { it.isActive && it.projectId == projectId }) {
            val lastRun: LocalDate? = r.lastRun.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it, fmt) }.getOrNull() }

            val nextDue = when {
                lastRun == null          -> today
                r.frequency == "Daily"   -> lastRun.plusDays(1)
                r.frequency == "Weekly"  -> lastRun.plusWeeks(1)
                r.frequency == "Monthly" -> lastRun.plusMonths(1)
                r.frequency == "Yearly"  -> lastRun.plusYears(1)
                else                     -> lastRun.plusMonths(1)
            }

            if (!today.isBefore(nextDue)) {
                val txn = Transaction(
                    id        = java.util.UUID.randomUUID().toString(),
                    type      = r.type,
                    category  = r.category,
                    amount    = r.amount,
                    date      = today.format(fmt),
                    desc      = r.name,
                    notes     = "Auto-logged (recurring)",
                    fromAcct  = if (r.type.equals("expense", ignoreCase = true)) r.account else "",
                    toAcct    = if (r.type.equals("income",  ignoreCase = true)) r.account else "",
                    projectId = projectId
                )
                dao.insertTransaction(txn)
                firestore.upsertTransaction(txn)

                val updated = r.copy(lastRun = today.format(fmt))
                dao.updateRecurringTransaction(updated)
                firestore.upsertRecurring(updated)
                logged++
            }
        }
        logged
    }
}
