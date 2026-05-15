package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.AccountRepository
import app.fynlo.data.LendingRepository
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Lending feature — borrowers and loan payments.
 * Depends on: LendingRepository, AccountRepository.
 */
@HiltViewModel
class LendingViewModel @Inject constructor(
    private val lendingRepo: LendingRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    val borrowers: StateFlow<List<Borrower>> =
        lendingRepo.allBorrowers
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> =
        accountRepo.allAccounts
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val people: StateFlow<List<Person>> =
        accountRepo.allPeople
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addBorrower(borrower: Borrower, sourceAccount: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lendingRepo.insertBorrower(borrower)
        }
    }

    fun updateBorrower(borrower: Borrower) {
        viewModelScope.launch(Dispatchers.IO) { lendingRepo.updateBorrower(borrower) }
    }

    fun deleteBorrower(borrower: Borrower) {
        viewModelScope.launch(Dispatchers.IO) { lendingRepo.deleteBorrower(borrower) }
    }

    fun collectLoanPayment(
        borrower: Borrower,
        amount: Double,
        principalPortion: Double,
        interestPortion: Double,
        payment: Payment
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            lendingRepo.collectPayment(borrower, amount, principalPortion, interestPortion, payment)
        }
    }

    fun markBorrowerDefaulted(borrowerId: String, frozenInterest: Double, defaultDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            lendingRepo.markDefaulted(borrowerId, frozenInterest, defaultDate)
        }
    }

    fun restoreBorrowerToActive(borrower: Borrower) {
        viewModelScope.launch(Dispatchers.IO) {
            lendingRepo.updateBorrower(borrower.copy(status = "Active"))
        }
    }

    fun writeOffBorrower(borrower: Borrower) {
        viewModelScope.launch(Dispatchers.IO) {
            lendingRepo.updateBorrower(borrower.copy(status = "Written Off"))
        }
    }

    suspend fun getPaymentsForLoan(loanId: String): List<Payment> =
        lendingRepo.getPaymentsForLoan(loanId)
}
