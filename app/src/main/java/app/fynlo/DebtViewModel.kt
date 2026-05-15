package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.AccountRepository
import app.fynlo.data.DebtRepository
import app.fynlo.data.model.Account
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Debts — money the user owes to others.
 */
@HiltViewModel
class DebtViewModel @Inject constructor(
    private val debtRepo: DebtRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    val debts: StateFlow<List<Debt>> =
        debtRepo.allDebts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> =
        accountRepo.allAccounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addDebt(debt: Debt) {
        viewModelScope.launch(Dispatchers.IO) { debtRepo.insertDebt(debt) }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch(Dispatchers.IO) { debtRepo.updateDebt(debt) }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch(Dispatchers.IO) { debtRepo.deleteDebt(debt) }
    }

    fun payDebt(debt: Debt, principal: Double, interest: Double, payment: DebtPayment) {
        viewModelScope.launch(Dispatchers.IO) {
            debtRepo.payInstalment(debt, principal, interest, payment)
        }
    }

    suspend fun getDebtPayments(debtId: String): List<DebtPayment> =
        debtRepo.getDebtPaymentsForDebt(debtId)
}
