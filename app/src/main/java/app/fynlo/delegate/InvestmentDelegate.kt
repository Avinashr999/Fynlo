package app.fynlo.delegate

import app.fynlo.data.model.Investment
import app.fynlo.data.model.InvestmentValuation
import app.fynlo.data.model.Debt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InvestmentDelegate(private val ctx: ViewModelContext) {

    fun withdrawFromInvestment(investment: Investment, amount: Double, toAccount: String) {
        ctx.runMoneyAction { ctx.repository.withdrawFromInvestment(investment, amount, toAccount) }
    }
    
    fun addInvestmentFundedByAccount(investment: Investment, accountName: String, accountId: String = "") {
        ctx.runMoneyAction { ctx.repository.insertInvestmentFundedByAccount(investment.copy(projectId = ctx.currentProjectId()), accountName, ctx.currentProjectId(), accountId) }
    }
    
    fun addInvestmentFundedByExistingDebt(investment: Investment, debt: Debt) {
        ctx.runMoneyAction { ctx.repository.insertInvestmentFundedByExistingDebt(investment.copy(projectId = ctx.currentProjectId()), debt, ctx.currentProjectId()) }
    }
    
    fun addInvestmentFundedByNewLoan(investment: Investment, newDebt: Debt) {
        ctx.runMoneyAction { ctx.repository.insertInvestmentFundedByNewLoan(investment.copy(projectId = ctx.currentProjectId()), newDebt.copy(projectId = ctx.currentProjectId()), ctx.currentProjectId()) }
    }
    
    fun addInvestmentWithSource(investment: Investment, source: String) {
        ctx.runMoneyAction { ctx.repository.insertInvestmentWithSource(investment.copy(projectId = ctx.currentProjectId()), source, ctx.currentProjectId()) }
    }

    fun updateInvestmentValue(investment: Investment, newCurrentVal: Double) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.updateInvestmentValue(investment, newCurrentVal)
        }
    }

    fun updateInvestment(investment: Investment) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.updateInvestment(investment.copy(projectId = ctx.currentProjectId()))
        }
    }

    fun updateInvestmentFundedByAccount(investment: Investment, accountName: String, accountId: String = "") {
        ctx.runMoneyAction { ctx.repository.updateInvestmentFundedByAccount(investment.copy(projectId = ctx.currentProjectId()), accountName, ctx.currentProjectId(), accountId) }
    }

    fun updateInvestmentFundedByExistingDebt(investment: Investment, debt: Debt) {
        ctx.runMoneyAction { ctx.repository.updateInvestmentFundedByExistingDebt(investment.copy(projectId = ctx.currentProjectId()), debt.copy(projectId = ctx.currentProjectId()), ctx.currentProjectId()) }
    }

    fun executeLinkedInvestment(
        investment: Investment,
        fundingSourceType: String,
        sourceName: String,
        debtDetails: Debt?= null
    ) {
        ctx.scope.launch(Dispatchers.IO) {
            ctx.repository.executeLinkedInvestment(
                investment.copy(projectId = ctx.currentProjectId()),
                fundingSourceType,
                sourceName,
                debtDetails?.copy(projectId = ctx.currentProjectId())
            )
        }
    }

    fun addValuation(v: InvestmentValuation) {
        ctx.scope.launch(Dispatchers.IO) { ctx.repository.addValuation(v) }
    }

    fun getValuationsForInvestment(invId: String) = ctx.repository.getValuationsForInvestment(invId)

    fun deleteInvestment(investment: Investment) {
        ctx.runMoneyAction { ctx.repository.deleteInvestmentOnly(investment) }
    }
    
    fun deleteInvestmentAndReverseAccount(investment: Investment) {
        ctx.runMoneyAction { ctx.repository.deleteInvestmentAndReverseAccount(investment) }
    }
    
    fun deleteInvestmentAndLinkedLoan(investment: Investment) {
        ctx.runMoneyAction { ctx.repository.deleteInvestmentAndLinkedLoan(investment) }
    }
}
