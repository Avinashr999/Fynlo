package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.AccountRepository
import app.fynlo.data.DebtRepository
import app.fynlo.data.InvestmentRepository
import app.fynlo.data.model.Account
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Investment
import app.fynlo.data.model.InvestmentValuation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Investments feature.
 */
@HiltViewModel
class InvestmentViewModel @Inject constructor(
    private val investRepo: InvestmentRepository,
    private val accountRepo: AccountRepository,
    private val debtRepo: DebtRepository
) : ViewModel() {

    val investments: StateFlow<List<Investment>> =
        investRepo.allInvestments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> =
        accountRepo.allAccounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debts: StateFlow<List<Debt>> =
        debtRepo.allDebts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addInvestment(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.insertInvestment(investment) }
    }

    fun addInvestmentFundedByAccount(investment: Investment, accountName: String) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.insertFundedByAccount(investment, accountName) }
    }

    fun addInvestmentFundedByExistingDebt(investment: Investment, debt: Debt) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.insertFundedByExistingDebt(investment, debt) }
    }

    fun addInvestmentFundedByNewLoan(investment: Investment, newDebt: Debt) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.insertFundedByNewLoan(investment, newDebt) }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.updateInvestment(investment) }
    }

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.deleteInvestment(investment) }
    }

    fun withdrawFromInvestment(investment: Investment, amount: Double, toAccount: String) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.withdraw(investment, amount, toAccount) }
    }

    fun addValuation(valuation: InvestmentValuation) {
        viewModelScope.launch(Dispatchers.IO) { investRepo.saveValuation(valuation) }
    }

    fun getValuationsForInvestment(investmentId: String): Flow<List<InvestmentValuation>> =
        investRepo.getValuationsForInvestment(investmentId)
}
