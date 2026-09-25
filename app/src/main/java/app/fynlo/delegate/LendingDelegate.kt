package app.fynlo.delegate

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LendingDelegate(private val ctx: ViewModelContext) {

    fun addBorrowerWithSource(borrower: Borrower, source: String) {
        ctx.runMoneyAction { ctx.repository.insertBorrowerWithSource(borrower.copy(projectId = ctx.currentProjectId()), source, ctx.currentProjectId()) }
    }

    fun deleteBorrower(borrower: Borrower) {
        ctx.runMoneyAction { ctx.repository.deleteBorrower(borrower) }
    }

    fun updateBorrower(borrower: Borrower) {
        ctx.runMoneyAction { ctx.repository.updateBorrower(borrower.copy(projectId = ctx.currentProjectId())) }
    }

    fun updateBorrowerWithSource(borrower: Borrower, source: String) {
        ctx.runMoneyAction { ctx.repository.updateBorrowerWithSource(borrower.copy(projectId = ctx.currentProjectId()), source) }
    }
    
    fun restoreBorrowerToActive(borrower: Borrower) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.restoreBorrowerToActive(borrower) }
    }
    
    fun markBorrowerDefaulted(borrower: Borrower) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.markBorrowerDefaulted(borrower) }
    }
    
    fun writeOffBorrower(borrower: Borrower) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.writeOffBorrower(borrower) }
    }
    
    fun collectLoanPayment(payment: Payment, destination: String) {
        ctx.runMoneyAction { ctx.repository.insertPaymentWithDest(payment.copy(projectId = ctx.currentProjectId()), destination, ctx.currentProjectId()) }
    }
    
    fun waiveBorrowerInterest(borrower: Borrower, amount: Double, reason: String) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.waiveBorrowerInterest(borrower.copy(projectId = ctx.currentProjectId()), amount, reason)
            ctx.showFeedback("Loan interest waived")
        }
    }
}
