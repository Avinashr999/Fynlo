package app.fynlo.delegate

import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebtDelegate(private val ctx: ViewModelContext) {

    fun updateDebt(debt: Debt) {
        ctx.runMoneyAction { ctx.repository.updateDebt(debt.copy(projectId = ctx.currentProjectId(), updatedAt = System.currentTimeMillis())) }
    }

    fun updateDebtWithDestination(debt: Debt, destination: String) {
        ctx.runMoneyAction { ctx.repository.updateDebtWithDestination(debt.copy(projectId = ctx.currentProjectId(), updatedAt = System.currentTimeMillis()), destination) }
    }
    
    fun addDebtWithDestination(debt: Debt, destination: String) {
        ctx.runMoneyAction { ctx.repository.insertDebtWithDestination(debt.copy(projectId = ctx.currentProjectId()), destination, ctx.currentProjectId()) }
    }

    fun deleteDebt(debt: Debt) {
        ctx.runMoneyAction { ctx.repository.deleteDebt(debt) }
    }
    
    fun payDebt(payment: DebtPayment, source: String) {
        ctx.runMoneyAction { ctx.repository.insertDebtPaymentWithSource(payment.copy(projectId = ctx.currentProjectId()), source, ctx.currentProjectId()) }
    }

    fun waiveDebtInterest(debt: Debt, amount: Double, reason: String) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.waiveDebtInterest(debt.copy(projectId = ctx.currentProjectId()), amount, reason)
            ctx.showFeedback("Debt interest waived")
        }
    }
}
