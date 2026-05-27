package app.fynlo.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fynlo.FynloApplication
import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class RecurringWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app    = context.applicationContext as FynloApplication
        val dao    = app.dao
        val today  = LocalDate.now()
        val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayStr = today.format(fmt)

        val recurring = dao.getAllRecurringTransactions().first()
        recurring.filter { it.isActive }.forEach { r ->
            if (shouldRunToday(r, today)) {
                // Auto-log the transaction
                val txn = Transaction(
                    id        = UUID.randomUUID().toString(),
                    date      = todayStr,
                    type      = r.type,
                    amount    = r.amount,
                    fromAcct  = r.fromAcct,
                    toAcct    = r.toAcct,
                    category  = r.category.ifBlank { r.type },
                    desc      = r.name,
                    notes     = "Auto-logged by recurring schedule",
                    projectId = r.projectId,
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertTransaction(txn)
                // Update account balance
                when (r.type.lowercase()) {
                    "expense" -> if (r.fromAcct.isNotBlank()) dao.updateAccountBalance(r.fromAcct, -r.amount)
                    "income"  -> if (r.toAcct.isNotBlank())   dao.updateAccountBalance(r.toAcct,    r.amount)
                }
                // Mark as run today
                dao.updateRecurringLastRun(r.id, todayStr)
            }
        }
        return Result.success()
    }

    private fun shouldRunToday(r: RecurringTransaction, today: LocalDate): Boolean {
        if (r.lastRun == today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) return false
        // C22 Stage 2 (3.2.47) — last-day-of-month support per audit §C22 #218.
        // If the user picked day 29/30/31, fire on the actual last day for
        // months that are shorter (Feb 28, Apr/Jun/Sep/Nov 30). Computed as
        // min(target, monthLength) so day 31 → Feb 28, Apr 30; day 28 →
        // Feb 28 always. Days 1-27 fire on the exact target day as before.
        val targetDay = minOf(r.dayOfMonth, today.lengthOfMonth())
        return when (r.frequency) {
            "Daily"   -> true
            "Weekly"  -> today.dayOfWeek.value == 1  // Monday
            "Monthly" -> today.dayOfMonth == targetDay
            "Yearly"  -> today.dayOfMonth == targetDay && today.monthValue == 1
            else      -> false
        }
    }
}
