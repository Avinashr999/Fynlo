package app.fynlo.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fynlo.FynloApplication
import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction
import app.fynlo.logic.BalanceAuditLog  // 3.2.72 — local `app` var in doWork shadows the package; importing the symbol avoids fully-qualifying
import app.fynlo.logic.Ids               // C03b Stage #4 — centralized UUID generator
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
                // Auto-log the transaction.
                //
                // C03b Stage #1c (3.2.89): use the RecurringTransaction's
                // stored `fromAcctId` / `toAcctId` directly. Resolved at
                // template create time (Stage #1a-style resolver in
                // `FinanceRepository.insertRecurringTransaction`) and
                // backfilled by `MIGRATION_23_24` for pre-existing
                // templates. Fallback to a name lookup only when the
                // stored id is empty (legacy orphan template). Using the
                // stored id is also defence-in-depth against a "user
                // renamed their account AND created a new account with
                // the old name" pathology: the name lookup would resolve
                // to the wrong account; the stored id always lands on
                // the originally-linked one.
                val fromAcctId = if (r.fromAcctId.isNotEmpty()) r.fromAcctId
                    else if (r.fromAcct.isNotBlank()) dao.getAccountByName(r.fromAcct)?.id.orEmpty()
                    else ""
                val toAcctId   = if (r.toAcctId.isNotEmpty()) r.toAcctId
                    else if (r.toAcct.isNotBlank()) dao.getAccountByName(r.toAcct)?.id.orEmpty()
                    else ""
                val txn = Transaction(
                    id         = Ids.newId(),
                    date       = todayStr,
                    type       = r.type,
                    amount     = r.amount,
                    fromAcct   = r.fromAcct,
                    toAcct     = r.toAcct,
                    fromAcctId = fromAcctId,
                    toAcctId   = toAcctId,
                    category   = r.category.ifBlank { r.type },
                    desc       = r.name,
                    notes      = "Auto-logged by recurring schedule",
                    projectId  = r.projectId,
                    updatedAt  = System.currentTimeMillis()
                )
                dao.insertTransaction(txn)
                // Update account balance.
                //
                // C03b Stage #1c: id-keyed first (the rename-safe path
                // from Stage #1b-1) with name-keyed fallback for legacy
                // orphan templates. RecurringWorker is now the last
                // balance-mutating path covered by the orphan-bug fix
                // for Transaction-shaped writes.
                //
                // 3.2.72 audit logging is unchanged — entries record the
                // account NAME (display text) even when the underlying
                // UPDATE is id-keyed.
                val tag = BalanceAuditLog.Source.RECURRING_WORKER
                val note = "Recurring \"${r.name}\" auto-fired"
                when (r.type.lowercase()) {
                    "expense" -> if (r.fromAcct.isNotBlank()) {
                        if (fromAcctId.isNotEmpty()) dao.updateAccountBalanceById(fromAcctId, -r.amount)
                        else                          dao.updateAccountBalance(r.fromAcct, -r.amount)
                        BalanceAuditLog.record(tag, r.fromAcct, -r.amount, note)
                    }
                    "income"  -> if (r.toAcct.isNotBlank()) {
                        if (toAcctId.isNotEmpty()) dao.updateAccountBalanceById(toAcctId,    r.amount)
                        else                        dao.updateAccountBalance(r.toAcct,        r.amount)
                        BalanceAuditLog.record(tag, r.toAcct,    r.amount, note)
                    }
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
