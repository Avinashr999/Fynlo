package app.fynlo.delegate

import app.fynlo.data.model.Person
import app.fynlo.data.model.Budget
import app.fynlo.data.model.Goal
import app.fynlo.data.model.RecurringTransaction
import app.fynlo.data.model.Transaction
import app.fynlo.logic.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PlanningDelegate(private val ctx: ViewModelContext) {

    fun addPerson(person: Person) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.insertPerson(person.copy(projectId = ctx.currentProjectId()))
        }
    }

    fun updatePerson(person: Person) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.insertPerson(person.copy(projectId = ctx.currentProjectId(), updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePerson(person: Person) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.deletePerson(person) }
    }

    fun addBudget(budget: Budget) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.insertBudget(budget.copy(projectId = ctx.currentProjectId()))
        }
    }

    fun deleteBudget(budget: Budget) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.deleteBudget(budget) }
    }
    
    fun addGoal(goal: Goal) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.insertGoal(goal.copy(projectId = ctx.currentProjectId()))
        }
    }

    fun deleteGoal(goal: Goal) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.deleteGoal(goal) }
    }
    
    fun addRecurringTransaction(r: RecurringTransaction) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.insertRecurringTransaction(r) }
    }

    fun deleteRecurringTransaction(r: RecurringTransaction) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.deleteRecurringTransaction(r) }
    }

    fun triggerDueRecurring() {
        ctx.scope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val fmt   = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val all   = ctx.repository.getAllRecurringTransactions().first()

            var logged = 0
            for (r in all) {
                if (!r.isActive) continue

                val lastRun = if (r.lastRun.isBlank()) null else
                    runCatching { LocalDate.parse(r.lastRun, fmt) }.getOrNull()

                val nextDue: java.time.LocalDate = when {
                    lastRun == null -> today
                    r.frequency == "Daily"   -> lastRun.plusDays(1)
                    r.frequency == "Weekly"  -> lastRun.plusWeeks(1)
                    r.frequency == "Monthly" -> lastRun.plusMonths(1)
                    r.frequency == "Yearly"  -> lastRun.plusYears(1)
                    else -> lastRun.plusMonths(1)
                }

                if (!today.isBefore(nextDue)) {
                    val txn = Transaction(
                        id       = Ids.newId(),
                        date     = today.format(fmt),
                        type     = r.type,
                        amount   = r.amount,
                        fromAcct = if (r.type == "Expense") r.fromAcct else "",
                        toAcct   = if (r.type == "Income")  r.toAcct  else "",
                        category = r.category,
                        notes    = "Auto: ${r.name}",
                        projectId = r.projectId
                    )
                    ctx.repository.insertTransaction(txn)

                    ctx.repository.insertRecurringTransaction(
                        r.copy(lastRun = today.format(fmt), updatedAt = System.currentTimeMillis())
                    )
                    logged++
                }
            }
            if (logged > 0) {
                android.util.Log.i("Recurring", "Auto-logged $logged recurring transactions")
            }
        }
    }
}
